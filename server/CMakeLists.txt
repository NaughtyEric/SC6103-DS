cmake_minimum_required(VERSION 3.10)
project(SC6103-DS)

if (CMAKE_BUILD_TYPE STREQUAL "Debug")
    add_compile_definitions(DEBUG)
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -DDEBUG")

# 设置C++标准
set(CMAKE_CXX_STANDARD 21)
set(CMAKE_CXX_STANDARD_REQUIRED True)

# 包含头文件目录
include_directories(
    ${PROJECT_SOURCE_DIR}/include
)

# 源文件
set(SOURCES
    src/message.cpp
    main.cpp
)

# 可执行文件输出到 bin 目录
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${PROJECT_SOURCE_DIR}/bin)

# 添加可执行文件
add_executable(SC6103-DS ${SOURCES})
