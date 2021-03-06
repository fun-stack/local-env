#!/bin/sh

set -e

cd "$(dirname $0)"

rm -rf ./npm/bin/
mkdir ./npm/bin/

# TODO: fullOptJS breaks oidc-provider code
# sbt cli/fullOptJS::webpack
sbt cli/fastOptJS::webpack

cp ./cli/target/scala-2.13/scalajs-bundler/main/fun-local-env.js ./npm/bin/
cp ./README.md ./npm/
