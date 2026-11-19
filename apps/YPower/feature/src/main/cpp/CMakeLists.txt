cmake_minimum_required(VERSION 3.22.1)
project(ypower_native_trace)

find_package(bytehook REQUIRED CONFIG)

add_library(
        ypower_native_trace
        SHARED
        ypower_native_trace.cpp
)

add_library(
        ypower_socket_trace
        SHARED
        ypower_socket_trace.cpp
)

find_library(log-lib log)
find_library(dl-lib dl)

target_link_libraries(
        ypower_native_trace
        bytehook::bytehook
        ${log-lib}
        ${dl-lib}
)

target_link_libraries(
        ypower_socket_trace
        bytehook::bytehook
        ${log-lib}
        ${dl-lib}
)

# NDK r27 does not guarantee 16 KiB ELF LOAD alignment without explicit
# linker options. Both native tracing libraries must be safe on 16 KiB devices.
foreach(trace_target IN ITEMS ypower_native_trace ypower_socket_trace)
    target_link_options(${trace_target} PRIVATE
        "-Wl,-z,max-page-size=16384"
        "-Wl,-z,common-page-size=16384"
    )
endforeach()
