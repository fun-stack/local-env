const webpack = require('webpack');
const fs = require('fs');

module.exports = require('./scalajs.webpack.config');

module.exports.output.filename = "fun-stack-local.js";
module.exports.target = "node";
module.exports.plugins = module.exports.plugins || [];
module.exports.plugins.push(new webpack.BannerPlugin({
  banner: '#!/usr/bin/env -S node --enable-source-maps',
  raw: true,
}));
module.exports.plugins.push(function () {
  this.plugin('done', () => fs.chmodSync('fun-stack-local.js', '755'))
});

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
