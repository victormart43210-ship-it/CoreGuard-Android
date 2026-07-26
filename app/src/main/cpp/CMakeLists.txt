cmake_minimum_required(VERSION 3.22.1)

project("tamperguard")

add_library(tamperguard SHARED tamperguard.cpp)

find_library(log-lib log)

target_link_libraries(tamperguard ${log-lib})

# Harden the native binary: hide symbols, stack canaries, strip/optimise in release.
target_compile_options(tamperguard PRIVATE
    -fvisibility=hidden
    -fstack-protector-strong
    -ffunction-sections
    -fdata-sections
    $<$<CONFIG:Release>:-O2>
    $<$<CONFIG:Release>:-DNDEBUG>)

target_link_options(tamperguard PRIVATE
    -Wl,--gc-sections
    $<$<CONFIG:Release>:-Wl,--strip-all>)
