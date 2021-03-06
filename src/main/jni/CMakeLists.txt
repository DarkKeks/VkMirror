cmake_minimum_required(VERSION 3.1 FATAL_ERROR)
project(VkMirror VERSION 1.0 LANGUAGES CXX)
set(CMAKE_BUILD_TYPE Release)

find_package(Td REQUIRED)

if (NOT JNI_FOUND)
  find_package(JNI REQUIRED)
endif()
message(STATUS "Found JNI: ${JNI_INCLUDE_DIRS} ${JNI_LIBRARIES}")

if (NOT Java_FOUND)
  find_package(Java 1.6 REQUIRED)
endif()
message(STATUS "Found Java: ${Java_JAVAC_EXECUTABLE} ${Java_JAVADOC_EXECUTABLE}")

# Generating TdApi.java
find_program(PHP_EXECUTABLE php)

set(TD_API_JAVA_PACKAGE "ru/darkkeks/vkmirror/tdlib/internal")
set(TD_API_JAVA_PATH ${CMAKE_CURRENT_SOURCE_DIR}/../java)
set(TD_API_TLO_PATH ${CMAKE_CURRENT_SOURCE_DIR}/td/bin/td/generate/scheme/td_api.tlo)
set(TD_API_TL_PATH ${CMAKE_CURRENT_SOURCE_DIR}/td/bin/td/generate/scheme/td_api.tl)
set(JAVADOC_TL_DOCUMENTATION_GENERATOR_PATH ${CMAKE_CURRENT_SOURCE_DIR}/td/bin/td/generate/JavadocTlDocumentationGenerator.php)
set(GENERATE_JAVA_API_CMD ${CMAKE_CURRENT_SOURCE_DIR}/td/bin/td_generate_java_api TdApi ${TD_API_TLO_PATH} ${TD_API_JAVA_PATH} ${TD_API_JAVA_PACKAGE})
if (PHP_EXECUTABLE)
  set(GENERATE_JAVA_API_CMD ${GENERATE_JAVA_API_CMD} && ${PHP_EXECUTABLE} ${JAVADOC_TL_DOCUMENTATION_GENERATOR_PATH} ${TD_API_TL_PATH} ${TD_API_JAVA_PATH}/${TD_API_JAVA_PACKAGE}/TdApi.java)
endif()

add_custom_target(td_generate_java_api
  COMMAND ${GENERATE_JAVA_API_CMD}
  COMMENT "Generating Java TDLib API source files"
  DEPENDS ${CMAKE_CURRENT_SOURCE_DIR}/td/bin/td_generate_java_api ${TD_API_TLO_PATH} ${TD_API_TL_PATH} ${JAVADOC_TL_DOCUMENTATION_GENERATOR_PATH}
)



# Building shared library
add_library(tdjni SHARED
  td_jni.cpp
)

set_property(TARGET tdjni PROPERTY CXX_STANDARD 14)

target_include_directories(tdjni PRIVATE ${JAVA_INCLUDE_PATH} ${JAVA_INCLUDE_PATH2})
target_link_libraries(tdjni PRIVATE Td::TdStatic ${JAVA_JVM_LIBRARY})
target_compile_definitions(tdjni PRIVATE PACKAGE_NAME="${TD_API_JAVA_PACKAGE}")


add_dependencies(tdjni td_generate_java_api)

install(TARGETS tdjni
  ARCHIVE DESTINATION bin
  LIBRARY DESTINATION bin
  RUNTIME DESTINATION bin
)
