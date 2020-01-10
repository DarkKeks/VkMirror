#!/bin/bash

pushd tdlib

rm -r jnibuild
mkdir jnibuild
pushd jnibuild

git pull
cmake -DCMAKE_BUILD_TYPE=Release -DTD_ENABLE_JNI=ON -DCMAKE_INSTALL_PREFIX:PATH=../../td ..
time cmake --build . --target install -j 8

popd
popd