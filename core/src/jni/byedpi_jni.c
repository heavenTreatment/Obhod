#include <android/log.h>
#include <jni.h>
#include <pthread.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <unistd.h>

extern int main(int argc, char **argv);
extern int server_fd;

static pthread_mutex_t proxy_mutex = PTHREAD_MUTEX_INITIALIZER;
static pthread_mutex_t log_mutex = PTHREAD_MUTEX_INITIALIZER;
static JavaVM *java_vm;
static jobject log_sink;
static jmethodID log_method;
static volatile int stop_requested;
static unsigned int start_generation;

static void *stop_watcher(void *arg) {
    const unsigned int generation = (unsigned int) (uintptr_t) arg;
    for (int attempt = 0; attempt < 400; ++attempt) {
        pthread_mutex_lock(&proxy_mutex);
        if (generation != start_generation) {
            pthread_mutex_unlock(&proxy_mutex);
            return NULL;
        }
        const int fd = server_fd;
        pthread_mutex_unlock(&proxy_mutex);
        if (fd >= 0) {
            pthread_mutex_lock(&proxy_mutex);
            if (generation == start_generation && server_fd >= 0) {
                shutdown(server_fd, SHUT_RDWR);
                server_fd = -1;
            }
            pthread_mutex_unlock(&proxy_mutex);
            return NULL;
        }
        usleep(5000);
    }
    return NULL;
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void) reserved;
    java_vm = vm;
    return JNI_VERSION_1_6;
}

static void forward_log(int priority, const char *tag, const char *message) {
    if (java_vm == NULL) return;
    JNIEnv *env = NULL;
    int attached = 0;
    jint state = (*java_vm)->GetEnv(java_vm, (void **) &env, JNI_VERSION_1_6);
    if (state == JNI_EDETACHED) {
        if ((*java_vm)->AttachCurrentThread(java_vm, &env, NULL) != JNI_OK) return;
        attached = 1;
    } else if (state != JNI_OK) {
        return;
    }

    pthread_mutex_lock(&log_mutex);
    jobject sink = log_sink;
    if (sink != NULL && log_method != NULL) {
        jstring java_tag = (*env)->NewStringUTF(env, tag != NULL ? tag : "proxy");
        jstring java_message = (*env)->NewStringUTF(env, message);
        if (java_tag != NULL && java_message != NULL) {
            (*env)->CallVoidMethod(env, sink, log_method, (jint) priority, java_tag, java_message);
        }
        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
        if (java_tag != NULL) (*env)->DeleteLocalRef(env, java_tag);
        if (java_message != NULL) (*env)->DeleteLocalRef(env, java_message);
    }
    pthread_mutex_unlock(&log_mutex);
    if (attached) (*java_vm)->DetachCurrentThread(java_vm);
}

int __wrap___android_log_print(int priority, const char *tag, const char *format, ...) {
    if (priority < ANDROID_LOG_WARN) return 0;
    char message[2048];
    va_list args;
    va_start(args, format);
    va_list log_args;
    va_copy(log_args, args);
    const int length = vsnprintf(message, sizeof(message), format, args);
    va_end(args);
    __android_log_vprint(priority, tag, format, log_args);
    va_end(log_args);
    if (length >= 0 && tag != NULL && strcmp(tag, "proxy") == 0) {
        message[sizeof(message) - 1] = '\0';
        forward_log(priority, tag, message);
    }
    return length;
}

JNIEXPORT void JNICALL
Java_com_heaventreat_obhod_proxy_ByeDpiEngine_setLogSink(
    JNIEnv *env, jclass clazz, jobject sink) {
    (void) clazz;
    pthread_mutex_lock(&log_mutex);
    if (log_sink != NULL) (*env)->DeleteGlobalRef(env, log_sink);
    log_sink = sink != NULL ? (*env)->NewGlobalRef(env, sink) : NULL;
    if (log_sink != NULL) {
        jclass sink_class = (*env)->GetObjectClass(env, sink);
        if (sink_class != NULL) {
            log_method = (*env)->GetMethodID(
                env, sink_class, "onLog", "(ILjava/lang/String;Ljava/lang/String;)V");
            if ((*env)->ExceptionCheck(env)) {
                (*env)->ExceptionClear(env);
                log_method = NULL;
            }
            (*env)->DeleteLocalRef(env, sink_class);
        } else {
            (*env)->ExceptionClear(env);
            log_method = NULL;
        }
    } else {
        log_method = NULL;
    }
    pthread_mutex_unlock(&log_mutex);
}

JNIEXPORT jint JNICALL
Java_com_heaventreat_obhod_proxy_ByeDpiEngine_startProxy(
    JNIEnv *env, jclass clazz, jobjectArray java_args) {
    (void) clazz;
    if (java_args == NULL) return -1;

    const jsize argc = (*env)->GetArrayLength(env, java_args);
    char **argv = calloc((size_t) argc + 1, sizeof(char *));
    if (argv == NULL) return -1;

    for (jsize index = 0; index < argc; ++index) {
        jstring value = (jstring) (*env)->GetObjectArrayElement(env, java_args, index);
        if (value == NULL) goto cleanup;
        const char *utf = (*env)->GetStringUTFChars(env, value, NULL);
        if (utf == NULL) {
            (*env)->DeleteLocalRef(env, value);
            goto cleanup;
        }
        argv[index] = strdup(utf);
        (*env)->ReleaseStringUTFChars(env, value, utf);
        (*env)->DeleteLocalRef(env, value);
        if (argv[index] == NULL) goto cleanup;
    }

    pthread_mutex_lock(&proxy_mutex);
    if (stop_requested) {
        stop_requested = 0;
        pthread_mutex_unlock(&proxy_mutex);
        goto cleanup;
    }
    server_fd = -1;
    ++start_generation;
    pthread_mutex_unlock(&proxy_mutex);

    const int result = main((int) argc, argv);
    for (jsize index = 0; index < argc; ++index) free(argv[index]);
    free(argv);

    pthread_mutex_lock(&proxy_mutex);
    server_fd = -1;
    stop_requested = 0;
    ++start_generation;
    pthread_mutex_unlock(&proxy_mutex);
    return result;

cleanup:
    for (jsize index = 0; index < argc; ++index) free(argv[index]);
    free(argv);
    return -1;
}

JNIEXPORT void JNICALL
Java_com_heaventreat_obhod_proxy_ByeDpiEngine_stopProxy(JNIEnv *env, jclass clazz) {
    (void) env;
    (void) clazz;
    pthread_mutex_lock(&proxy_mutex);
    stop_requested = 1;
    if (server_fd >= 0) {
        shutdown(server_fd, SHUT_RDWR);
        server_fd = -1;
        pthread_mutex_unlock(&proxy_mutex);
        return;
    }
    const unsigned int generation = start_generation;
    pthread_mutex_unlock(&proxy_mutex);

    pthread_t watcher;
    if (pthread_create(&watcher, NULL, stop_watcher,
                       (void *) (uintptr_t) generation) == 0) {
        pthread_detach(watcher);
    }
}
