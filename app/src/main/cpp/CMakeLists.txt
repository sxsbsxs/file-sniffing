cmake_minimum_required(VERSION 3.22.1)
project(media_probe LANGUAGES CXX)

add_library(media_probe SHARED native_media_probe.cpp)
target_compile_features(media_probe PRIVATE cxx_std_17)
