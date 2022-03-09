#!/bin/sh

cd "$(dirname $0)"

rm -rf ./npm/bin/
mkdir ./npm/bin/

# TODO: fullOptJS breaks oidc-provider code
# sbt lambdaServer/fullOptJS::webpack
sbt lambdaServer/fastOptJS::webpack

cp ./lambda-server/target/scala-2.13/scalajs-bundler/main/lambda-server.js ./npm/bin/
cp ./README.md ./npm/
