// Entry-point consumed by Expo's autolinking / "plugins" resolver.
// The real implementation lives in plugin/src/index.ts and is compiled to
// plugin/build/index.js at build time (npm run build plugin).
module.exports = require('./plugin/build');
