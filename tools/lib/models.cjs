const fs = require('node:fs');
const path = require('node:path');

function filesIn(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const file = path.join(directory, entry.name);
    return entry.isDirectory() ? filesIn(file) : [file];
  });
}

// Block states and item models are the entry points for this mod's model graph.
function findUnusedModels(assetsRoot) {
  const modelRoot = path.join(assetsRoot, 'models');
  const models = new Map(
    filesIn(modelRoot)
      .filter((file) => file.endsWith('.json'))
      .map((file) => [path.relative(modelRoot, file).slice(0, -5).split(path.sep).join('/'), file]),
  );
  const used = new Set();

  function follow(reference) {
    if (!reference.startsWith('player_blocks:')) return;
    const name = reference.slice('player_blocks:'.length);
    if (used.has(name)) return;
    const file = models.get(name);
    if (!file) throw new Error(`Missing model: ${reference}`);
    used.add(name);
    visit(JSON.parse(fs.readFileSync(file, 'utf8')));
  }

  function visit(value) {
    if (!value || typeof value !== 'object') return;
    for (const [key, child] of Object.entries(value)) {
      if ((key === 'model' || key === 'parent') && typeof child === 'string') follow(child);
      else if (typeof child === 'object') visit(child);
    }
  }

  for (const name of models.keys()) {
    if (name.startsWith('item/')) follow(`player_blocks:${name}`);
  }
  for (const file of filesIn(path.join(assetsRoot, 'blockstates'))) {
    visit(JSON.parse(fs.readFileSync(file, 'utf8')));
  }
  return [...models].filter(([name]) => !used.has(name)).map(([, file]) => file);
}

module.exports = { findUnusedModels };
