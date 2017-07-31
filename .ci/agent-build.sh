#!/bin/bash

set -ex

pushd agent

DEST=libsentry_agent_linux-$TARGET.so

cmake CMakeLists.txt
make

mv libsentry_agent.so $DEST
file $DEST

popd