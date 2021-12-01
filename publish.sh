#!/bin/sh

cd "$(dirname $0)"

rm -rf ./npm/bin/
mkdir ./npm/bin/

sbt lambdaServer/fullOptJS::webpack

cp ./lambda-server/target/scala-2.13/scalajs-bundler/main/lambda-server.js ./npm/bin/
cp ./README.md ./npm/
