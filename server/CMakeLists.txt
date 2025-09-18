cmake_minimum_required(VERSION 3.14)

project(SC6103DS_Server)
message(STATUS "CMAKE_SYSTEM_PROCESSOR = ${CMAKE_SYSTEM_PROCESSOR}")
message(STATUS "CMAKE_OSX_ARCHITECTURES = ${CMAKE_OSX_ARCHITECTURES}")
if(CMAKE_SYSTEM_PROCESSOR MATCHES "arm64|aarch64")
    set(CMAKE_OSX_ARCHITECTURES "arm64" CACHE STRING "" FORCE)
elseif(CMAKE_SYSTEM_PROCESSOR MATCHES "x86_64|amd64")
    set(CMAKE_OSX_ARCHITECTURES "x86_64" CACHE STRING "" FORCE)
endif()

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# 查找GTest
find_package(GTest REQUIRED)
include_directories(${GTEST_INCLUDE_DIRS})

# 包含头文件目录
include_directories(${CMAKE_SOURCE_DIR}/include)

# 源文件
set(SERVER_SOURCES
	src/message.cpp
)

# 生成静态库
add_library(message STATIC ${SERVER_SOURCES})

# 主程序（如有）
add_executable(server main.cpp)
target_link_libraries(server message)

# 测试
add_executable(message_test test_module/message_test.cpp)
target_link_libraries(message_test message GTest::GTest GTest::Main pthread)
