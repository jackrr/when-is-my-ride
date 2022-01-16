#!/usr/bin/env bash

protoc \
  -I=/usr/include \
  -I=/usr/local/include \
  -I=resources/proto \
  --java_out=protoc/ \
  resources/proto/*.proto
