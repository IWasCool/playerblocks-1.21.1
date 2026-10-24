const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const projectRoot = path.resolve(__dirname, '../..');
const javaRoot = path.join(projectRoot, 'src/main/java');
const resourceRoot = path.join(projectRoot, 'src/main/resources');

function filesIn(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const file = path.join(directory, entry.name);
    return entry.isDirectory() ? filesIn(file) : [file];
  });
}

const javaFiles = filesIn(javaRoot).filter((file) => file.endsWith('.java'));
for (const file of javaFiles) {
  const source = fs.readFileSync(file, 'utf8');
  const packageName = source.match(/^package ([\w.]+);/m)?.[1];
  assert(packageName, `Missing package declaration: ${file}`);
  assert.equal(
    path.dirname(file),
    path.join(javaRoot, ...packageName.split('.')),
    `Package does not match its directory: ${file}`,
  );
}

const config = JSON.parse(
  fs.readFileSync(path.join(resourceRoot, 'player_blocks.mixins.json'), 'utf8'),
);
const mixinRoot = path.join(javaRoot, ...config.package.split('.'));
const configured = [...(config.mixins || []), ...(config.client || []), ...(config.server || [])];
assert.equal(new Set(configured).size, configured.length, 'Duplicate mixin registration');
for (const name of configured) {
  const file = path.join(mixinRoot, ...name.split('.')) + '.java';
  assert(fs.existsSync(file), `Configured mixin does not exist: ${name}`);
  assert(/@Mixin\(/.test(fs.readFileSync(file, 'utf8')), `Missing @Mixin annotation: ${name}`);
}
const declared = filesIn(mixinRoot)
  .filter((file) => file.endsWith('.java'))
  .map((file) => path.relative(mixinRoot, file).slice(0, -5).split(path.sep).join('.'));
assert.deepEqual(declared.sort(), configured.sort(), 'Mixin sources and configuration must agree');

const jsonFiles = filesIn(resourceRoot).filter((file) => file.endsWith('.json'));
for (const file of jsonFiles) {
  try {
    JSON.parse(fs.readFileSync(file, 'utf8'));
  } catch (error) {
    throw new Error(`Invalid JSON in ${path.relative(projectRoot, file)}: ${error.message}`);
  }
}

console.log(
  `PASS: ${javaFiles.length} Java source paths, ${configured.length} mixins, and ${jsonFiles.length} JSON resources.`,
);
