cmake_minimum_required(VERSION 3.16)
project(facility_client LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_executable(facility_client
    src/main.cpp
    src/udp_client.cpp
    src/protocol.cpp
)

target_include_directories(facility_client PRIVATE include)

if (WIN32)
    target_link_libraries(facility_client ws2_32)
endif()


