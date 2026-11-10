// Ajustes de 2026-11-10:
// - Spawners: um bloco por tipo em cada terreno (o segundo do mesmo tipo só empilha) e o /spawners juntar;
// - Máquinas: com o dono offline, a produção anda na metade (producao-offline: 0.5);
// - Pesca: o lixo do vanilla vira peixe (com lixo-por-nivel 1 e sem pesca especial, todo lixo vira peixe).
// Põe um pescador.loot de teste no config do SkillsPlugin e devolve o original no fim.
// Uso: node teste-ajustes-1110.js   (leva uns 5 min: a pesca é de verdade)
const fs = require('fs')
const path = require('path')
const t = require('./lib')
const { Vec3 } = require('vec3')

const CELULA = 37 // terreno de 32 + rua de 5 (TerrenosPlugin)
const CONFIG_SKILLS = path.join(process.env.MC_TESTE || 'C:\\mc-teste', 'plugins', 'SkillsPlugin', 'config.yml')
const JAR_SKILLS = path.join(__dirname, '..', '..', 'skills', 'skills-plugin', 'src', 'main', 'resources', 'config.yml')
const TANQUE = { x: 300, y: 120, z: 300 }
const LIXO = ['lily_pad', 'bowl', 'leather', 'leather_boots', 'rotten_flesh', 'stick', 'string', 'potion', 'bone', 'ink_sac',
  'tripwire_hook', 'bamboo', 'fishing_rod']
const PEIXES = ['cod', 'salmon', 'pufferfish', 'tropical_fish']

const contar = async (nome, item) => Number(((await t.console(`minecraft:clear ${nome} ${item} 0`)).match(/Found (\d+)/) || [, 0])[1])
const janela = (bot, fn) => new Promise(ok => {
  const fim = setTimeout(() => ok(null), 3000)
  bot.once('windowOpen', j => { clearTimeout(fim); setTimeout(() => ok(j), 800) })
  fn()
})

/** O bloco "loot:" do jar, sem pesca especial e com todo lixo virando peixe. */
function lootDeTeste() {
  const jar = fs.readFileSync(JAR_SKILLS, 'utf8').replace(/\r\n/g, '\n')
  const inicio = jar.indexOf('\n  loot:\n') + 1
  return jar.slice(inicio, jar.indexOf('\n\n', inicio))
    .replace(/chance-base: [\d.]+/, 'chance-base: 0')
    .replace(/chance-por-nivel: [\d.]+/, 'chance-por-nivel: 0')
    .replace(/lixo-por-nivel: [\d.]+/, 'lixo-por-nivel: 1')
}

async function terreno(bot) {
  const msgs = await t.capturar(bot, () => bot.chat('/terreno auto'), 3000)
  const [gx, gz] = ((msgs.join(' ').match(/terreno (-?\d+;-?\d+)/) || [])[1] || '0;0').split(';').map(Number)
  const ir = () => t.console(`execute in minecraft:terrenos run minecraft:tp ${bot.username} ${gx * CELULA + 16.5} 65 ${gz * CELULA + 12.5} 0 0`)
  await ir()
  await t.espera(1500)
  const r = await t.capturar(bot, () => bot.chat('/terreno comprar'), 1500)
  t.verificar(r.some(m => m.includes('comprado')), 'comprou um terreno', r[0])
  return ir
}

async function colocar(bot, item, lugar) {
  await bot.equip(bot.inventory.items().find(i => i.name === item), 'hand')
  await t.espera(300)
  return t.capturar(bot, () => bot.placeBlock(bot.blockAt(lugar.offset(0, -1, 0)), new Vec3(0, 1, 0)).catch(() => {}), 1200)
}

