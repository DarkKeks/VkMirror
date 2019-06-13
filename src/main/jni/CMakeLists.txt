cmake_minimum_required(VERSION 3.1 FATAL_ERROR)
project(VkMirror VERSION 1.0 LANGUAGES CXX)
set(CMAKE_BUILD_TYPE Release)

if (NOT JNI_FOUND)
  find_package(JNI REQUIRED)
endif()

find_package(Td REQUIRED)

add_library(tdjni SHARED
  td_jni.cpp
)

set(TD_API_JAVA_PACKAGE "ru/darkkeks/vkmirror/tdlib")
target_include_directories(tdjni PRIVATE ${JAVA_INCLUDE_PATH} ${JAVA_INCLUDE_PATH2})
target_link_libraries(tdjni PRIVATE Td::TdStatic ${JAVA_JVM_LIBRARY})
target_compile_definitions(tdjni PRIVATE PACKAGE_NAME="${TD_API_JAVA_PACKAGE}")
set_property(TARGET tdjni PROPERTY CXX_STANDARD 14)

install(TARGETS tdjni
  ARCHIVE DESTINATION bin
  LIBRARY DESTINATION bin
  RUNTIME DESTINATION bin
)
