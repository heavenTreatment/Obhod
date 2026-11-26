# Third-party dependencies

## ByeDPI

Source: https://github.com/hufrea/byedpi
Core source: `core/src/byedpi`
Pinned revision: `ba532298de7b28cfe854aea83d061369d13ca290` (the revision used by ByeByeDPI)
Role: upstream SOCKS5 proxy and CLI strategy engine used by VPN mode
License: MIT, included at `core/src/byedpi/LICENSE`
Obhod Android integration: `core/src/jni/byedpi_jni.c` and the `ANDROID_APP` build definition

## ByeByeDPI strategy list

Source: https://github.com/romanvht/ByeByeDPI (`proxytest_strategies.list`)
Copied to: `app/src/main/assets/strategies/proxytest_strategies.list`
License: GPL-3.0, see the upstream repository `LICENSE`

## hev-socks5-tunnel

Source: https://github.com/heiher/hev-socks5-tunnel
Git submodule: `third_party/hev-socks5-tunnel`
Role: forwards Android VPN TUN traffic to ByeDPI SOCKS5
License: MIT, included in the submodule

## tg-ws-proxy

Source: https://github.com/AmurCanov/tg-ws-proxy-android
Rust crate: `native/tgwsproxy`
Android libraries: generated during the Gradle build
License: MIT, notice at `third_party/tg-ws-proxy/LICENSE`
