#!/usr/bin/env bash

set -eo pipefail

VERSION=$(./scripts/version.sh)
IMAGE=jackratner/when-is-my-ride:${VERSION}
npx shadow-cljs release :app
lein uberjar
docker build . -t ${IMAGE}
docker push ${IMAGE}
