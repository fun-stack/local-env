FROM node:18-slim

ENV NODE_ENV=production

# use a proper pid 1, see https://blog.ghaiklor.com/2018/02/20/avoid-running-nodejs-as-pid-1-under-docker-images/ and https://github.com/krallin/tini
# otherwise, e.g., `docker stop` will not work properly.
RUN apt update && apt install -y tini curl

COPY ["./cli/target/scala-2.13/scalajs-bundler/main/fun-local-env.js", "/bin/fun-local-env"]

ENTRYPOINT ["/usr/bin/tini", "--",  "fun-local-env"]
