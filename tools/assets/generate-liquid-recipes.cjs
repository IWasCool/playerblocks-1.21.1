const fs = require('node:fs');
const path = require('node:path');
const { resourceRoot, writeJson } = require('../lib/resources.cjs');
const root = resourceRoot;
const write = writeJson;
const liquid = 'player_blocks:player_liquid';
const conditions = [{ type: 'neoforge:mod_loaded', modid: 'create' }];
function create(name, recipe) {
  write(`data/player_blocks/recipe/create/${name}.json`, {
    'neoforge:conditions': conditions,
    ...recipe,
  });
}
write('data/player_blocks/tags/fluid/player_liquid.json', {
  replace: false,
  values: [liquid, 'player_blocks:flowing_player_liquid'],
});
write('data/minecraft/tags/fluid/water.json', {
  replace: false,
  values: [liquid, 'player_blocks:flowing_player_liquid'],
});
write('assets/player_blocks/blockstates/player_liquid.json', {
  variants: { '': { model: 'minecraft:block/water' } },
});
write('assets/player_blocks/models/item/player_liquid_bucket.json', {
  parent: 'minecraft:item/generated',
  textures: {
    layer0: 'minecraft:item/bucket',
    layer1: 'player_blocks:item/player_liquid_bucket_overlay',
  },
});
const langFile = path.join(root, 'assets/player_blocks/lang/en_us.json');
const language = JSON.parse(fs.readFileSync(langFile, 'utf8'));
Object.assign(language, {
  'fluid.player_blocks.player_liquid': 'Player Liquid',
  'block.player_blocks.player_liquid': 'Player Liquid',
  'item.player_blocks.player_liquid_bucket': 'Player Liquid Bucket',
});
fs.writeFileSync(langFile, JSON.stringify(language, null, 2) + '\n');
write('data/player_blocks/recipe/player_liquid_bucket.json', {
  type: 'minecraft:crafting_shapeless',
  category: 'misc',
  ingredients: [
    { item: 'minecraft:bucket' },
    ...Array.from({ length: 4 }, () => ({ item: 'player_blocks:player_dye' })),
  ],
  result: { id: 'player_blocks:player_liquid_bucket', count: 1 },
});
create('liquid_from_dye', {
  type: 'create:mixing',
  ingredients: [{ item: 'player_blocks:player_dye' }],
  results: [{ id: liquid, amount: 250 }],
});
const fluid = (amount) => ({ type: 'neoforge:tag', tag: 'player_blocks:player_liquid', amount });
const converted = new Set();
for (const file of fs
  .readdirSync(path.join(root, 'data/player_blocks/recipe'))
  .filter((file) => file.endsWith('.json'))) {
  const recipe = JSON.parse(
    fs.readFileSync(path.join(root, 'data/player_blocks/recipe', file), 'utf8'),
  );
  if (
    recipe.type !== 'minecraft:crafting_shapeless' ||
    recipe.result.id === 'player_blocks:player_liquid_bucket'
  )
    continue;
  if (!recipe.ingredients.some((ingredient) => ingredient.item === 'player_blocks:player_dye'))
    continue;
  const source = recipe.ingredients.filter(
    (ingredient) => ingredient.item !== 'player_blocks:player_dye',
  );
  if (source.length !== 1 || converted.has(recipe.result.id)) continue;
  converted.add(recipe.result.id);
  create('fill_' + recipe.result.id.split(':')[1], {
    type: 'create:filling',
    ingredients: [source[0], fluid(250)],
    results: [recipe.result],
  });
}
create('fill_player_glass', {
  type: 'create:filling',
  ingredients: [{ item: 'minecraft:glass' }, fluid(250)],
  results: [{ id: 'player_blocks:player_concrete_glass', count: 1 }],
});
write('data/player_blocks/tags/item/vanilla_shulker_boxes.json', {
  replace: false,
  values: [
    'minecraft:shulker_box',
    ...[
      'white',
      'orange',
      'magenta',
      'light_blue',
      'yellow',
      'lime',
      'pink',
      'gray',
      'light_gray',
      'cyan',
      'purple',
      'blue',
      'brown',
      'green',
      'red',
      'black',
    ].map((color) => `minecraft:${color}_shulker_box`),
  ],
});
create('fill_player_shulker_box', {
  type: 'create:filling',
  ingredients: [{ tag: 'player_blocks:vanilla_shulker_boxes' }, fluid(250)],
  results: [{ id: 'player_blocks:player_shulker_box', count: 1 }],
});
create('fill_player_liquid_bucket', {
  type: 'create:filling',
  ingredients: [{ item: 'minecraft:bucket' }, fluid(1000)],
  results: [{ id: 'player_blocks:player_liquid_bucket', count: 1 }],
});
create('empty_player_liquid_bucket', {
  type: 'create:emptying',
  ingredients: [{ item: 'player_blocks:player_liquid_bucket' }],
  results: [
    { id: 'minecraft:bucket', count: 1 },
    { id: liquid, amount: 1000 },
  ],
});
