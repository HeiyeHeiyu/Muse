# v2.x 终端二期:自编译 PTY 桥(自写 forkpty 实现,零第三方代码)
cmake_minimum_required(VERSION 3.22.1)
project("pty")

add_library(pty SHARED pty.c)

find_library(log-lib log)
target_link_libraries(pty ${log-lib})
