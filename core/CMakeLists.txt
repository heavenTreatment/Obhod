cmake_minimum_required(VERSION 3.22)
project(obhod_native C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)

set(HEV_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../third_party/hev-socks5-tunnel")
if(NOT EXISTS "${HEV_DIR}/CMakeLists.txt" AND NOT EXISTS "${HEV_DIR}/Makefile")
    message(FATAL_ERROR
        "ByeByeDPI VPN requires hev-socks5-tunnel. Run: git submodule update --init --recursive")
endif()

set(BYEDPI_DIR "${CMAKE_CURRENT_SOURCE_DIR}/src/byedpi")

include(ExternalProject)
set(HEV_BUILD_DIR "${CMAKE_CURRENT_BINARY_DIR}/hev-build/${ANDROID_ABI}")
set(HEV_LIB "${HEV_BUILD_DIR}/bin/libhev-socks5-tunnel.a")
set(HEV_YAML_LIB "${HEV_BUILD_DIR}/third-part/yaml/bin/libyaml.a")
set(HEV_LWIP_LIB "${HEV_BUILD_DIR}/third-part/lwip/bin/liblwip.a")
set(HEV_TASK_LIB "${HEV_BUILD_DIR}/third-part/hev-task-system/bin/libhev-task-system.a")
ExternalProject_Add(
    hev_socks5_tunnel_ext
    SOURCE_DIR "${HEV_DIR}"
    CONFIGURE_COMMAND ""
    BUILD_COMMAND
        sh -c "make clean -C ${HEV_DIR} && make static -C ${HEV_DIR} CC=${CMAKE_C_COMPILER} AR=${CMAKE_AR} STRIP=${CMAKE_STRIP} \"CFLAGS=${CMAKE_C_FLAGS} --target=${CMAKE_C_COMPILER_TARGET} -fPIC -DFD_SET_DEFINED -Wno-unused-command-line-argument\" && mkdir -p ${HEV_BUILD_DIR}/bin && cp ${HEV_DIR}/bin/*.a ${HEV_BUILD_DIR}/bin/ && mkdir -p ${HEV_BUILD_DIR}/third-part/yaml/bin && cp ${HEV_DIR}/third-part/yaml/bin/*.a ${HEV_BUILD_DIR}/third-part/yaml/bin/ && mkdir -p ${HEV_BUILD_DIR}/third-part/lwip/bin && cp ${HEV_DIR}/third-part/lwip/bin/*.a ${HEV_BUILD_DIR}/third-part/lwip/bin/ && mkdir -p ${HEV_BUILD_DIR}/third-part/hev-task-system/bin && cp ${HEV_DIR}/third-part/hev-task-system/bin/*.a ${HEV_BUILD_DIR}/third-part/hev-task-system/bin/"
    BUILD_IN_SOURCE 1
    INSTALL_COMMAND ""
    BUILD_BYPRODUCTS "${HEV_LIB}" "${HEV_YAML_LIB}" "${HEV_LWIP_LIB}" "${HEV_TASK_LIB}"
)
add_library(hev_socks5_tunnel_static STATIC IMPORTED)
set_target_properties(hev_socks5_tunnel_static PROPERTIES IMPORTED_LOCATION "${HEV_LIB}")
add_dependencies(hev_socks5_tunnel_static hev_socks5_tunnel_ext)

add_library(hev_tunnel SHARED src/jni/tun2socks_bridge.cpp)
target_include_directories(hev_tunnel PRIVATE "${HEV_DIR}/src")
add_dependencies(hev_tunnel hev_socks5_tunnel_ext)
target_link_libraries(hev_tunnel PRIVATE
    hev_socks5_tunnel_static
    "${HEV_YAML_LIB}"
    "${HEV_LWIP_LIB}"
    "${HEV_TASK_LIB}"
    log android
)

add_library(byedpi SHARED
    "${BYEDPI_DIR}/conev.c"
    "${BYEDPI_DIR}/desync.c"
    "${BYEDPI_DIR}/extend.c"
    "${BYEDPI_DIR}/main.c"
    "${BYEDPI_DIR}/mpool.c"
    "${BYEDPI_DIR}/packets.c"
    "${BYEDPI_DIR}/proxy.c"
    src/jni/byedpi_jni.c
)
target_include_directories(byedpi PRIVATE "${BYEDPI_DIR}")
target_compile_definitions(byedpi PRIVATE ANDROID_APP)
target_compile_options(byedpi PRIVATE -std=c99 -O2 -Wall -Wextra -Wno-unused-parameter -Wno-unused)
target_link_libraries(byedpi PRIVATE android log dl)
target_link_options(byedpi PRIVATE "-Wl,--wrap=__android_log_print")


