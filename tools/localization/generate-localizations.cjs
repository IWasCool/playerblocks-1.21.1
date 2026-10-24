// Authoring only: run prepare-localizations.cjs and import-localization-drafts.ps1 first.
// The resulting lang/*.json files are the editable source used by Minecraft.
const fs = require('node:fs');
const path = require('node:path');
const root = 'src/main/resources/assets/player_blocks/lang';
const english = JSON.parse(fs.readFileSync(path.join(root, 'en_us.json'), 'utf8'));
const { languages } = JSON.parse(
  fs.readFileSync('tools/localization/minecraft-languages.json', 'utf8'),
);
const vanilla = JSON.parse(fs.readFileSync('build/localization/vanilla.json', 'utf8'));
const overrides = JSON.parse(
  fs.readFileSync('tools/localization/localization-overrides.json', 'utf8'),
);
const drafts = {};
for (const file of fs.readdirSync('build/localization/drafts')) {
  if (file.endsWith('.json'))
    drafts[file.slice(0, -5)] = JSON.parse(
      fs.readFileSync(`build/localization/drafts/${file}`, 'utf8'),
    );
}
const aliases = {
  ast_es: 'es',
  ba_ru: 'ba',
  bar: 'de',
  be_latn: 'be',
  brb: 'nl',
  de_at: 'de',
  de_ch: 'de',
  esan: 'es',
  fil_ph: 'fil',
  fra_de: 'de',
  fur_it: 'fur',
  hal_ua: 'uk',
  hn_no: 'no',
  io_en: 'eo',
  isv: 'sk',
  ksh: 'de',
  li_li: 'nl',
  lmo: 'lmo',
  lzh: 'zh-TW',
  nds_de: 'de',
  nl_be: 'nl',
  nn_no: 'no',
  no_no: 'no',
  oc_fr: 'oc',
  ovd: 'sv',
  pls: 'es',
  qcb_es: 'es',
  qid: 'id',
  rpr: 'ru',
  ry_ua: 'uk',
  sah_sah: 'sah',
  se_no: 'se',
  sr_cs: 'sr',
  sr_sp: 'sr',
  sxu: 'de',
  szl: 'szl',
  tl_ph: 'fil',
  val_es: 'ca',
  vec_it: 'vec',
  vp_vl: 'nl',
  zh_cn: 'zh-CN',
  zh_hk: 'zh-TW',
  zh_tw: 'zh-TW',
};
const fallbackTargets = {
  ast: 'es',
  ba: 'ru',
  br: 'fr',
  fo: 'no',
  fur: 'it',
  kw: 'cy',
  lmo: 'it',
  oc: 'fr',
  sah: 'ru',
  se: 'no',
  szl: 'pl',
  vec: 'it',
};
const englishVariants = new Set([
  'en_au',
  'en_ca',
  'en_gb',
  'en_nz',
  'enp',
  'enws',
  'en_pt',
  'en_ud',
  'lol_us',
]);
const compactLocales = new Set([
  'nah',
  'tzo_mx',
  'zlm_arab',
  'qya_aa',
  'tlh_aa',
  'jbo_en',
  'kw_gb',
  'io_en',
  'isv',
]);

