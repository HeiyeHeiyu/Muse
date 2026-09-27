# v2.x 终端二期:自编译 PTY 桥(自写 forkpty 实现,零第三方代码)
cmake_minimum_required(VERSION 3.22.1)
project("pty")

add_library(pty SHARED pty.c)

# Android 15+ 16KB 页支持:显式要求 16KB 对齐
# (4KB 页设备向后兼容;16KB 页设备缺少该对齐会 dlopen 失败)
target_link_options(pty PRIVATE "-Wl,-z,max-page-size=16384" "-Wl,-z,common-page-size=16384")

find_library(log-lib log)
target_link_libraries(pty ${log-lib})
