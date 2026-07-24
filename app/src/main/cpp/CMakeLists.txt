cmake_minimum_required(VERSION 3.22.1)

project("tamperguard")

add_library(tamperguard SHARED tamperguard.cpp)

find_library(log-lib log)

target_link_libraries(tamperguard ${log-lib})

# Harden the native binary: strip symbols and optimise in release.
target_compile_options(tamperguard PRIVATE
    -fvisibility=hidden
    -ffunction-sections
    -fdata-sections
    $<$<CONFIG:Release>:-O2>)

target_link_options(tamperguard PRIVATE
    -Wl,--gc-sections
    $<$<CONFIG:Release>:-Wl,--strip-all>)