// Corresponding vanilla names retain Minecraft's terminology and regional spelling.
const nativeNames = {
  'Iron Door': 'block.minecraft.iron_door',
  'Glass Pane': 'block.minecraft.glass_pane',
  Glass: 'block.minecraft.glass',
  Terracotta: 'block.minecraft.terracotta',
  'Purpur Block': 'block.minecraft.purpur_block',
  'Purpur Pillar': 'block.minecraft.purpur_pillar',
  'Stone Bricks': 'block.minecraft.stone_bricks',
  Chest: 'block.minecraft.chest',
  'Shulker Box': 'block.minecraft.shulker_box',
  'Light Weighted Pressure Plate': 'block.minecraft.light_weighted_pressure_plate',
  'Heavy Weighted Pressure Plate': 'block.minecraft.heavy_weighted_pressure_plate',
};
const compactNames = {
  Block: ['iron_block', 'gold_block'],
  Slab: ['stone_slab', 'oak_slab'],
  Stairs: ['stone_stairs', 'oak_stairs'],
  Wall: ['brick_wall', 'cobblestone_wall'],
  Fence: ['oak_fence', 'birch_fence'],
  'Fence Gate': ['oak_fence_gate', 'birch_fence_gate'],
  Door: ['oak_door', 'birch_door'],
  Trapdoor: ['oak_trapdoor', 'birch_trapdoor'],
  Pillar: ['quartz_pillar', 'purpur_pillar'],
  Button: ['stone_button', 'oak_button'],
  'Wooden Button': ['oak_button', 'birch_button'],
  'Pressure Plate': ['stone_pressure_plate', 'oak_pressure_plate'],
  Carpet: ['white_carpet', 'red_carpet'],
  Wool: ['white_wool', 'red_wool'],
  Candle: ['white_candle', 'red_candle'],
  Bed: ['white_bed', 'red_bed'],
  Banner: ['white_banner', 'red_banner'],
  Chiseled: ['chiseled_quartz_block', 'chiseled_quartz_block'],
  Tiles: ['quartz_bricks', 'quartz_bricks'],
  'Chiseled Slab': ['smooth_quartz_slab', 'smooth_quartz_slab'],
  'Chiseled Stairs': ['smooth_quartz_stairs', 'smooth_quartz_stairs'],
  'Chiseled Wall': ['sandstone_wall', 'sandstone_wall'],
  'Tiles Slab': ['stone_brick_slab', 'stone_brick_slab'],
  'Tiles Stairs': ['stone_brick_stairs', 'stone_brick_stairs'],
  'Tiles Wall': ['stone_brick_wall', 'stone_brick_wall'],
  Blocks: ['iron_block', 'gold_block'],
  Liquid: ['water', 'water'],
};

function native(locale, key, fallback = '') {
  return vanilla[locale]?.[key] || fallback;
}
function commonName(a, b) {
  if (a === b) return a;
  if (!/\s/.test(a) && !/\s/.test(b)) {
    const left = [...a],
      right = [...b];
    let prefix = 0,
      suffix = 0;
    while (prefix < Math.min(left.length, right.length) && left[prefix] === right[prefix]) prefix++;
    while (
      suffix < Math.min(left.length, right.length) &&
      left[left.length - suffix - 1] === right[right.length - suffix - 1]
    )
      suffix++;
    return prefix >= suffix
      ? left.slice(0, prefix).join('') || a
      : left.slice(left.length - suffix).join('');
  }
  // Shared words avoid keeping half of an inflected adjective. Non-spaced scripts use characters.
  const split = (value) => (/\s/.test(value) ? value.split(/\s+/) : [...value]);
  const left = split(a),
    right = new Set(split(b));
  const words = left.filter((word) => right.has(word));
  return words.length ? words.join(/\s/.test(a) ? ' ' : '') : a;
}
function genericNative(locale, noun) {
  let term = compactNoun(locale, noun).trim();
  // Remove material-linking particles left after comparing two vanilla variants.
  term = term
    .replace(/\s+(?:de|di|of|en|da|do|du|del|van|von|aus|av|af|o|a|pi|kiwen)$/i, '')
    .trim();
  if (locale === 'ar_sa') term = term.replace(/\s+من$/, '');
  if (locale === 'hi_in') term = term.replace(/^(?:का|की|के)\s+/, '');
  if (locale.startsWith('de_') && noun !== 'Wooden Button')
    term = term.replace(/^(?:en)?holz/i, '');
  if (locale === 'ja_jp') term = term.replace(/^(?:色)?の|^色の/, '');
  if (locale.startsWith('zh_')) term = term.replace(/^色/, '');
  const first = [...native(locale, 'block.minecraft.iron_block')][0];
  if (first === first?.toLocaleUpperCase('en-US') && first !== first?.toLocaleLowerCase('en-US'))
    term = term[0].toLocaleUpperCase('en-US') + term.slice(1);
  return term;
}
function compactNoun(locale, noun) {
  if (nativeNames[noun]) return native(locale, nativeNames[noun]);
  if (noun.endsWith('Glazed Terracotta'))
    return native(locale, `block.minecraft.${noun.toLowerCase().replaceAll(' ', '_')}`);
  if (noun === 'Dye')
    return commonName(
      native(locale, 'item.minecraft.white_dye'),
      native(locale, 'item.minecraft.red_dye'),
    );
  if (noun === 'Liquid Bucket') return native(locale, 'item.minecraft.water_bucket');
  const pair = compactNames[noun];
  if (!pair) throw new Error(`No native noun mapping for ${noun}`);
  return commonName(...pair.map((id) => native(locale, `block.minecraft.${id}`)));
}

