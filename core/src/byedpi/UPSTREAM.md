# Vendored ByeDPI source

Source: https://github.com/hufrea/byedpi
Pinned revision: `ba532298de7b28cfe854aea83d061369d13ca290`

This is the upstream C engine used by ByeByeDPI, vendored so Android builds do
not require a ByeDPI git submodule. Only the C sources and headers used by the
Android target are retained. The upstream MIT license is in `LICENSE`.

Obhod builds these sources as the `byedpi` Android shared library with
`ANDROID_APP` enabled. The JNI lifecycle and log bridge are implemented in
`core/src/jni/byedpi_jni.c`.
