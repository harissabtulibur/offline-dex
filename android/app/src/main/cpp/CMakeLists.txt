cmake_minimum_required(VERSION 3.18.1)
project(cyberdeck_fdhook C)

add_library(
    cyberdeck_fdhook
    SHARED
    fd_hook.c
)

target_compile_options(cyberdeck_fdhook PRIVATE -O2 -Wall -Wextra)

find_library(log-lib log)

target_link_libraries(
    cyberdeck_fdhook
    ${log-lib}
    dl
)
