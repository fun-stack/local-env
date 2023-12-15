# local-env

Replicate fun-stack infrastructure locally: AWS lambdas with a HTTP/WS Server, local cognito.

## Links

Example on how to use it:
- Fun Scala Template: [example](https://github.com/fun-stack/example)

Terraform module for the corresponding AWS infrastructure:
- Fun Terraform Module: [terraform-aws-fun](https://github.com/fun-stack/terraform-aws-fun)

SDK library to communicate with the infrastructure in your code:
- Fun SDK Scala: [sdk-scala](https://github.com/fun-stack/sdk-scala)

## Install

```sh
npm install --global @fun-stack/fun-local-env
```

## Usage

```sh
fun-local-env --help
```

Usage:
```
Usage: fun-local-env <options>
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
sbt ~cli/fastOptJS/webpack
node cli/target/scala-2.13/scalajs-bundler/main/fun-local-env.js --ws 8080 --ws-rpc <path-to-js> handlerWebsocket

# or, with file watching:
echo cli/target/scala-2.13/scalajs-bundler/main/fun-local-env.js | entr -cnr node --enable-source-maps cli/target/scala-2.13/scalajs-bundler/main/fun-local-env.js --ws 8080 --ws-rpc <path-to-js> handlerWebsocket
```

## Release

```sh
cd npm
mkdir -p .git # trick npm into thinking this is a git root
npm version patch
git push
git push --tags
```

## Caveats

If you're using a node-version that's too new (>16.x), you might need to set this env-var if you get errors bundling the app. 
```sh 
export NODE_OPTIONS=--openssl-legacy-provider
```
