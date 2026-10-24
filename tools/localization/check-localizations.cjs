const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const root = 'src/main/resources/assets/player_blocks/lang';
const manifest = JSON.parse(fs.readFileSync('tools/localization/minecraft-languages.json', 'utf8'));
const english = JSON.parse(fs.readFileSync(path.join(root, 'en_us.json'), 'utf8'));
const keys = Object.keys(english).sort();
const expectedFiles = Object.keys(manifest.languages)
  .map((locale) => `${locale}.json`)
  .sort();
assert.deepEqual(
  fs
    .readdirSync(root)
    .filter((file) => file.endsWith('.json'))
    .sort(),
  expectedFiles,
  'Must cover exactly the Minecraft 1.21.1 language menu',
);
const argumentsOf = (value) => (value.match(/%\d+\$s/g) || []).sort();
for (const file of expectedFiles) {
  const raw = fs.readFileSync(path.join(root, file), 'utf8');
  assert(!raw.includes('\uFFFD'), `${file}: damaged Unicode`);
  const lang = JSON.parse(raw);
  assert.deepEqual(Object.keys(lang).sort(), keys, `${file}: missing or obsolete translation keys`);
  for (const key of keys) {
    const text = lang[key];
    assert(typeof text === 'string' && text.trim(), `${file}: empty ${key}`);
    assert.deepEqual(
      argumentsOf(text),
      argumentsOf(english[key]),
      `${file}: changed arguments for ${key}`,
    );
    assert(
      !text.replace(/%\d+\$s/g, '').includes('%'),
      `${file}: unsupported percent formatting for ${key}`,
    );
    assert(
      !/\[\[PB|\{\{\d|name\.|command\.player_blocks\./.test(text),
      `${file}: leaked draft/key markers`,
    );
    // Exercise formatting with username, RGB and team values containing '%' and '$'.
    const values = ['Alex%$1', '#27B6D9', 'Team%$3'];
    const formatted = text.replace(/%(\d+)\$s/g, (_, index) => values[Number(index) - 1]);
    assert(!formatted.includes('undefined'), `${file}: unexpected argument index`);
  }
  assert.notEqual(
    lang['command.player_blocks.confirm'],
    lang['command.player_blocks.cancel'],
    `${file}: confirmation choices indistinguishable`,
  );
}
const commands = fs.readFileSync(
  'src/main/java/com/ourcreation/playerblocks/color/PlayerColorCommands.java',
  'utf8',
);
for (const [, key] of commands.matchAll(/(?:text|error)\("([a-z_]+)"/g)) {
  assert(`command.player_blocks.${key}` in english, `Command references missing ${key}`);
}
assert(!/Component\.literal\("[^"\s]/.test(commands), 'Command feedback must be translatable');
console.log(
  `PASS: ${expectedFiles.length} Minecraft ${manifest.minecraft} locales × ${keys.length} keys; arguments, Unicode, names and command feedback.`,
);