// For minority/constructed languages without an available translation draft,
// use the game's own vocabulary and compact numeric instructions, rather than
// inventing sentences in languages we cannot verify. The native Yes/Cancel
// choices show -1 / +/-0 explicitly for the two-use allowance.
function compactCommands(locale) {
  const get = (key) => native(locale, key);
  const player = get('entity.minecraft.player'),
    color = get('options.chat.color');
  const yes = get('gui.yes'),
    cancel = get('gui.cancel'),
    done = get('gui.done');
  const team = '/teamcolor',
    hex = '/hex';
  const generic = get('commands.help.failed');
  const fill = (value, args) => {
    let n = 0;
    return value.replace(/%(?:\d+\$)?s/g, () => args[n++] ?? '?');
  };
  const prompt = `${player} ${color} ${hex}: ≤ 2; %1$s/2. %2$s? [${yes}] ⇒ -1; [${cancel}] ⇒ ±0. `;
  return {
    invalid_hex: `${hex}: #000000–#FFFFFF; #27B6D9`,
    unknown_player: `${get('argument.player.unknown')} (%1$s)`,
    own_color_only_op: `${generic} (${player} ${hex})`,
    own_color_only: `${generic} (${player} ${hex})`,
    effective_color: `${player} ${color}: %1$s`,
    no_team: `${fill(get('team.notFound'), ['?'])} (${team} leader)`,
    team_info: `${team} %1$s: %2$s (→ %3$s)`,
    unknown_team: `${fill(get('team.notFound'), ['?'])} (${team})`,
    leader_not_member: `${generic} (${team} leader)`,
    leader_assigned: `${team} %1$s → %2$s (${color})`,
    player_color: `${player} %1$s — ${color}: %2$s`,
    limit_reached: `${hex}: 0/2; ≤ 2. ${generic}`,
    action_reset: `${color} → ${get('gui.defaults') || get('controls.reset')}`,
    action_set: `${color} → %1$s`,
    confirm_prompt_one: prompt,
    confirm_prompt_many: prompt,
    confirm: `[${yes}]`,
    cancel: `[${cancel}]`,
    confirm_hover: `${yes}: ${color}; -1/2`,
    cancel_hover: `${cancel}: ${color}; ±0/2`,
    invalid_confirmation: `${get('command.unknown.argument')} (${hex})`,
    expired_confirmation: `${get('command.unknown.argument')} (${hex})`,
    canceled: `${cancel}. ${color}: ±0; ${hex}: ±0/2.`,
    personal_color: `${player} ${color}: %1$s.`,
    changes_remaining: ' %1$s/2.',
    team_leader_only: `${generic} (${team} leader)`,
    team_color: `${team} %1$s — ${color}: %2$s`,
  };
}
const latinSerbian = {
  А: 'A',
  Б: 'B',
  В: 'V',
  Г: 'G',
  Д: 'D',
  Ђ: 'Đ',
  Е: 'E',
  Ж: 'Ž',
  З: 'Z',
  И: 'I',
  Ј: 'J',
  К: 'K',
  Л: 'L',
  Љ: 'Lj',
  М: 'M',
  Н: 'N',
  Њ: 'Nj',
  О: 'O',
  П: 'P',
  Р: 'R',
  С: 'S',
  Т: 'T',
  Ћ: 'Ć',
  У: 'U',
  Ф: 'F',
  Х: 'H',
  Ц: 'C',
  Ч: 'Č',
  Џ: 'Dž',
  Ш: 'Š',
};
const latinBelarusian = {
  А: 'A',
  Б: 'B',
  В: 'V',
  Г: 'H',
  Д: 'D',
  Е: 'Je',
  Ё: 'Jo',
  Ж: 'Ž',
  З: 'Z',
  І: 'I',
  Й: 'J',
  К: 'K',
  Л: 'L',
  М: 'M',
  Н: 'N',
  О: 'O',
  П: 'P',
  Р: 'R',
  С: 'S',
  Т: 'T',
  У: 'U',
  Ў: 'Ŭ',
  Ф: 'F',
  Х: 'Ch',
  Ц: 'C',
  Ч: 'Č',
  Ш: 'Š',
  Ы: 'Y',
  Ь: '',
  Э: 'E',
  Ю: 'Ju',
  Я: 'Ja',
};
function transliterate(value, table) {
  return [...value]
    .map(
      (char) =>
        table[char] ??
        (table[char.toUpperCase()] !== undefined ? table[char.toUpperCase()].toLowerCase() : char),
    )
    .join('');
}
const upsideMap = Object.fromEntries(
  [..."abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.,?!'()[]"].map(
    (char, index) => [
      char,
      [..."ɐqɔpǝɟƃɥıɾʞlɯuodbɹsʇnʌʍxʎz∀𐐒ƆᗡƎℲפHIſꓘꓶWNOԀΌᴚS⊥∩ΛMX⅄Z0ƖᄅƐㄣϛ9ㄥ86˙'¿¡,)(]["][index],
    ],
  ),
);
function stylize(locale, value) {
  if (locale === 'en_ud') {
    return (value.match(/%\d+\$s|./gu) || [])
      .reverse()
      .map((token) => (token.startsWith('%') ? token : (upsideMap[token] ?? token)))
      .join('');
  }
  if (locale === 'sr_cs') return transliterate(value, latinSerbian);
  if (locale === 'be_latn') return transliterate(value, latinBelarusian);
  if (['en_gb', 'en_au', 'en_nz'].includes(locale))
    value = value
      .replaceAll('color', 'colour')
      .replaceAll('Color', 'Colour')
      .replaceAll('Gray', 'Grey');
  if (locale === 'qid')
    value = value.replace(/u/g, 'oe').replace(/U/g, 'Oe').replace(/j/g, 'dj').replace(/J/g, 'Dj');
  if (locale === 'en_pt')
    value = value
      .replaceAll('Your', 'Yer')
      .replaceAll('your', 'yer')
      .replaceAll('You have', 'Ye have')
      .replaceAll('You can', 'Ye can')
      .replaceAll('You', 'Ye')
      .replaceAll('player', 'sailor')
      .replaceAll('Player', 'Sailor')
      .replaceAll('team', 'crew')
      .replaceAll('Team', 'Crew')
      .replaceAll('leader', 'captain')
      .replaceAll('Operators', 'Captains')
      .replaceAll('operator', 'captain')
      .replaceAll('color', 'hue');
  if (locale === 'enws')
    value = value
      .replaceAll('Your', 'Thy')
      .replaceAll('your', 'thy')
      .replaceAll('You have', 'Thou hast')
      .replaceAll('You can', 'Thou mayest')
      .replaceAll('You', 'Thou')
      .replaceAll('want', 'wish');
  if (locale === 'enp')
    value = value
      .replaceAll('personal', 'own')
      .replaceAll('hex digits', 'sixteen-base marks')
      .replaceAll('hex color', 'sixteen-base hue')
      .replaceAll('color', 'hue')
      .replaceAll('Confirm', 'Say yes')
      .replaceAll('confirm', 'say yes')
      .replaceAll('Cancel', 'Call off')
      .replaceAll('canceled', 'called off');
  if (locale === 'lol_us')
    value = value
      .replaceAll('Your', 'UR')
      .replaceAll('your', 'ur')
      .replaceAll('You', 'U')
      .replaceAll('you', 'u')
      .replaceAll('have', 'haz')
      .replaceAll('Unknown', 'I CAN HAS NO')
      .replaceAll('Confirm', 'DO IT')
      .replaceAll('Cancel', 'NOPE')
      .replace(/%\d+\$s|[^%]+/g, (token) => (token.startsWith('%') ? token : token.toUpperCase()));
  return value;
}

