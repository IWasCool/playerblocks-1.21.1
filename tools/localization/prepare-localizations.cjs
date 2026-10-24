// Prepare draft translation input using the installed Minecraft 1.21.1 assets.
// This is an authoring tool; no translation service or vanilla language files are shipped.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const assets = process.argv[2];
if (!assets)
  throw new Error('Supply the Minecraft asset root containing indexes/17.json and objects/');
const index = JSON.parse(fs.readFileSync(path.join(assets, 'indexes/17.json'), 'utf8'));
function asset(name) {
  const hash = index.objects[name]?.hash;
  if (!hash) throw new Error(`Missing Minecraft asset ${name}`);
  const bytes = fs.readFileSync(path.join(assets, 'objects', hash.slice(0, 2), hash));
  if (crypto.createHash('sha1').update(bytes).digest('hex') !== hash)
    throw new Error(`Damaged asset ${name}`);
  return JSON.parse(bytes);
}
const languages = asset('pack.mcmeta').language;
const vanilla = {};
for (const locale of Object.keys(languages)) {
  if (locale !== 'en_us') vanilla[locale] = asset(`minecraft/lang/${locale}.json`);
}
const english = JSON.parse(
  fs.readFileSync('src/main/resources/assets/player_blocks/lang/en_us.json', 'utf8'),
);
const input = Object.fromEntries(
  Object.entries(english).filter(([key]) => key.startsWith('command.')),
);
input['name.pattern'] = '%1$s of the player';
for (const value of new Set(
  Object.entries(english)
    .filter(([key]) => !key.startsWith('command.'))
    .map(([, value]) => value.replace(/^Player /, '')),
)) {
  input[`name.${value}`] = value;
}
fs.mkdirSync('build/localization', { recursive: true });
fs.writeFileSync('build/localization/input.json', JSON.stringify(input, null, 2) + '\n');
fs.writeFileSync('build/localization/vanilla.json', JSON.stringify(vanilla));
fs.writeFileSync(
  'tools/localization/minecraft-languages.json',
  JSON.stringify({ minecraft: '1.21.1', assetIndex: '17', languages }, null, 2) + '\n',
);
console.log(
  `Prepared ${Object.keys(languages).length} selectable languages and ${Object.keys(input).length} draft strings.`,
);
