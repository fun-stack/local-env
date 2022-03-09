# lambda-server

Run AWS lambdas locally with a HTTP/WS Server


## Install

```sh
npm install --global @fun-stack/lambda-server
```

## Usage

```sh
lambda-server --help
```

Usage:
```
Usage: lambda-server <options>
--http [<port>]
--ws [<port>]
--auth [<port>]
--http-api <js-file-name> <export-name>
--http-rpc <js-file-name> <export-name>
--ws-rpc <js-file-name> <export-name>
--ws-event-authorizer <js-file-name> <export-name>
```

## Development

```sh
sbt ~lambdaServer/fastOptJS/webpack
node lambda-server/target/scala-2.13/scalajs-bundler/main/lambda-server.js --ws 8080 --ws-rpc <path-to-js> handlerWebsocket

# or, with file watching:
echo lambda-server/target/scala-2.13/scalajs-bundler/main/lambda-server.js | entr -cnr node --enable-source-maps lambda-server/target/scala-2.13/scalajs-bundler/main/lambda-server.js --ws 8080 --ws-rpc <path-to-js> handlerWebsocket
```

## Release

```sh
cd npm
mkdir -p .git # trick npm into thinking this is a git root
npm version patch
git push
git push --tags
```
