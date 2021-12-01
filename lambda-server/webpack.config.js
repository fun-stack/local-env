const webpack = require('webpack');
const fs = require('fs');

module.exports = require('./scalajs.webpack.config');

module.exports.output.filename = "lambda-server.js";
module.exports.target = "node";
module.exports.plugins = module.exports.plugins || [];
module.exports.plugins.push(new webpack.BannerPlugin({
  banner: '#!/usr/bin/env node',
  raw: true,
}));
module.exports.plugins.push(function () {
  this.plugin('done', () => fs.chmodSync('lambda-server.js', '755'))
});
