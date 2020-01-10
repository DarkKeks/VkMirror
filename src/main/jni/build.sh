#!/bin/bash

rm -rf build
mkdir build
pushd build

cmake -DCMAKE_BUILD_TYPE=Release -DTd_DIR=${PWD}/../td/lib/cmake/Td -DCMAKE_INSTALL_PREFIX:PATH=.. ..
cmake --build . --target install

popd
cp bin/libtdjni.so ../resources/
