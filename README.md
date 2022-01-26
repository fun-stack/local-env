# lambda-server

Run AWS lambdas locally with a HTTP/WS Server


## Install

```sh
npm install --global @fun-stack/lambda-server
```

## Usage

```sh
lambda-server <http|ws> <js-file-name> <export-name> [<port>]
```

## Development

```sh
sbt ~lambdaServer/fastOptJS/webpack
node lambda-server/target/scala-2.13/scalajs-bundler/main/lambda-server.js ws <path-to-js> handlerWebsocket 8080
```