async function spawners(bot) {
  const nome = bot.username
  console.log('\n== Spawners: um bloco por tipo no terreno ==')
  await t.console(`spawners dar ${nome} porco 3`)
  await t.console(`spawners dar ${nome} vaca 1`)
  await t.espera(500)
  const base = bot.entity.position.floored()
  const a = base.offset(0, 0, 2), b = base.offset(3, 0, 2)
  await colocar(bot, 'spawner', a)
  t.verificar(bot.blockAt(a)?.name === 'spawner', 'o primeiro spawner de porco entra')
  let msgs = await colocar(bot, 'spawner', b)
  t.verificar(msgs.some(m => /já tem um Spawner de .*Porco/.test(t.semCor(m))) && bot.blockAt(b)?.name !== 'spawner',
    'o segundo de porco em outro lugar do terreno é recusado', msgs.find(m => /Spawner/.test(m)))
  await bot.equip(bot.inventory.items().find(i => i.name === 'spawner'), 'hand')
  await t.espera(300)
  await t.capturar(bot, () => bot.activateBlock(bot.blockAt(a)), 1000)
  const lista = t.semCor(await t.console('spawners lista'))
  t.verificar(new RegExp(`Porco x2 em terrenos ${a.x} ${a.y} ${a.z}`).test(lista), 'mas empilha no primeiro (x2)', lista.slice(0, 200))
  // o de vaca é outro tipo: entra
  await t.console(`minecraft:clear ${nome} spawner`)
  await t.console(`spawners dar ${nome} vaca 1`)
  await t.espera(500)
  await colocar(bot, 'spawner', b)
  t.verificar(bot.blockAt(b)?.name === 'spawner', 'um spawner de outro tipo (vaca) entra em outro lugar')
  for (const l of [a, b]) await bot.dig(bot.blockAt(l), true).catch(() => {})
  await t.espera(1000)

  console.log('\n== /spawners juntar ==')
  let r = t.semCor(await t.console('spawners juntar'))
  t.verificar(/Juntaria/.test(r) && /blocos/.test(r), 'sem confirmar, só lista o que juntaria', r.replace(/\n/g, ' | ').slice(0, 220))
  const antes = Number((t.semCor(await t.console('spawners lista')).match(/colocados: (\d+)/) || [, -1])[1])
  r = t.semCor(await t.console('spawners juntar confirmar'))
  const depois = Number((t.semCor(await t.console('spawners lista')).match(/colocados: (\d+)/) || [, -1])[1])
  t.verificar(/Juntei/.test(r) && depois < antes, 'com confirmar, junta (menos blocos)', `${antes} → ${depois} | ${r.replace(/\n/g, ' | ').slice(0, 160)}`)
  r = t.semCor(await t.console('spawners juntar'))
  t.verificar(/Nenhum terreno/.test(r), 'e não sobra nada separado', r)
}

