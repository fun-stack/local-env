FROM node:18-slim

ENV NODE_ENV=production

COPY ["./cli/target/scala-2.13/scalajs-bundler/main/fun-local-env.js", "/bin/fun-local-env"]

ENTRYPOINT ["fun-local-env"]
