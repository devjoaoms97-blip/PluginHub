// Caixas (CaixasPlugin, 2026-11-18): a Praça das Caixas no nordeste do spawn com as 4 caixas (voto, mina,
// boss e tesouro); sem a chave não abre; clique esquerdo mostra os prêmios; clique direito com a chave abre a
// roleta e entrega um prêmio; agachado abre todas; chave pra quem está offline chega quando ele entra; a
// chave da Mina cai minerando, a do Boss vem do ranking de dano e a do Tesouro está no loot dos baús.
// Precisa da praça (montar-mapa.js construir caixas). Uso: node teste-caixas.js
const fs = require('fs')
const path = require('path')
const { Vec3 } = require('vec3')
const t = require('./lib')

const W = 'execute in minecraft:spawn run'
const PASTA = process.env.MC_TESTE || 'C:\\mc-teste'
const CONFIG = path.join(PASTA, 'plugins', 'CaixasPlugin', 'config.yml')
// (relativo ao centro do spawn 0, 43): praça em 22, -22; voto -4, 4; mina -6, 0; tesouro 0, 6; boss -4, -4
const CAIXAS = { voto: [18, 66, 25], mina: [16, 66, 21], tesouro: [22, 66, 27], boss: [18, 66, 17] }

const chaves = (bot, id) => bot.inventory.items().filter(i => i.name === 'tripwire_hook' && JSON.stringify(i.nbt || i.components || '').includes(id))
  .reduce((n, i) => n + i.count, 0)
const ganchos = bot => bot.inventory.items().filter(i => i.name === 'tripwire_hook').reduce((n, i) => n + i.count, 0)

function titulo(janela) {
  const tt = janela && janela.title
  return typeof tt === 'string' ? tt : JSON.stringify(tt || '')
}

/** Espera abrir uma janela (até ms) e devolve ela, ou null. */
function janelaAberta(bot, ms = 3000) {
  return new Promise(res => {
    const tm = setTimeout(() => res(null), ms)
    bot.once('windowOpen', w => { clearTimeout(tm); res(w) })
  })
}

async function matar(bot, tipo) {
  for (let i = 0; i < 20; i++) {
    const r = await t.console(`damage @e[type=${tipo},limit=1] 100000 minecraft:player_attack by ${bot.username}`)
    if (/No entity was found|Nenhuma entidade/i.test(r)) return true
    await t.espera(1000)
  }
  return false
}