async function maquinas(bot, irProTerreno) {
  const nome = bot.username
  console.log('\n== Máquinas: produção offline na metade ==')
  await t.console(`ranks admin set ${nome} fazendeiro`)
  await t.console(`minecraft:clear ${nome}`)
  await t.console(`maquinas dar ${nome} tear`)
  await t.console(`maquinas dar ${nome} combustivel 16`)
  await t.espera(500)
  const lugar = bot.entity.position.floored().offset(0, 0, 2)
  await colocar(bot, 'barrier', lugar)
  let j = await janela(bot, () => bot.activateBlock(bot.blockAt(lugar)))
  const info = j ? t.semCor(JSON.stringify(j.slots[4] ?? '')) : ''
  t.verificar(/com você offline, 30/.test(info), 'o menu mostra a produção offline (30 por hora no Tear)', info.match(/Fabrica[^"]*/)?.[0])
  if (!j) return
  await bot.clickWindow(12, 0, 0) // abastecer
  await t.espera(1200)
  bot.closeWindow(bot.currentWindow)

  bot.quit()
  await t.espera(1500)
  await t.console('maquinas acelerar 30') // offline: 30 min valem 15
  const bot2 = await t.criarBot(nome)
  await t.espera(1500)
  await t.console('maquinas acelerar 30') // online: 30
  await irProTerreno()
  await t.espera(1500)
  j = await janela(bot2, () => bot2.activateBlock(bot2.blockAt(lugar)))
  if (j) {
    await bot2.clickWindow(10, 0, 0) // coletar
    await t.espera(1200)
    bot2.closeWindow(bot2.currentWindow)
  }
  const tecidos = await contar(nome, 'paper')
  t.verificar(tecidos === 45, '30 min offline + 30 min online = 15 + 30 Tecidos', `${tecidos}`)
  j = await janela(bot2, () => bot2.activateBlock(bot2.blockAt(lugar)))
  if (j) await bot2.clickWindow(22, 0, 0) // recolher
  await t.espera(1000)
  await t.console(`minecraft:clear ${nome}`)
  return bot2
}

async function pescar(bot) {
  try {
    await Promise.race([bot.fish(), t.espera(40000).then(() => { throw new Error('nada fisgou em 40 s') })])
  } catch (e) {
    console.log('  pesca:', e.message)
    bot.activateItem()
  }
  await t.espera(800)
}

async function pesca(bot) {
  const nome = bot.username
  console.log('\n== Pesca: o lixo vira peixe ==')
  const { x, y, z } = TANQUE
  await t.console(`skills admin setlevel ${nome} pescador 1`)
  await t.console(`minecraft:clear ${nome}`)
  await t.console(`minecraft:give ${nome} fishing_rod[enchantments={lure:3,unbreaking:3}]`)
  await t.console(`minecraft:effect give ${nome} slow_falling 10 0 true`)
  await t.console(`execute in minecraft:spawn run minecraft:tp ${nome} ${x + 0.5} ${y + 2} ${z - 2.5} 0 30`)
  await t.espera(2000)
  await t.console(`execute in minecraft:spawn run fill ${x - 4} ${y - 4} ${z - 4} ${x + 4} ${y + 4} ${z + 4} air`)
  await t.console(`execute in minecraft:spawn run fill ${x - 3} ${y - 4} ${z - 3} ${x + 3} ${y - 1} ${z + 3} stone`)
  await t.console(`execute in minecraft:spawn run fill ${x - 2} ${y - 3} ${z - 2} ${x + 2} ${y - 1} ${z + 2} water`)
  await t.console(`execute in minecraft:spawn run minecraft:tp ${nome} ${x + 0.5} ${y} ${z - 2.5} 0 30`)
  await t.espera(1500)
  await bot.equip(bot.inventory.items().find(i => i.name === 'fishing_rod'), 'hand')
  const N = 25
  for (let i = 0; i < N; i++) await pescar(bot)
  await t.espera(1000)
  const itens = bot.inventory.items().filter(i => i.slot !== bot.inventory.items().find(v => v.name === 'fishing_rod')?.slot)
  const lixo = itens.filter(i => LIXO.includes(i.name))
  const peixes = itens.filter(i => PEIXES.includes(i.name)).reduce((s, i) => s + i.count, 0)
  t.verificar(lixo.length === 0 && peixes >= N - 3, `${N} pescas: nenhum lixo, só peixe`, itens.map(i => i.name + 'x' + i.count).join(','))
  await t.console(`execute in minecraft:spawn run fill ${x - 4} ${y - 4} ${z - 4} ${x + 4} ${y + 4} ${z + 4} air`)
}

t.rodar(async () => {
  const original = fs.readFileSync(CONFIG_SKILLS, 'utf8')
  let bot
  try {
    fs.writeFileSync(CONFIG_SKILLS, original.replace(/\s*$/, '\n') + '\npescador:\n' + lootDeTeste() + '\n')
    await t.console('skills admin reload')
    bot = await t.criarBot(t.nomeNovo('Aj'))
    await t.espera(1000)
    await t.console(`eco set ${bot.username} 100000`)
    await t.console(`minecraft:clear ${bot.username}`)
    const irProTerreno = await terreno(bot)
    await spawners(bot)
    await irProTerreno()
    await t.espera(1000)
    bot = await maquinas(bot, irProTerreno) || bot
    await t.capturar(bot, () => bot.chat('/terreno abandonar confirmar'), 1500)
    await t.console(`ranks admin set ${bot.username} campones`)
    await pesca(bot)
  } finally {
    fs.writeFileSync(CONFIG_SKILLS, original)
    await t.console('skills admin reload')
  }
  await t.terminar(bot)
})
