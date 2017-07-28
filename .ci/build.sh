#!/usr/bin/env bash -ev

pushd agent

cmake CMakeLists.txt
make
mv libsentry_agent.so libsentry_agent_linux-x86_64.so

TARGET=i686 cmake CMakeLists.txt
make
mv libsentry_agent.so libsentry_agent_linux-i686.so

ls -lh
file libsentry_agent_linux-x86_64.so
file libsentry_agent_linux-i686.so

popd