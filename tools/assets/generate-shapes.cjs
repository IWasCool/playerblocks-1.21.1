// Usage: node tools/assets/generate-shapes.cjs <directory containing extracted vanilla assets>
const fs = require('node:fs');
const path = require('node:path');
const { resourceRoot, writeJson } = require('../lib/resources.cjs');
const { findUnusedModels } = require('../lib/models.cjs');
const source = process.argv[2];
if (!source) throw new Error('Supply the extracted Minecraft resource directory');
const root = resourceRoot;
function write(file, value) {
  const normalized = new Set([
    'white_concrete',
    'white_wool',
    'quartz_pillar',
    'quartz_pillar_top',
    'chiseled_quartz_block',
    'quartz_bricks',
    'white_candle',
    'white_candle_lit',
    'white_stained_glass',
    'white_stained_glass_pane_top',
  ]);
  for (const [key, texture] of Object.entries(value.textures || {})) {
    if (
      texture.startsWith('minecraft:block/') &&
      normalized.has(texture.slice('minecraft:block/'.length))
    )
      value.textures[key] =
        'player_blocks:block/tinted/' + texture.slice('minecraft:block/'.length);
  }
  writeJson(file, value);
}
function read(file) {
  return JSON.parse(fs.readFileSync(path.join(source, file), 'utf8'));
}
const copied = new Set();
function model(ref, texture, family) {
  if (!ref.includes(':')) ref = `minecraft:${ref}`;
  if (!ref.startsWith('minecraft:block/') || ref === 'minecraft:block/block') return ref;
  const name = ref.slice('minecraft:block/'.length);
  const relative = family ? `${family}/${name}` : name;
  const result = `player_blocks:block/shapes/${relative}`;
  if (copied.has(relative)) return result;
  copied.add(relative);
  const value = read(`assets/minecraft/models/block/${name}.json`);
  for (const key of Object.keys(value.textures || {})) {
    if (!value.textures[key].startsWith('#') && family !== 'reference_candle')
      value.textures[key] = texture || 'minecraft:block/white_concrete';
  }
  if (name.startsWith('quartz_pillar')) {
    value.textures.side = 'minecraft:block/quartz_pillar';
    value.textures.end = 'minecraft:block/quartz_pillar_top';
  }
  if (family === 'reference_purpur_pillar' && name.startsWith('purpur_pillar')) {
    value.textures.side = 'player_blocks:block/tinted/purpur_pillar';
    value.textures.end = 'player_blocks:block/tinted/purpur_pillar_top';
  }
  if (family === 'reference_candle') value.render_type = 'minecraft:cutout';
  for (const element of value.elements || []) {
    for (const face of Object.values(element.faces || {})) face.tintindex = 0;
  }
  if (value.parent) value.parent = model(value.parent, texture, family);
  write(`assets/player_blocks/models/block/shapes/${relative}.json`, value);
  return result;
}
const shapes = {
  slab: ['smooth_stone_slab', 'smooth_stone_slab'],
  stairs: ['brick_stairs', 'brick_stairs'],
  wall: ['cobblestone_wall', 'cobblestone_wall_inventory'],
  fence: ['oak_fence', 'oak_fence_inventory'],
  fence_gate: ['oak_fence_gate', 'oak_fence_gate'],
  door: ['oak_door', 'oak_door_bottom_left'],
  iron_door: ['oak_door', 'oak_door_bottom_left'],
  trapdoor: ['oak_trapdoor', 'oak_trapdoor_bottom'],
  pane: ['glass_pane', 'glass_pane_post'],
  pillar: ['quartz_pillar', 'quartz_pillar'],
  button: ['stone_button', 'stone_button_inventory'],
  wooden_button: ['oak_button', 'oak_button_inventory'],
  pressure_plate: ['stone_pressure_plate', 'stone_pressure_plate'],
  carpet: ['white_carpet', 'white_carpet', 'player_blocks:block/tinted/white_wool', 'wool_carpet'],
  glass: ['white_stained_glass', 'white_stained_glass'],
  chiseled: ['chiseled_quartz_block', 'chiseled_quartz_block'],
  tiles: ['quartz_bricks', 'quartz_bricks'],
};
for (const [family, texture] of [
  ['chiseled', 'minecraft:block/chiseled_quartz_block'],
  ['tiles', 'minecraft:block/quartz_bricks'],
]) {
  for (const [kind, base, item] of [
    ['slab', 'smooth_stone_slab', 'smooth_stone_slab'],
    ['stairs', 'brick_stairs', 'brick_stairs'],
    ['wall', 'cobblestone_wall', 'cobblestone_wall_inventory'],
  ]) {
    shapes[`${family}_${kind}`] = [base, item, texture, family];
  }
}
for (const material of ['light_weighted', 'heavy_weighted']) {
  shapes[`${material}_pressure_plate`] = [
    `${material}_pressure_plate`,
    `${material}_pressure_plate`,
  ];
}
const language = {
  'itemGroup.player_blocks': 'Player Blocks',
  'block.player_blocks.player_concrete': 'Player Block',
};
for (const [kind, [base, inventory, texture, family]] of Object.entries(shapes)) {
  const id = `player_concrete_${kind}`;
  const state = read(`assets/minecraft/blockstates/${base}.json`);
  const applications = [
    ...Object.values(state.variants || {}),
    ...(state.multipart || []).map((p) => p.apply),
  ];
  for (const application of applications) {
    for (const entry of Array.isArray(application) ? application : [application])
      entry.model = model(entry.model, texture, family);
  }
  write(`assets/player_blocks/blockstates/${id}.json`, state);
  write(`assets/player_blocks/models/item/${id}.json`, {
    parent: model(`minecraft:block/${inventory}`, texture, family),
  });
  language[`block.player_blocks.${id}`] =
    'Player ' +
    kind
      .split('_')
      .map((s) => s[0].toUpperCase() + s.slice(1))
      .join(' ');
  const conditions = [{ condition: 'minecraft:survives_explosion' }];
  if (kind === 'door' || kind === 'iron_door')
    conditions.push({
      condition: 'minecraft:block_state_property',
      block: `player_blocks:${id}`,
      properties: { half: 'lower' },
    });
  const entry = { type: 'minecraft:item', name: `player_blocks:${id}` };
  const slab = kind === 'slab' || kind.endsWith('_slab');
  if (slab)
    entry.functions = [
      {
        function: 'minecraft:set_count',
        count: 2,
        conditions: [
          {
            condition: 'minecraft:block_state_property',
            block: `player_blocks:${id}`,
            properties: { type: 'double' },
          },
        ],
      },
    ];
  write(`data/player_blocks/loot_table/blocks/${id}.json`, {
    type: 'minecraft:block',
    pools: [{ rolls: 1, entries: [entry], conditions }],
  });
  const patternedIngredient = ['chiseled', 'tiles'].includes(family);
  const recipe = {
    type: 'minecraft:stonecutting',
    ingredient: {
      item: patternedIngredient
        ? `player_blocks:player_concrete_${family}`
        : 'player_blocks:player_concrete',
    },
    result: { id: `player_blocks:${id}`, count: slab ? 2 : 1 },
  };
  write(`data/player_blocks/recipe/${id}.json`, recipe);
  if (family)
    write(`data/player_blocks/recipe/${id}_from_concrete.json`, {
      ...recipe,
      ingredient: { item: 'player_blocks:player_concrete' },
    });
}
// Author geometry directly for silhouettes that depend on transparent vanilla textures.
function box(from, to, texture = '#texture', tint = true) {
  const faces = {};
  for (const direction of ['down', 'up', 'north', 'south', 'east', 'west']) {
    faces[direction] = { texture, uv: [0, 0, 16, 16], ...(tint ? { tintindex: 0 } : {}) };
  }
  return { from, to, faces };
}
function geometry(name, elements, options = {}) {
  write(`assets/player_blocks/models/block/shapes/${name}.json`, {
    parent: 'minecraft:block/block',
    textures: {
      particle: '#texture',
      texture: 'minecraft:block/white_concrete',
      metal: 'minecraft:block/iron_block',
    },
    elements,
    ...options,
  });
}
for (const id of ['player_concrete_door', 'player_concrete_iron_door']) {
  write(`assets/player_blocks/models/item/${id}.json`, {
    parent: 'minecraft:item/generated',
    textures: { layer0: 'player_blocks:item/player_door' },
  });
}
const paneTextures = {
  particle: '#pane',
  pane: 'minecraft:block/white_stained_glass',
  edge: 'minecraft:block/white_stained_glass_pane_top',
};
const glassOptions = {
  render_type: 'minecraft:translucent',
  textures: { particle: '#texture', texture: 'minecraft:block/white_stained_glass' },
};
const paneFace = (texture, uv, cullface) => ({
  texture,
  uv,
  tintindex: 0,
  ...(cullface ? { cullface } : {}),
});
const paneModel = (elements) => ({
  parent: 'minecraft:block/block',
  ambientocclusion: false,
  textures: paneTextures,
  elements,
  render_type: 'minecraft:translucent',
});
const paneSide = (from, to, side, sideUv, edgeFace, edgeUv) => ({
  from,
  to,
  faces: {
    down: paneFace('#edge', edgeUv),
    up: paneFace('#edge', edgeUv),
    [side]: paneFace('#edge', sideUv, side),
    west: paneFace('#pane', [16, 0, 9, 16]),
    east: paneFace('#pane', [9, 0, 16, 16]),
  },
});
write(
  'assets/player_blocks/models/block/shapes/glass_pane_post.json',
  paneModel([
    {
      from: [7, 0, 7],
      to: [9, 16, 9],
      faces: { down: paneFace('#edge', [7, 7, 9, 9]), up: paneFace('#edge', [7, 7, 9, 9]) },
    },
  ]),
);
write(
  'assets/player_blocks/models/block/shapes/glass_pane_side.json',
  paneModel([paneSide([7, 0, 0], [9, 16, 7], 'north', [7, 0, 9, 16], null, [7, 0, 9, 7])]),
);
write(
  'assets/player_blocks/models/block/shapes/glass_pane_side_alt.json',
  paneModel([
    {
      from: [7, 0, 9],
      to: [9, 16, 16],
      faces: {
        down: paneFace('#edge', [7, 0, 9, 7]),
        up: paneFace('#edge', [7, 0, 9, 7]),
        south: paneFace('#edge', [7, 0, 9, 16], 'south'),
        west: paneFace('#pane', [7, 0, 0, 16]),
        east: paneFace('#pane', [0, 0, 7, 16]),
      },
    },
  ]),
);
write(
  'assets/player_blocks/models/block/shapes/glass_pane_noside.json',
  paneModel([
    { from: [7, 0, 7], to: [9, 16, 9], faces: { north: paneFace('#pane', [9, 0, 7, 16]) } },
  ]),
);
write(
  'assets/player_blocks/models/block/shapes/glass_pane_noside_alt.json',
  paneModel([
    { from: [7, 0, 7], to: [9, 16, 9], faces: { east: paneFace('#pane', [7, 0, 9, 16]) } },
  ]),
);
write('assets/player_blocks/models/item/player_concrete_pane.json', {
  parent: 'minecraft:item/generated',
  textures: { layer0: 'player_blocks:item/player_glass_pane' },
});
geometry('white_stained_glass', [box([0, 0, 0], [16, 16, 16])], glassOptions);
// Decorative textures stay pale, allowing the player tint to remain clearly visible.
for (const [kind, texture] of [
  ['chiseled_quartz_block', 'chiseled_quartz_block'],
  ['quartz_bricks', 'quartz_bricks'],
]) {
  geometry(kind, [box([0, 0, 0], [16, 16, 16])], {
    textures: { particle: '#texture', texture: `minecraft:block/${texture}` },
  });
}
write('data/player_blocks/recipe/player_concrete_glass.json', {
  type: 'minecraft:crafting_shaped',
  pattern: ['GGG', 'GPG', 'GGG'],
  key: { G: { item: 'minecraft:glass' }, P: { item: 'player_blocks:player_concrete' } },
  result: { id: 'player_blocks:player_concrete_glass', count: 8 },
});
write('data/player_blocks/recipe/player_concrete_pane_from_glass.json', {
  type: 'minecraft:crafting_shaped',
  pattern: ['GGG', 'GGG'],
  key: { G: { item: 'player_blocks:player_concrete_glass' } },
  result: { id: 'player_blocks:player_concrete_pane', count: 16 },
});
language['block.player_blocks.player_concrete_iron_door'] = 'Player Iron Door';
language['block.player_blocks.player_concrete_glass'] = 'Player Glass';
language['block.player_blocks.player_concrete_pane'] = 'Player Glass Pane';
language['block.player_blocks.player_concrete_chiseled'] = 'Player Chiseled';
language['block.player_blocks.player_concrete_tiles'] = 'Player Tiles';
language['block.player_blocks.player_concrete_button'] = 'Player Button';
language['block.player_blocks.player_concrete_wooden_button'] = 'Player Wooden Button';
const referenceIds = [];
function referenceBlock(id, base, texture, family, ingredient) {
  referenceIds.push(`player_blocks:${id}`);
  const state = read(`assets/minecraft/blockstates/${base}.json`);
  for (const application of [
    ...Object.values(state.variants || {}),
    ...(state.multipart || []).map((part) => part.apply),
  ]) {
    for (const entry of Array.isArray(application) ? application : [application])
      entry.model = model(entry.model, texture, family);
  }
  write(`assets/player_blocks/blockstates/${id}.json`, state);
  write(`assets/player_blocks/models/item/${id}.json`, {
    parent: model(
      `minecraft:block/${base === 'white_candle' ? 'white_candle_one_candle' : base}`,
      texture,
      family,
    ),
  });
  const entry = { type: 'minecraft:item', name: `player_blocks:${id}` };
  if (id === 'player_candle')
    entry.functions = [2, 3, 4].map((count) => ({
      function: 'minecraft:set_count',
      count,
      conditions: [
        {
          condition: 'minecraft:block_state_property',
          block: `player_blocks:${id}`,
          properties: { candles: String(count) },
        },
      ],
    }));
  write(`data/player_blocks/loot_table/blocks/${id}.json`, {
    type: 'minecraft:block',
    pools: [
      { rolls: 1, entries: [entry], conditions: [{ condition: 'minecraft:survives_explosion' }] },
    ],
  });
  write(`data/player_blocks/recipe/${id}.json`, {
    type: 'minecraft:crafting_shapeless',
    ingredients: [
      ingredient || { item: `minecraft:${base}` },
      { item: 'player_blocks:player_concrete' },
    ],
    result: { id: `player_blocks:${id}`, count: 1 },
  });
  language[`block.player_blocks.${id}`] =
    'Player ' +
    base
      .split('_')
      .map((word) => word[0].toUpperCase() + word.slice(1))
      .join(' ');
}
for (const base of ['terracotta', 'purpur_block', 'purpur_pillar', 'stone_bricks']) {
  referenceBlock(`player_${base}`, base, `player_blocks:block/tinted/${base}`, `reference_${base}`);
}
// Retain the former registry ID so placed blocks become wool without disappearing.
referenceBlock(
  'player_diorite',
  'white_wool',
  'player_blocks:block/tinted/white_wool',
  'reference_wool',
  { tag: 'minecraft:wool' },
);
language['block.player_blocks.player_diorite'] = 'Player Wool';
const glazeColors = [
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
];
for (const color of glazeColors) {
  const base = `${color}_glazed_terracotta`;
  referenceBlock(`player_${base}`, base, `player_blocks:block/tinted/${base}`, `glazed_${color}`);
  write(`data/player_blocks/recipe/player_${base}_from_terracotta.json`, {
    type: 'minecraft:stonecutting',
    ingredient: { item: 'player_blocks:player_terracotta' },
    result: { id: `player_blocks:player_${base}`, count: 1 },
  });
}
referenceBlock('player_candle', 'white_candle', undefined, 'reference_candle', {
  tag: 'minecraft:candles',
});
write('assets/player_blocks/models/item/player_candle.json', {
  parent: 'minecraft:item/generated',
  textures: { layer0: 'minecraft:item/white_candle' },
});
language['block.player_blocks.player_candle'] = 'Player Candle';
write(
  'assets/player_blocks/blockstates/player_bed.json',
  read('assets/minecraft/blockstates/white_bed.json'),
);
write(
  'assets/player_blocks/models/item/player_bed.json',
  read('assets/minecraft/models/item/white_bed.json'),
);
write('data/player_blocks/recipe/player_bed.json', {
  type: 'minecraft:crafting_shapeless',
  ingredients: [{ tag: 'minecraft:beds' }, { item: 'player_blocks:player_concrete' }],
  result: { id: 'player_blocks:player_bed', count: 1 },
});
write('data/player_blocks/loot_table/blocks/player_bed.json', {
  type: 'minecraft:block',
  pools: [
    {
      rolls: 1,
      entries: [{ type: 'minecraft:item', name: 'player_blocks:player_bed' }],
      conditions: [
        { condition: 'minecraft:survives_explosion' },
        {
          condition: 'minecraft:block_state_property',
          block: 'player_blocks:player_bed',
          properties: { part: 'head' },
        },
      ],
    },
  ],
});
language['block.player_blocks.player_bed'] = 'Player Bed';
referenceIds.push(
  'player_blocks:player_bed',
  'player_blocks:player_banner',
  'player_blocks:player_wall_banner',
);
write(
  'assets/player_blocks/blockstates/player_banner.json',
  read('assets/minecraft/blockstates/white_banner.json'),
);
write(
  'assets/player_blocks/blockstates/player_wall_banner.json',
  read('assets/minecraft/blockstates/white_wall_banner.json'),
);
write(
  'assets/player_blocks/models/item/player_banner.json',
  read('assets/minecraft/models/item/white_banner.json'),
);
write('data/player_blocks/recipe/player_banner.json', {
  type: 'minecraft:crafting_shapeless',
  ingredients: [{ item: 'minecraft:white_banner' }, { item: 'player_blocks:player_concrete' }],
  result: { id: 'player_blocks:player_banner', count: 1 },
});
for (const id of ['player_banner', 'player_wall_banner']) {
  write(`data/player_blocks/loot_table/blocks/${id}.json`, {
    type: 'minecraft:block',
    pools: [
      {
        rolls: 1,
        entries: [
          {
            type: 'minecraft:item',
            name: 'player_blocks:player_banner',
            functions: [
              {
                function: 'minecraft:copy_components',
                source: 'block_entity',
                include: ['minecraft:banner_patterns', 'minecraft:custom_name'],
              },
            ],
          },
        ],
        conditions: [{ condition: 'minecraft:survives_explosion' }],
      },
    ],
  });
  language[`block.player_blocks.${id}`] = 'Player Banner';
}
write('data/minecraft/tags/block/beds.json', {
  replace: false,
  values: ['player_blocks:player_bed'],
});
write('data/minecraft/tags/item/beds.json', {
  replace: false,
  values: ['player_blocks:player_bed'],
});
write('data/minecraft/tags/block/candles.json', {
  replace: false,
  values: ['player_blocks:player_candle'],
});
// Vanilla cake attachment only accepts candles with a registered candle-cake counterpart.
write('data/minecraft/tags/item/candles.json', { replace: false, values: [] });
write('data/minecraft/tags/block/banners.json', {
  replace: false,
  values: ['player_blocks:player_banner', 'player_blocks:player_wall_banner'],
});
write('data/minecraft/tags/item/banners.json', {
  replace: false,
  values: ['player_blocks:player_banner'],
});
write('data/minecraft/tags/block/terracotta.json', {
  replace: false,
  values: ['player_blocks:player_terracotta'],
});
for (const [id, base] of [
  ['player_concrete_chest', 'chest'],
  ['player_shulker_box', 'white_shulker_box'],
]) {
  referenceIds.push(`player_blocks:${id}`);
  write(
    `assets/player_blocks/blockstates/${id}.json`,
    read(`assets/minecraft/blockstates/${base}.json`),
  );
  write(
    `assets/player_blocks/models/item/${id}.json`,
    read(`assets/minecraft/models/item/${base}.json`),
  );
  const entry = { type: 'minecraft:item', name: `player_blocks:${id}` };
  if (id === 'player_shulker_box')
    entry.functions = [
      {
        function: 'minecraft:copy_components',
        source: 'block_entity',
        include: [
          'minecraft:custom_name',
          'minecraft:container',
          'minecraft:lock',
          'minecraft:container_loot',
        ],
      },
    ];
  write(`data/player_blocks/loot_table/blocks/${id}.json`, {
    type: 'minecraft:block',
    pools: [{ rolls: 1, entries: [entry] }],
  });
  write(
    `data/player_blocks/recipe/${id}.json`,
    id === 'player_shulker_box'
      ? { type: 'player_blocks:player_shulker_conversion', category: 'misc' }
      : {
          type: 'minecraft:crafting_shapeless',
          ingredients: [{ item: `minecraft:${base}` }, { item: 'player_blocks:player_concrete' }],
          result: { id: `player_blocks:${id}`, count: 1 },
        },
  );
  language[`block.player_blocks.${id}`] =
    id === 'player_concrete_chest' ? 'Player Chest' : 'Player Shulker Box';
}
write('data/minecraft/tags/block/wool.json', {
  replace: false,
  values: ['player_blocks:player_diorite'],
});
write('data/minecraft/tags/item/wool.json', {
  replace: false,
  values: ['player_blocks:player_diorite'],
});
write('data/minecraft/tags/block/shulker_boxes.json', {
  replace: false,
  values: ['player_blocks:player_shulker_box'],
});
write('data/c/tags/block/chests.json', {
  replace: false,
  values: ['player_blocks:player_concrete_chest'],
});
write('data/c/tags/item/chests.json', {
  replace: false,
  values: ['player_blocks:player_concrete_chest'],
});
write(
  'assets/player_blocks/models/block/player_concrete.json',
  JSON.parse(
    fs.readFileSync(
      path.join(root, 'assets/player_blocks/models/block/player_concrete.json'),
      'utf8',
    ),
  ),
);
// Command translations are maintained alongside the names, not generated from shapes.
const langFile = path.join(root, 'assets/player_blocks/lang/en_us.json');
if (fs.existsSync(langFile)) {
  const previous = JSON.parse(fs.readFileSync(langFile, 'utf8'));
  for (const [key, value] of Object.entries(previous)) {
    if (key.startsWith('command.player_blocks.')) language[key] = value;
  }
}
write('assets/player_blocks/lang/en_us.json', language);
const ids = [
  'player_blocks:player_concrete',
  ...Object.keys(shapes).map((k) => `player_blocks:player_concrete_${k}`),
  ...referenceIds,
];
write('data/minecraft/tags/block/mineable/pickaxe.json', { replace: false, values: ids });
write('data/framedblocks/tags/block/blockentity_whitelisted.json', {
  replace: false,
  values: [
    'player_blocks:player_concrete',
    'player_blocks:player_concrete_chiseled',
    'player_blocks:player_concrete_tiles',
  ],
});
const tags = {
  slabs: 'slab',
  stairs: 'stairs',
  walls: 'wall',
  fences: 'fence',
  wooden_fences: 'fence',
  fence_gates: 'fence_gate',
  doors: 'door',
  wooden_doors: 'door',
  trapdoors: 'trapdoor',
  wooden_trapdoors: 'trapdoor',
  buttons: 'button',
  stone_buttons: 'button',
  pressure_plates: 'pressure_plate',
  carpets: 'carpet',
};
for (const [tag, kind] of Object.entries(tags))
  write(`data/minecraft/tags/block/${tag}.json`, {
    replace: false,
    values: [`player_blocks:player_concrete_${kind}`],
  });
