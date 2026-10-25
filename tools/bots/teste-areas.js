// Áreas fechadas (AreasPlugin): no spawn (e nas arenas) a pérola do fim não sai nem é gasta, o /fly
// não liga, e quem entra voando cai. Num terreno (fora das áreas), a pérola funciona normal.
// Uso: node teste-areas.js
const t = require('./lib')

const contar = async (nome, item) => Number(((await t.console(`minecraft:clear ${nome} ${item} 0`)).match(/Found (\d+)/) || [, 0])[1])
const voando = async nome => /1b/.test(await t.console(`data get entity ${nome} abilities.flying`))

t.rodar(async () => {
  const bot = await t.criarBot(t.nomeNovo('Are'))
  const nome = bot.username
  await t.espera(1000)
  await t.console(`gamemode survival ${nome}`)
  await t.console(`minecraft:clear ${nome}`)

  console.log('\n== Pérola no spawn ==')
  await t.console(`execute in minecraft:spawn run minecraft:tp ${nome} 0.5 65 60.5 0 0`)
  await t.espera(1200)
  await t.console(`minecraft:item replace entity ${nome} weapon.mainhand with minecraft:ender_pearl 4`)
  await t.espera(500)
  const antes = bot.entity.position.clone()
  let msgs = await t.capturar(bot, async () => { bot.activateItem(); await t.espera(200); bot.deactivateItem() }, 2000)
  t.verificar(await contar(nome, 'ender_pearl') === 4 && bot.entity.position.distanceTo(antes) < 1.5,
    'no spawn, a pérola não sai e não é gasta', msgs.find(m => m.includes('Aqui não dá')) || '')

  console.log('\n== Voo no spawn ==')
  await t.console(`fly ${nome} on`)
  await t.espera(500)
  bot.creative.startFlying()
  await t.espera(1500)
  t.verificar(!(await voando(nome)), 'com /fly ligado, não voa no spawn')
  bot.creative.stopFlying()

  console.log('\n== Fora das áreas ==')
  // um terreno à venda qualquer: o mundo dos terrenos não é área fechada
  await t.console(`execute in minecraft:terrenos run minecraft:tp ${nome} 500.5 66 500.5 0 0`)
  await t.espera(2000)
  const longe = bot.entity.position.clone()
  await t.console(`minecraft:item replace entity ${nome} weapon.mainhand with minecraft:ender_pearl 4`)
  await t.espera(500)
  bot.look(0, -0.3, true)
  bot.activateItem()
  await t.espera(200)
  bot.deactivateItem()
  await t.espera(3000)
  t.verificar(await contar(nome, 'ender_pearl') === 3, 'num terreno, a pérola sai normal (gastou uma)',
    `${await contar(nome, 'ender_pearl')} pérolas, andou ${bot.entity.position.distanceTo(longe).toFixed(1)}`)

  await t.console(`fly ${nome} off`)
  await t.console(`minecraft:clear ${nome}`)
  await t.terminar(bot)
})