t.rodar(async () => {
  const bot = await t.criarBot(t.nomeNovo('Cxa'))
  const nome = bot.username
  await t.console(`gamemode survival ${nome}`)
  await t.console(`minecraft:clear ${nome}`)
  await t.console(`${W} minecraft:tp ${nome} 15.5 65 25.5 -90 0`)
  await t.espera(2500)

  console.log('\n== A praça ==')
  const lista = await t.console('caixas lista')
  for (const [id, [x, y, z]] of Object.entries(CAIXAS)) {
    t.verificar(new RegExp(`${id}: \\d+ prêmios, spawn ${x} ${y} ${z}`).test(lista), `a caixa ${id} está cadastrada no pedestal dela`, lista.split('\n').find(l => l.startsWith(id)))
    t.verificar(/passed/i.test(await t.console(`${W} execute if block ${x} ${y} ${z} minecraft:vault`)), `o pedestal da ${id} tem o cofre`)
  }
  t.verificar(/passed/i.test(await t.console(`${W} execute if block 22 69 21 minecraft:quartz_pillar`)), 'o obelisco no meio da praça (a cerejeira saiu)')

  const caixa = bot.blockAt(new Vec3(...CAIXAS.voto))
  console.log('\n== Sem chave, não abre ==')
  let msgs = await t.capturar(bot, async () => { await bot.lookAt(caixa.position.offset(0.5, 0.5, 0.5), true); await bot.activateBlock(caixa) }, 1200)
  t.verificar(msgs.some(m => /segure uma .*Chave de Voto/.test(m) && /votar/.test(m)), 'sem a chave, diz qual chave precisa e como conseguir', msgs.join(' | '))

  console.log('\n== Os prêmios (clique esquerdo) ==')
  let espera = janelaAberta(bot)
  bot.dig(caixa, true).catch(() => {})
  let janela = await espera
  bot.stopDigging()
  t.verificar(janela && /prêmios/.test(titulo(janela)), 'o clique esquerdo abre a vitrine dos prêmios', titulo(janela))
  const itensVitrine = janela ? janela.slots.slice(0, 45).filter(Boolean) : []
  t.verificar(itensVitrine.length >= 10, 'a vitrine mostra os prêmios com a chance', `${itensVitrine.length} prêmios`)
  if (janela) bot.closeWindow(janela)
  await t.espera(800)

  console.log('\n== A roleta (clique direito com a chave) ==')
  await t.console(`caixas dar ${nome} voto 3`)
  await t.espera(1000)
  t.verificar(ganchos(bot) === 3, 'o /caixas dar entrega 3 chaves de voto', `${ganchos(bot)}`)
  await bot.equip(bot.inventory.items().find(i => i.name === 'tripwire_hook'), 'hand')
  espera = janelaAberta(bot)
  msgs = await t.capturar(bot, async () => {
    await bot.activateBlock(caixa)
    janela = await espera
    await t.espera(7000)
  }, 500)
  t.verificar(janela && /Caixa de Voto/.test(titulo(janela)), 'abre a roleta da Caixa de Voto', titulo(janela))
  t.verificar(msgs.some(m => /Você ganhou/.test(m)), 'a roleta para e entrega o prêmio', msgs.find(m => /ganhou/.test(m)))
  t.verificar(ganchos(bot) === 2, 'gastou uma chave', `${ganchos(bot)}`)
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow)
  await t.espera(800)

  console.log('\n== Fechar no meio entrega na hora ==')
  espera = janelaAberta(bot)
  msgs = await t.capturar(bot, async () => {
    await bot.activateBlock(caixa)
    janela = await espera
    await t.espera(400)
    if (janela) bot.closeWindow(janela)
  }, 1200)
  t.verificar(msgs.some(m => /Você ganhou/.test(m)), 'fechando a roleta antes do fim, o prêmio sai na hora', msgs.join(' | ').slice(0, 160))
  t.verificar(ganchos(bot) === 1, 'e gastou mais uma chave', `${ganchos(bot)}`)

  console.log('\n== Agachado abre todas ==')
  await t.console(`caixas dar ${nome} voto 4`)
  await t.espera(800)
  await bot.equip(bot.inventory.items().find(i => i.name === 'tripwire_hook'), 'hand')
  bot.setControlState('sneak', true)
  await t.espera(300)
  msgs = await t.capturar(bot, () => bot.activateBlock(caixa), 1500)
  bot.setControlState('sneak', false)
  t.verificar(msgs.some(m => /Você abriu 5x/.test(m)), 'agachado + clique direito abre as 5 chaves da mão de uma vez', msgs.join(' | ').slice(0, 200))
  t.verificar(ganchos(bot) === 0, 'e não sobra chave', `${ganchos(bot)}`)

  console.log('\n== A chave da caixa errada não abre ==')
  await t.console(`caixas dar ${nome} boss 1`)
  await t.espera(800)
  await bot.equip(bot.inventory.items().find(i => i.name === 'tripwire_hook'), 'hand')
  msgs = await t.capturar(bot, () => bot.activateBlock(caixa), 1200)
  t.verificar(msgs.some(m => /segure uma/.test(m)) && ganchos(bot) === 1, 'com a Chave do Boss, a Caixa de Voto não abre', msgs.join(' | '))
  await t.console(`minecraft:clear ${nome}`)

  console.log('\n== Chave pra quem está offline ==')
  const outro = t.nomeNovo('Cxb')
  const r = await t.console(`caixas dar ${outro} tesouro 2`)
  t.verificar(/guardada/.test(r), 'pra quem está offline, a chave fica guardada', r)
  const b = await t.criarBot(outro)
  await t.espera(5000)
  t.verificar(ganchos(b) === 2, 'quando ele entra, as chaves chegam sozinhas', `${ganchos(b)}`)

  console.log('\n== De onde vêm as chaves ==')
  const loot = await t.console('tesouro loot raro')
  t.verificar(/chave/i.test(loot), 'o baú do tesouro raro tem a Chave do Tesouro no loot', loot.slice(0, 200))
  // a chave da Mina: com a chance em 100% por um instante, um bloco quebrado na mina PvP dá a chave
  const original = fs.readFileSync(CONFIG, 'utf8')
  try {
    fs.writeFileSync(CONFIG, original.replace(/chance-por-bloco: [\d.]+/, 'chance-por-bloco: 1'))
    await t.console('caixas reload')
    await t.console(`minecraft:give ${nome} iron_pickaxe`)
    await t.console(`${W} minecraft:tp ${nome} 0.5 65 -202.5 180 40`)
    await t.espera(2500)
    await t.console(`${W} setblock 0 64 -205 minecraft:stone`)
    await t.espera(500)
    await bot.equip(bot.inventory.items().find(i => i.name === 'iron_pickaxe'), 'hand')
    await bot.lookAt(new Vec3(0.5, 64.5, -204.5), true)
    await bot.dig(bot.blockAt(new Vec3(0, 64, -205)), true)
    await t.espera(1200)
    t.verificar(ganchos(bot) === 1, 'minerando numa mina, cai a Chave da Mina', `${ganchos(bot)}`)
  } finally {
    fs.writeFileSync(CONFIG, original)
    await t.console('caixas reload')
  }
  await t.console(`minecraft:clear ${nome}`)
  await t.console(`${W} minecraft:tp ${nome} 0.5 65 43.5`)
  await t.espera(1500)
  await t.console('boss stop')
  await t.console('boss start zombie')
  await t.espera(2000)
  msgs = await t.capturar(bot, async () => { await matar(bot, 'zombie') }, 3000)
  t.verificar(ganchos(bot) === 1 && msgs.some(m => /Chave do Boss/.test(m)), 'o 1º do ranking de dano do boss ganha a Chave do Boss', msgs.filter(m => /Chave|ranking/.test(m)).join(' | '))
  await t.console('boss stop')
  await t.console('minecraft:kill @e[type=item]')

  await t.console(`minecraft:clear ${nome}`)
  await t.console(`minecraft:clear ${outro}`)
  await t.terminar(bot, b)
})
