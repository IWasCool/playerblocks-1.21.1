const fs = require('node:fs');
const path = require('node:path');

const resourceRoot = path.resolve(__dirname, '../../src/main/resources');

function writeJson(relative, value) {
  const file = path.resolve(resourceRoot, relative);
  if (!file.startsWith(resourceRoot + path.sep)) {
    throw new Error(`Resource path is outside src/main/resources: ${relative}`);
  }
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, JSON.stringify(value, null, 2) + '\n');
}

module.exports = { resourceRoot, writeJson };
