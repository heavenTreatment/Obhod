-keepclasseswithmembernames class * {
    native <methods>;
}

-keep class com.heaventreat.obhod.proxy.ByeDpiEngine { *; }
-keep interface com.heaventreat.obhod.proxy.ByeDpiEngine$LogSink { *; }
-keep class com.heaventreat.obhod.proxy.TelegramProxyLibrary { *; }
-keep class com.heaventreat.obhod.proxy.NativeTelegramProxy { *; }
-keep class com.heaventreat.obhod.bridge.NativeBridge { *; }
-keep class com.heaventreat.obhod.bridge.NativeBridge$* { *; }
-keep class com.sun.jna.** { *; }
-dontwarn com.sun.jna.**