let compactCount = 0;
const approximate = [];
for (const locale of Object.keys(languages)) {
  if (locale === 'en_us') continue;
  let target = aliases[locale] || locale.split('_')[0];
  if (
    !drafts[target] &&
    fallbackTargets[target] &&
    !overrides[locale] &&
    !compactLocales.has(locale)
  ) {
    approximate.push({ locale, draft: fallbackTargets[target] });
    target = fallbackTargets[target];
  }
  const draft = overrides[locale] || drafts[target];
  const compact = compactLocales.has(locale) || (!draft && !englishVariants.has(locale));
  if (compact) compactCount++;
  const messages = compact ? compactCommands(locale) : null;
  const result = {};
  for (const [key, value] of Object.entries(english)) {
    let translated;
    if (englishVariants.has(locale)) translated = stylize(locale, value);
    else if (key.startsWith('command.')) {
      translated = compact ? messages[key.slice('command.player_blocks.'.length)] : draft[key];
    } else {
      const noun = value.replace(/^Player /, '');
      let term;
      if (compact) term = genericNative(locale, noun);
      else {
        term = nativeNames[noun]
          ? native(locale, nativeNames[noun])
          : noun.endsWith('Glazed Terracotta')
            ? native(locale, `block.minecraft.${noun.toLowerCase().replaceAll(' ', '_')}`)
            : draft[`name.${noun}`];
        // French 'Block' is a noun, not the verb 'to block'; Spanish Player Dye
        // uses a crafting dye rather than a hair dye. Match vanilla terminology.
        if (noun === 'Block')
          term = overrides[locale]?.['name.Block'] || genericNative(locale, 'Block');
        if (
          ['fr', 'de', 'es', 'pt', 'it', 'ja', 'ko', 'zh-CN', 'zh-TW'].includes(target) &&
          [
            'Slab',
            'Stairs',
            'Wall',
            'Fence',
            'Fence Gate',
            'Door',
            'Trapdoor',
            'Pillar',
            'Button',
            'Pressure Plate',
            'Carpet',
            'Wool',
            'Candle',
            'Bed',
            'Banner',
          ].includes(noun)
        )
          term = genericNative(locale, noun);
        if (target === 'ja' && noun === 'Pressure Plate') term = '感圧板';
        if (target === 'ja' && noun === 'Wool') term = '羊毛';
        if (target === 'zh-CN' && noun === 'Block') term = '方块';
        if (target === 'zh-TW' && noun === 'Block') term = '方塊';
        if (target === 'ta' && noun === 'Block') term = 'தொகுதி';
        if (noun === 'Chiseled')
          term =
            {
              fr: 'Bloc sculpté',
              de: 'Gemeißelter Block',
              es: 'Bloque cincelado',
              pt: 'Bloco esculpido',
              it: 'Blocco cesellato',
              ja: '模様入りブロック',
            }[target] || term;
        if (noun === 'Dye') term = overrides[locale]?.['name.Dye'] || genericNative(locale, 'Dye');
      }
      const pattern =
        {
          ar_sa: '%1$s اللاعب',
          he_il: '%1$s של השחקן',
          th_th: '%1$sของผู้เล่น',
          lo_la: `%1$sຂອງ${native(locale, 'entity.minecraft.player')}`,
        }[locale] || draft?.['name.pattern'];
      translated = compact
        ? `${native(locale, 'entity.minecraft.player')} · ${term}`
        : pattern.replace('%1$s', term);
      if (locale === 'ja_jp' || locale.startsWith('zh_'))
        translated = translated.replaceAll(' ', '');
    }
    if (typeof translated !== 'string' || !translated.trim())
      throw new Error(`Missing ${locale}: ${key}`);
    if (!englishVariants.has(locale)) translated = stylize(locale, translated);
    if (
      key.endsWith('confirm_prompt_one') ||
      key.endsWith('confirm_prompt_many') ||
      key.endsWith('personal_color')
    )
      translated = translated.trimEnd() + ' ';
    if (key.endsWith('changes_remaining')) translated = ' ' + translated.trim();
    result[key] = translated;
  }
  fs.writeFileSync(path.join(root, `${locale}.json`), JSON.stringify(result, null, 2) + '\n');
}
fs.writeFileSync(
  'build/localization/coverage.json',
  JSON.stringify(
    {
      locales: Object.keys(languages).length,
      keys: Object.keys(english).length,
      compactCount,
      approximate,
    },
    null,
    2,
  ) + '\n',
);
console.log(
  `Wrote ${Object.keys(languages).length} languages, ${Object.keys(english).length} keys each; ${compactCount} use compact native instructions.`,
);
