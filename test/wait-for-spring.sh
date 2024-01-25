#!/usr/bin/env bash

for attempt in {1..20}; do sleep 1; if $(curl --output /dev/null --silent --head --fail http://user:password@localhost:8080/actuator/health); then echo ready; break; fi; echo waiting...; done
