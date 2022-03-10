# lambda-server

Run AWS lambdas locally with a HTTP/WS Server


## Install

```sh
npm install --global @fun-stack/lambda-server
```

## Usage

```sh
lambda-server <http|ws> <js-file-name> <export-name> [<port>] [-- ...]
```

## Development

```sh
sbt ~lambdaServer/fastOptJS/webpack
node lambda-server/target/scala-2.13/scalajs-bundler/main/lambda-server.js ws <path-to-js> handlerWebsocket 8080

# or, with file watching:
echo lambda-server/target/scala-2.13/scalajs-bundler/main/lambda-server.js | entr -cnr node --enable-source-maps lambda-server/target/scala-2.13/scalajs-bundler/main/lambda-server.js ws <path-to-js> handlerWebsocket 8080
```

## Release

```sh
cd npm
mkdir -p .git # trick npm into thinking this is a git root
npm version patch
git push 
git push --tags
```
