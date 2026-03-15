#!/bin/bash

set -euo pipefail

VERSION=$(./scripts/version.sh)
IMAGE=jackratner/when-is-my-ride:${VERSION}

npx shadow-cljs release :app
lein uberjar

# Stamp version into asset URLs so CDN cache is busted on each deploy
sed -i "s|/css/site.css|/css/site.css?v=${VERSION}|g" resources/public/index.html
sed -i "s|/js/compiled/app.js|/js/compiled/app.js?v=${VERSION}|g" resources/public/index.html

docker buildx build --platform linux/amd64,linux/arm64 -t ${IMAGE} --push .

# Restore index.html so the version stamp isn't committed
git checkout resources/public/index.html

ssh jack@pi4.jackratner.com "kubectl set image deployment/when-is-my-ride when-is-my-ride=${IMAGE}"
