const webpack = require('webpack');
const path = require('path');
const fs = require('fs');

module.exports = require('./scalajs.webpack.config');

module.exports.output.filename = "fun-local-env.js";
module.exports.target = "node";
module.exports.plugins = module.exports.plugins || [];
module.exports.plugins.push(function () {
  this.plugin('beforeRun', () => {
    fs.copyFileSync("../../../../../oidc-server/index.js", "./oidc-server.js");
    fs.copyFileSync("../../../../../oidc-server/silent-attention.js", "./silent-attention.js");
  })
});
module.exports.plugins.push(new webpack.BannerPlugin({
  banner: '#!/usr/bin/env -S node --enable-source-maps',
  raw: true,
}));
module.exports.plugins.push(function () {
  this.plugin('done', () => fs.chmodSync('fun-local-env.js', '755'))
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
