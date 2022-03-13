# local-env

Replicate fun-stack infrastructure locally: AWS lambdas with a HTTP/WS Server, local cognito.

See Examples for how to use it:
- Scala Project Template: [fun-stack-example](https://github.com/fun-stack/fun-stack-example)

See terraform module for the corresponding AWS infrastructure:
- Terraform Module: [terraform-aws-fun](https://github.com/fun-stack/terraform-aws-fun)

See SDK library for scala to communicate with the infrastructure in your code:
- Scala SDK: [fun-stack-scala](https://github.com/fun-stack/fun-stack-scala)

## Install

```sh
npm install --global @fun-stack/local-env
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
