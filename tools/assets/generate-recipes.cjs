// Run after generate-shapes.cjs to create crafting-table alternatives and recipe-book unlocks.
const fs = require('node:fs');
const path = require('node:path');
const { resourceRoot, writeJson } = require('../lib/resources.cjs');
const root = path.join(resourceRoot, 'data/player_blocks');
const playerBlock = 'player_blocks:player_concrete';
const playerDye = 'player_blocks:player_dye';
function write(relative, value) {
  writeJson(`data/player_blocks/${relative}`, value);
}
write('recipe/player_concrete.json', {
  type: 'minecraft:crafting_shapeless',
  category: 'building',
  ingredients: [{ item: 'minecraft:white_concrete' }, { item: playerDye }],
  result: { id: playerBlock, count: 1 },
});
write('recipe/player_dye.json', {
  type: 'minecraft:crafting_shapeless',
  category: 'misc',
  ingredients: [{ tag: 'player_blocks:vanilla_dyes' }],
  result: { id: playerDye, count: 1 },
});
write('tags/item/vanilla_dyes.json', {
  replace: false,
  values: [
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
  ].map((color) => `minecraft:${color}_dye`),
});
const assetRoot = 'src/main/resources/assets/player_blocks';
fs.writeFileSync(
  path.join(assetRoot, 'models/item/player_dye.json'),
  JSON.stringify(
    { parent: 'minecraft:item/generated', textures: { layer0: 'player_blocks:item/player_dye' } },
    null,
    2,
  ) + '\n',
);
const languageFile = path.join(assetRoot, 'lang/en_us.json');
const language = JSON.parse(fs.readFileSync(languageFile, 'utf8'));
language['item.player_blocks.player_dye'] = 'Player Dye';
fs.writeFileSync(languageFile, JSON.stringify(language, null, 2) + '\n');
const sources = {
  slab: 'stone_slab',
  stairs: 'stone_stairs',
  wall: 'cobblestone_wall',
  fence: 'oak_fence',
  fence_gate: 'oak_fence_gate',
  door: 'oak_door',
  iron_door: 'iron_door',
  trapdoor: 'oak_trapdoor',
  pane: 'glass_pane',
  pillar: 'quartz_pillar',
  button: 'stone_button',
  wooden_button: 'oak_button',
  pressure_plate: 'stone_pressure_plate',
  carpet: 'white_carpet',
  chiseled: 'chiseled_quartz_block',
  tiles: 'quartz_bricks',
  chiseled_slab: 'quartz_slab',
  chiseled_stairs: 'quartz_stairs',
  chiseled_wall: 'cobblestone_wall',
  tiles_slab: 'stone_brick_slab',
  tiles_stairs: 'stone_brick_stairs',
  tiles_wall: 'stone_brick_wall',
  light_weighted_pressure_plate: 'light_weighted_pressure_plate',
  heavy_weighted_pressure_plate: 'heavy_weighted_pressure_plate',
};
for (const [shape, source] of Object.entries(sources)) {
  write(`recipe/player_concrete_${shape}_crafting.json`, {
    type: 'minecraft:crafting_shapeless',
    category: 'building',
    ingredients: [{ item: playerDye }, { item: `minecraft:${source}` }],
    result: { id: `player_blocks:player_concrete_${shape}`, count: 1 },
  });
}
require('./generate-liquid-recipes.cjs');
for (const file of fs
  .readdirSync(path.join(root, 'recipe'))
  .filter((file) => file.endsWith('.json'))) {
  const name = file.slice(0, -5);
  // Convert every existing crafting recipe's coloring ingredient, retaining stonecutting conversions.
  const recipeFile = path.join(root, 'recipe', file);
  const recipe = JSON.parse(fs.readFileSync(recipeFile, 'utf8'));
  if (recipe.type.startsWith('minecraft:crafting')) {
    const replaceIngredient = (value) => {
      if (!value || typeof value !== 'object') return;
      if (value.item === playerBlock) value.item = playerDye;
      for (const child of Object.values(value)) replaceIngredient(child);
    };
    replaceIngredient(recipe.ingredients);
    replaceIngredient(recipe.key);
    write(`recipe/${file}`, recipe);
  }
  const ingredient = name === 'player_dye' ? null : playerDye;
  write(`advancement/recipes/${name}.json`, {
    parent: 'minecraft:recipes/root',
    criteria: {
      has_ingredient: {
        trigger: 'minecraft:inventory_changed',
        conditions: {
          items: [ingredient ? { items: ingredient } : { items: '#player_blocks:vanilla_dyes' }],
        },
      },
      has_the_recipe: {
        trigger: 'minecraft:recipe_unlocked',
        conditions: { recipe: `player_blocks:${name}` },
      },
    },
    requirements: [['has_ingredient', 'has_the_recipe']],
    rewards: { recipes: [`player_blocks:${name}`] },
  });
}
console.log('Generated Player Dye, dye-based crafting recipes, and recipe-book unlocks.');
