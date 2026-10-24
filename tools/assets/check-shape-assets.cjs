const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { findUnusedModels } = require('../lib/models.cjs');
const root = 'src/main/resources/assets/player_blocks';
assert.deepEqual(
  findUnusedModels(root),
  [],
  'Every model must be referenced by a block state or item',
);
const states = fs
  .readdirSync(path.join(root, 'blockstates'))
  .filter((file) => file !== 'player_liquid.json');
assert.equal(states.length, 53, '52 colored items plus the banner wall variant');
function read(file) {
  return JSON.parse(fs.readFileSync(file, 'utf8'));
}
function checkModel(ref, seen = new Set()) {
  if (!ref.startsWith('player_blocks:')) return;
  if (seen.has(ref)) return;
  seen.add(ref);
  const value = read(path.join(root, 'models', ref.split(':')[1] + '.json'));
  if (value.parent) checkModel(value.parent, seen);
  for (const texture of Object.values(value.textures || {})) {
    if (texture.startsWith('player_blocks:'))
      assert(
        fs.existsSync(path.join(root, 'textures', texture.split(':')[1] + '.png')),
        `Missing pattern mask ${texture}`,
      );
    else
      assert(
        texture.startsWith('#') ||
          [
            'minecraft:block/white_concrete',
            'minecraft:block/quartz_pillar',
            'minecraft:block/quartz_pillar_top',
            'minecraft:block/white_stained_glass',
            'minecraft:block/chiseled_quartz_block',
            'minecraft:block/quartz_bricks',
            'minecraft:block/iron_block',
            'minecraft:block/ladder',
            'minecraft:block/gray_concrete',
            'minecraft:block/white_wool',
            'minecraft:block/oak_planks',
            'minecraft:block/white_candle',
            'minecraft:block/white_candle_lit',
          ].includes(texture),
        `Unexpected texture in ${ref}: ${texture}`,
      );
  }
  for (const element of value.elements || []) {
    for (const face of Object.values(element.faces)) {
      if (!['#metal', '#trim', '#wood', '#pillow'].includes(face.texture))
        assert.equal(face.tintindex, 0, `Untinted face in ${ref}`);
    }
  }
}
const language = read(path.join(root, 'lang/en_us.json'));
for (const file of states) {
  const id = file.replace('.json', '');
  assert(
    language[`block.player_blocks.${id}`].startsWith('Player '),
    'Every block name starts with Player',
  );
  assert(
    !language[`block.player_blocks.${id}`].includes('Player Concrete '),
    'Shape names must omit redundant Concrete',
  );
  const state = read(path.join(root, 'blockstates', file));
  const applications = [
    ...Object.values(state.variants || {}),
    ...(state.multipart || []).map((p) => p.apply),
  ];
  for (const application of applications) {
    for (const variant of Array.isArray(application) ? application : [application])
      checkModel(variant.model);
  }
  if (id !== 'player_wall_banner') {
    checkModel(read(path.join(root, 'models/item', file)).parent);
    assert(fs.existsSync(`src/main/resources/data/player_blocks/recipe/${id}.json`));
  }
  assert(fs.existsSync(`src/main/resources/data/player_blocks/loot_table/blocks/${id}.json`));
}
const cube = read(path.join(root, 'models/block/shapes/cube.json'));
assert(
  cube.elements[0].faces.north.tintindex === 0,
  'Pillar parent chain must reach a tinted cube',
);
assert.equal(
  read(path.join(root, 'models/block/shapes/cube_column.json')).parent,
  'player_blocks:block/shapes/cube',
);
for (const removed of ['ladder'])
  assert(!states.includes(`player_concrete_${removed}.json`), `${removed} must be removed`);
const pane = read(path.join(root, 'models/item/player_concrete_pane.json'));
assert.equal(pane.parent, 'minecraft:item/generated', 'Inventory pane is a flat 2D sprite');
assert.equal(pane.textures.layer0, 'player_blocks:item/player_glass_pane');
const panePost = read(path.join(root, 'models/block/shapes/glass_pane_post.json'));
assert.deepEqual(
  Object.keys(panePost.elements[0].faces).sort(),
  ['down', 'up'],
  'Pane post should retain the vanilla narrow top and bottom frame',
);
assert.equal(
  panePost.textures.edge,
  'player_blocks:block/tinted/white_stained_glass_pane_top',
  'Placed pane uses the vanilla glass pane edge texture',
);
assert.equal(
  read(path.join(root, 'models/block/shapes/glass_pane_noside.json')).elements[0].faces.north
    .tintindex,
  0,
  'Unconnected pane surfaces retain player coloring',
);
assert.equal(
  read(path.join(root, 'models/item/player_concrete_door.json')).parent,
  'minecraft:item/generated',
);
assert(
  fs.existsSync(path.join(root, 'textures/item/player_door.png')),
  'Door must use the textured flat sprite',
);
for (const id of ['player_bed', 'player_banner'])
  assert(
    read(path.join(root, 'models/item', id + '.json')).parent.includes('template_'),
    'Bed/banner must use vanilla entity item models',
  );
assert(
  !states.includes('player_polished_blackstone_bricks.json') &&
    !states.includes('player_deepslate_bricks.json'),
);
assert.deepEqual(
  read(path.join(root, 'models/item/player_concrete_iron_door.json')),
  read(path.join(root, 'models/item/player_concrete_door.json')),
  'Redstone door keeps the regular concrete door appearance',
);
for (const family of ['chiseled', 'tiles'])
  for (const shape of ['slab', 'stairs', 'wall']) {
    const file = `player_concrete_${family}_${shape}.json`;
    const state = read(path.join(root, 'blockstates', file));
    for (const variant of Object.values(state.variants || {}))
      assert(
        variant.model.includes(`/shapes/${family}/`),
        'Patterned shape must use its own model family',
      );
    assert(
      read(`src/main/resources/data/player_blocks/recipe/player_concrete_${family}_${shape}.json`)
        .result.count === (shape === 'slab' ? 2 : 1),
    );
  }
assert.equal(
  read('src/main/resources/assets/player_blocks/lang/en_us.json')['itemGroup.player_blocks'],
  'Player Blocks',
);
assert.equal(
  read('src/main/resources/assets/player_blocks/lang/en_us.json')[
    'block.player_blocks.player_concrete'
  ],
  'Player Block',
);
const glazes = states.filter((file) => file.endsWith('_glazed_terracotta.json'));
assert.equal(glazes.length, 16, 'Every vanilla glazed terracotta pattern must be present');
for (const file of glazes)
  assert.equal(
    Object.keys(read(path.join(root, 'blockstates', file)).variants).length,
    4,
    'Glazed patterns need all four facing rotations',
  );
console.log(
  'PASS: all 53 block states (52 items) have drops and tinted models; all 16 glazed patterns, mask textures, and existing rendering checks passed.',
);

assert.equal(language['block.player_blocks.player_diorite'], 'Player Wool');
assert.equal(
  read(path.join(root, 'models/block/shapes/reference_wool/white_wool.json')).textures.all,
  'player_blocks:block/tinted/white_wool',
);
assert.equal(
  read(path.join(root, 'models/block/shapes/reference_terracotta/terracotta.json')).textures.all,
  'player_blocks:block/tinted/terracotta',
);
for (const variant of ['normal', 'normal_left', 'normal_right'])
  for (const layer of ['body', 'outline'])
    assert(fs.existsSync(path.join(root, `textures/entity/chest/${variant}_${layer}.png`)));
