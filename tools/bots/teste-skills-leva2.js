// Skills de profissão, segunda leva (SkillsPlugin + OficinasPlugin, 2026-11-18): Caçador (XP só de mob que
// nasceu sozinho, drop a mais e cabeça), Escavador (XP na terra do mundo de recursos, bloco colocado não
// conta, relíquia), /trofeus vender, Alquimista (XP ao tirar a poção do suporte, poção mais longa) e Ferreiro
// (XP no reparo e desconto no Ferreiro). As chances vão a 100% só durante o teste (no config do servidor).
// Uso: node teste-skills-leva2.js   (Ferreiro precisa das oficinas: montar-mapa.js construir oficinas)
const fs = require('fs')
const path = require('path')
const { Vec3 } = require('vec3')
const t = require('./lib')

const R = 'execute in minecraft:recursos run'
const CONFIG = path.join(process.env.MC_TESTE || 'C:\\mc-teste', 'plugins', 'SkillsPlugin', 'config.yml')
const FERREIRO = new Vec3(14.5, 65, -24.5)
const BASE = [1005, 100, 1005]

async function xpPorSkill(bot) {
  const msgs = await t.capturar(bot, () => bot.chat('/skills ver'), 1500)
  const xp = {}
  for (const m of msgs) {
    const r = m.match(/^(.+?) - Nível \d+ \(([\d.]+)\//)
    if (r) xp[r[1].trim()] = Number(r[2].replace(/\./g, ''))
  }
  return xp
}

async function itemNoChao(id, x, y, z, raio = 4) {
  const r = await t.console(`${R} execute if entity @e[type=item,x=${x},y=${y},z=${z},distance=..${raio},nbt={Item:{id:"minecraft:${id}"}}]`)
  return /passed/i.test(r)
}

async function matar(bot, tipo, x, y, z) {
  await t.console(`${R} summon minecraft:${tipo} ${x} ${y} ${z} {NoAI:1b}`)
  await t.espera(600)
  await t.console(`${R} damage @e[type=${tipo},x=${x},y=${y},z=${z},distance=..3,limit=1] 1000 minecraft:player_attack by ${bot.username}`)
  await t.espera(800)
}

async function abrirNpc(bot, onde) {
  await t.console(`execute in minecraft:spawn run minecraft:tp ${bot.username} ${onde.x} 65 ${onde.z - 3} 0 0`)
  await t.espera(1500)
  const npc = Object.values(bot.entities).find(e => e.position.distanceTo(onde) < 1 && e.name === 'mannequin')
  if (!npc) return null
  return new Promise(ok => {
    const fim = setTimeout(() => ok(null), 3000)
    bot.once('windowOpen', j => { clearTimeout(fim); ok(j) })
    bot.activateEntity(npc)
  })
}

async function reparar(bot) {
  await t.console(`minecraft:clear ${bot.username}`)
  await t.console(`minecraft:give ${bot.username} diamond_pickaxe[damage=1200]`)
  const janela = await abrirNpc(bot, FERREIRO)
  if (!janela) return NaN
  const msgs = await t.capturar(bot, () => bot.clickWindow(0, 0, 0), 1200)
  bot.closeWindow(janela)
  const m = msgs.map(x => x.match(/reparou 1 item\(ns\) por R\$ ([\d.]+,\d\d)/)).find(Boolean)
  return m ? Number(m[1].replace(/\./g, '').replace(',', '.')) : NaN
}

t.rodar(async () => {
  const bot = await t.criarBot(t.nomeNovo('Sk2'))
  const nome = bot.username
  await t.console(`gamemode survival ${nome}`)
  await t.console(`minecraft:clear ${nome}`)
  await t.console(`effect give ${nome} minecraft:resistance infinite 4 true`)
  for (const s of ['cacador', 'escavador', 'alquimista']) await t.console(`skills admin setlevel ${nome} ${s} 10`)
  await t.console(`${R} forceload add ${BASE[0] - 16} ${BASE[2] - 16} ${BASE[0] + 16} ${BASE[2] + 16}`)
  await t.console(`${R} fill ${BASE[0] - 6} ${BASE[1]} ${BASE[2] - 6} ${BASE[0] + 6} ${BASE[1]} ${BASE[2] + 6} minecraft:stone`)
  await t.console(`${R} fill ${BASE[0] - 6} ${BASE[1] + 1} ${BASE[2] - 6} ${BASE[0] + 6} ${BASE[1] + 6} ${BASE[2] + 6} minecraft:air`)
  await t.console(`${R} minecraft:tp ${nome} ${BASE[0] + 0.5} ${BASE[1] + 1} ${BASE[2] + 0.5} 0 30`)
  await t.espera(3000)

  console.log('\n== Caçador: mob de comando não conta ==')
  let antes = await xpPorSkill(bot)
  await matar(bot, 'zombie', BASE[0] + 0.5, BASE[1] + 1, BASE[2] + 3.5)
  let depois = await xpPorSkill(bot)
  t.verificar((depois['Caçador'] || 0) === (antes['Caçador'] || 0), 'zumbi de /summon (como os de spawner e plugin) não dá XP de Caçador', `${antes['Caçador']} → ${depois['Caçador']}`)

  const original = fs.readFileSync(CONFIG, 'utf8')
  try {
    // o plugin já completou o config com as seções novas: troca só os valores
    const teste = original
      .replace(/  origens:(\n  - .*)+|  origens: \[.*\]/, '  origens: [COMMAND, NATURAL]')
      .replace(/chance-cabeca-por-nivel: .*/, 'chance-cabeca-por-nivel: 0.1')
      .replace(/chance-drop-extra-por-nivel: .*/, 'chance-drop-extra-por-nivel: 0.1')
      .replace(/chance-achado-por-nivel: .*/, 'chance-achado-por-nivel: 0.1')
    if (!/origens: \[COMMAND/.test(teste)) throw new Error('não achei o cacador.origens no config do servidor')
    fs.writeFileSync(CONFIG, teste)
    await t.console('skills admin reload')

    console.log('\n== Caçador: XP, cabeça e drop a mais ==')
    antes = await xpPorSkill(bot)
    await t.console(`${R} minecraft:kill @e[type=item,x=${BASE[0]},y=${BASE[1]},z=${BASE[2]},distance=..10]`)
    await matar(bot, 'zombie', BASE[0] + 0.5, BASE[1] + 1, BASE[2] + 3.5)
    depois = await xpPorSkill(bot)
    t.verificar((depois['Caçador'] || 0) - (antes['Caçador'] || 0) === 14, 'zumbi que conta dá 14 XP de Caçador', `${antes['Caçador']} → ${depois['Caçador']}`)
    t.verificar(await itemNoChao('zombie_head', BASE[0], BASE[1] + 1, BASE[2] + 3), 'o zumbi deixa a cabeça (troféu)')
    await matar(bot, 'spider', BASE[0] + 3.5, BASE[1] + 1, BASE[2] + 0.5)
    t.verificar(await itemNoChao('player_head', BASE[0] + 3, BASE[1] + 1, BASE[2]), 'a aranha deixa a cabeça com a textura dela (cabeça de jogador)')

    console.log('\n== Escavador ==')
    antes = await xpPorSkill(bot)
    await t.console(`${R} setblock ${BASE[0]} ${BASE[1] + 1} ${BASE[2] - 2} minecraft:dirt`)
    await t.espera(500)
    await bot.dig(bot.blockAt(new Vec3(BASE[0], BASE[1] + 1, BASE[2] - 2)), true)
    await t.espera(800)
    depois = await xpPorSkill(bot)
    t.verificar((depois['Escavador'] || 0) - (antes['Escavador'] || 0) === 1, 'terra natural no mundo de recursos dá 1 XP de Escavador', `${antes['Escavador']} → ${depois['Escavador']}`)
    const reliquias = ['gold_nugget', 'brick', 'bone', 'flint', 'heart_of_the_sea', 'golden_carrot', 'golden_helmet', 'turtle_egg']
    let achou = bot.inventory.items().some(i => reliquias.includes(i.name)) // o bot pode ter pegado do chão
    for (const r of reliquias) if (await itemNoChao(r, BASE[0], BASE[1] + 1, BASE[2] - 2)) achou = true
    t.verificar(achou, 'cavando, acha uma relíquia')
    // terra colocada pelo jogador: não conta
    await t.console(`minecraft:give ${nome} dirt 1`)
    await t.espera(500)
    await bot.equip(bot.inventory.items().find(i => i.name === 'dirt'), 'hand')
    await bot.placeBlock(bot.blockAt(new Vec3(BASE[0] - 2, BASE[1], BASE[2])), new Vec3(0, 1, 0)).catch(() => {})
    await t.espera(600)
    antes = await xpPorSkill(bot)
    const colocada = bot.blockAt(new Vec3(BASE[0] - 2, BASE[1] + 1, BASE[2]))
    if (colocada && colocada.name === 'dirt') {
      await bot.dig(colocada, true)
      await t.espera(600)
      depois = await xpPorSkill(bot)
      t.verificar((depois['Escavador'] || 0) === (antes['Escavador'] || 0), 'terra que o jogador colocou não dá XP', `${antes['Escavador']} → ${depois['Escavador']}`)
    } else {
      t.verificar(false, 'o bot colocou a terra pra testar', colocada && colocada.name)
    }

    console.log('\n== Troféus ==')
    await t.console(`minecraft:clear ${nome}`)
    await t.console(`trofeus dar ${nome} cabeca:enderman 2`)
    await t.console(`trofeus dar ${nome} reliquia:coroa 1`)
    await t.espera(800)
    let msgs = await t.capturar(bot, () => bot.chat('/trofeus vender'), 1500)
    t.verificar(msgs.some(m => /Vendeu seus troféus por R\$ 35\.000/.test(m)), '/trofeus vender: 2 cabeças de enderman (2.500) e a coroa (30.000)', msgs.join(' | '))
  } finally {
    fs.writeFileSync(CONFIG, original)
    await t.console('skills admin reload')
  }

  console.log('\n== Alquimista ==')
  await t.console(`skills admin setlevel ${nome} alquimista 49`)
  const [sx, sy, sz] = [BASE[0] + 2, BASE[1] + 1, BASE[2] - 2]
  const awk = '{id:"minecraft:potion",count:1,components:{"minecraft:potion_contents":{potion:"minecraft:awkward"}}}'
  await t.console(`${R} setblock ${sx} ${sy} ${sz} minecraft:brewing_stand{Items:[${awk.replace('{', '{Slot:0b,')},${awk.replace('{', '{Slot:1b,')},{Slot:3b,id:"minecraft:sugar",count:1},{Slot:4b,id:"minecraft:blaze_powder",count:1}]}`)
  await t.espera(600)
  const suporte = bot.blockAt(new Vec3(sx, sy, sz))
  let janela = await new Promise(ok => { const f = setTimeout(() => ok(null), 3000); bot.once('windowOpen', j => { clearTimeout(f); ok(j) }); bot.activateBlock(suporte) })
  if (janela) bot.closeWindow(janela)
  await t.espera(22000) // a fermentação leva 20 s
  antes = await xpPorSkill(bot)
  janela = await new Promise(ok => { const f = setTimeout(() => ok(null), 3000); bot.once('windowOpen', j => { clearTimeout(f); ok(j) }); bot.activateBlock(suporte) })
  if (janela) {
    await bot.clickWindow(0, 0, 1) // shift: vai pro inventário
    await t.espera(800)
    bot.closeWindow(janela)
  }
  depois = await xpPorSkill(bot)
  t.verificar((depois['Alquimista'] || 0) - (antes['Alquimista'] || 0) === 10, 'tirar a poção pronta do suporte dá 10 XP de Alquimista', `${antes['Alquimista']} → ${depois['Alquimista']}`)
  const efeito = await t.console(`data get entity ${nome} Inventory[{id:"minecraft:potion"}].components."minecraft:potion_contents".custom_effects[0].duration`)
  t.verificar(/ 4658$/.test(efeito.trim()), 'a Rapidez dura 3:52 em vez de 3:00 (4.658 ticks)', efeito)

  console.log('\n== Ferreiro ==')
  await t.console(`eco set ${nome} 1000000`)
  await t.console(`skills admin setlevel ${nome} ferreiro 1`)
  antes = await xpPorSkill(bot)
  const preco1 = await reparar(bot)
  depois = await xpPorSkill(bot)
  t.verificar((depois['Ferreiro'] || 0) > (antes['Ferreiro'] || 0), 'o reparo no Ferreiro dá XP de Ferreiro', `${antes['Ferreiro']} → ${depois['Ferreiro']} (R$ ${preco1})`)
  await t.console(`skills admin setlevel ${nome} ferreiro 50`)
  const preco50 = await reparar(bot)
  const razao = preco50 / preco1
  t.verificar(Math.abs(razao - 0.75 / 0.995) < 0.02, 'no nível 50, o reparo sai 25% mais barato', `R$ ${preco1} → R$ ${preco50} (${razao.toFixed(3)})`)

  msgs = await t.capturar(bot, () => bot.chat('/skills ver'), 1500)
  t.verificar(['Caçador', 'Escavador', 'Alquimista', 'Ferreiro'].every(s => msgs.some(m => m.startsWith(s))), 'as 4 skills novas aparecem no /skills ver', msgs.filter(m => /Nível/.test(m)).slice(-4).join(' | '))

  await t.console(`${R} minecraft:kill @e[type=item,x=${BASE[0]},y=${BASE[1]},z=${BASE[2]},distance=..12]`)
  await t.console(`${R} forceload remove ${BASE[0] - 16} ${BASE[2] - 16} ${BASE[0] + 16} ${BASE[2] + 16}`)
  await t.console(`minecraft:clear ${nome}`)
  await t.terminar(bot)
})
