//const { define } = require('./amd');

function readFile(path) {
  const Files = java.nio.file.Files;
  const encoded = Files.readAllLines(Paths.get(path));
  return [...encoded].join('\n');
}

var global = globalThis;
var server = org.bukkit.Bukkit;
var exports = {};
load('./js/internal/dist/src/require.js');
require('./js/internal/dist/src/init');
