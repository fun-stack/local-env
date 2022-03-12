#!/bin/sh

cd "$(dirname $0)"

rm -rf ./npm/bin/
mkdir ./npm/bin/

# TODO: fullOptJS breaks oidc-provider code
# sbt cli/fullOptJS::webpack
sbt cli/fastOptJS::webpack

cp ./cli/target/scala-2.13/scalajs-bundler/main/fun-stack-local.js ./npm/bin/
cp ./README.md ./npm/
