const webpack = require('webpack');
const fs = require('fs');
const path = require('path');
const {binProd} = require("@fun-stack/fun-pack");

module.exports = binProd({
  fileName: "fun-local-env.js"
});

 // module.exports.plugins[2] = {
module.exports.plugins.push({
  apply: (compiler) => {
    compiler.hooks.done.tap('done', () => {
      fs.copyFileSync("../../../../../oidc-server/index.js", "./oidc-server.js");
      fs.copyFileSync("../../../../../oidc-server/silent-attention.js", "./silent-attention.js");
    })
  }
});

module.exports.plugins.push(new webpack.NormalModuleReplacementPlugin(
  /oidc-provider\/.*\/attention/,
  path.join(__dirname, 'silent-attention.js')
));

// workaround for unsupported class properties in oidc-provider
module.exports.module = {
  rules: [
    {
      test: /oidc-provider\/.*\.js$/,
      use: {
        loader: 'babel-loader',
        options: {
          plugins: [
            '@babel/plugin-proposal-class-properties',
          ]
        }
      }
    }
  ]
};
