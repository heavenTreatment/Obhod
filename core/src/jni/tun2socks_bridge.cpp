#include <jni.h>
#include <android/log.h>
#include <atomic>
#include <mutex>
#include <string>
#include <thread>

extern "C" {
#include "hev-main.h"
}

#define LOG_TAG "Tun2SocksBridge"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static std::atomic<bool> g_tunnelRunning{false};
static std::thread g_tunnelThread;
static std::mutex g_tunnelMutex;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_heaventreat_obhod_bridge_NativeBridge_startTunnel(
    JNIEnv* env, jclass, jint tunFd, jstring configPath) {
    std::lock_guard<std::mutex> lock(g_tunnelMutex);
    if (g_tunnelRunning.load()) {
        LOGD("startTunnel: already running");
        return JNI_FALSE;
    }
    if (!configPath) {
        LOGE("startTunnel: null configPath");
        return JNI_FALSE;
    }

    const char* path = env->GetStringUTFChars(configPath, nullptr);
    if (!path) {
        LOGE("startTunnel: failed to read configPath");
        return JNI_FALSE;
    }
    std::string cfgPath(path);
    env->ReleaseStringUTFChars(configPath, path);

    const int fd = static_cast<int>(tunFd);
    if (g_tunnelThread.joinable()) g_tunnelThread.join();

    LOGD("Starting Hev tun2socks: tunFd=%d, config=%s", fd, cfgPath.c_str());
    g_tunnelRunning.store(true);
    try {
        g_tunnelThread = std::thread([fd, cfgPath]() {
            const int result = hev_socks5_tunnel_main_from_file(cfgPath.c_str(), fd);
            if (result < 0) {
                LOGE("Hev tun2socks stopped with error %d", result);
            } else {
                LOGD("Hev tun2socks stopped with result %d", result);
            }
            g_tunnelRunning.store(false);
        });
    } catch (const std::exception& error) {
        g_tunnelRunning.store(false);
        LOGE("Failed to start Hev thread: %s", error.what());
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_heaventreat_obhod_bridge_NativeBridge_isTunnelRunning(JNIEnv*, jclass) {
    return g_tunnelRunning.load() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_heaventreat_obhod_bridge_NativeBridge_stopTunnel(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> lock(g_tunnelMutex);
    if (g_tunnelRunning.load()) hev_socks5_tunnel_quit();
    if (g_tunnelThread.joinable()) g_tunnelThread.join();
    g_tunnelRunning.store(false);
}

}