for (const [tag, suffix] of [
  ['slabs', 'slab'],
  ['stairs', 'stairs'],
  ['walls', 'wall'],
]) {
  write(`data/minecraft/tags/block/${tag}.json`, {
    replace: false,
    values: [
      'player_blocks:player_concrete_' + suffix,
      ...['chiseled', 'tiles'].map((family) => `player_blocks:player_concrete_${family}_${suffix}`),
    ],
  });
}
write('data/minecraft/tags/block/doors.json', {
  replace: false,
  values: ['player_blocks:player_concrete_door', 'player_blocks:player_concrete_iron_door'],
});
write('data/minecraft/tags/block/buttons.json', {
  replace: false,
  values: ['player_blocks:player_concrete_button', 'player_blocks:player_concrete_wooden_button'],
});
write('data/minecraft/tags/block/wooden_buttons.json', {
  replace: false,
  values: ['player_blocks:player_concrete_wooden_button'],
});
const plates = Object.keys(shapes)
  .filter((kind) => kind.endsWith('pressure_plate'))
  .map((kind) => `player_blocks:player_concrete_${kind}`);
write('data/minecraft/tags/block/pressure_plates.json', { replace: false, values: plates });
write('data/minecraft/tags/block/wooden_pressure_plates.json', {
  replace: false,
  values: plates.filter(
    (id) =>
      !id.includes('weighted') &&
      !id.includes('blackstone') &&
      !id.endsWith(':player_concrete_pressure_plate'),
  ),
});
// Remove only obsolete generated variants from this resource tree.
for (const material of [
  'oak',
  'spruce',
  'birch',
  'jungle',
  'acacia',
  'dark_oak',
  'mangrove',
  'cherry',
  'bamboo',
  'crimson',
  'warped',
  'polished_blackstone',
]) {
  const id = `player_concrete_${material}_pressure_plate`;
  for (const file of [
    `assets/player_blocks/blockstates/${id}.json`,
    `assets/player_blocks/models/item/${id}.json`,
    `data/player_blocks/recipe/${id}.json`,
    `data/player_blocks/loot_table/blocks/${id}.json`,
  ]) {
    for (const resourceRoot of [root, 'build/resources/main']) {
      const target = path.resolve(resourceRoot, file);
      if (!target.startsWith(path.resolve(resourceRoot) + path.sep))
        throw new Error('Resource cleanup escaped the resource directory');
      if (fs.existsSync(target)) fs.unlinkSync(target);
    }
  }
}
console.log(`Generated ${ids.length} blocks and ${copied.size} tinted models.`);
const removedFiles = [
  'data/minecraft/tags/block/climbable.json',
  ...['ladder'].flatMap((kind) => [
    `assets/player_blocks/blockstates/player_concrete_${kind}.json`,
    `assets/player_blocks/models/item/player_concrete_${kind}.json`,
    `data/player_blocks/recipe/player_concrete_${kind}.json`,
    `data/player_blocks/loot_table/blocks/player_concrete_${kind}.json`,
    `assets/player_blocks/models/block/shapes/${kind}.json`,
  ]),
  'assets/player_blocks/models/block/shapes/chest_inventory.json',
];
for (const file of removedFiles)
  for (const resourceRoot of [root, 'build/resources/main']) {
    const target = path.resolve(resourceRoot, file);
    if (!target.startsWith(path.resolve(resourceRoot) + path.sep))
      throw new Error('Cleanup escaped the resource directory');
    if (fs.existsSync(target)) fs.unlinkSync(target);
  }
require('./generate-recipes.cjs');

// Custom pane geometry and removed variants can leave unused vanilla parent models.
const unusedModels = findUnusedModels(path.join(root, 'assets/player_blocks'));
for (const file of unusedModels) {
  fs.unlinkSync(file);
}
console.log(`Removed ${unusedModels.length} unused models.`);
