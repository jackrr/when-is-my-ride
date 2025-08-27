#!/usr/bin/env bash

set -eo pipefail

VERSION=$(./scripts/version.sh)
IMAGE=jackratner/when-is-my-ride:${VERSION}
npx shadow-cljs release :app
lein uberjar
docker buildx build --platform linux/amd64,linux/arm64 . -t ${IMAGE}
docker push ${IMAGE}
