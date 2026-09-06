cmake_minimum_required(VERSION 3.22.1)

project("cryptafile")

# Use prefab to find OpenSSL
find_package(openssl REQUIRED CONFIG)

# Main native library
add_library(${CMAKE_PROJECT_NAME} SHARED
        native-lib.cpp)

# Link OpenSSL and Android libraries
target_link_libraries(${CMAKE_PROJECT_NAME}
        openssl::crypto
        openssl::ssl
        android
        log)
