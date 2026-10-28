// PvP em ciclos na warp mobs (AreasPlugin): da linha vermelha pra dentro, 15 min sem e 15 com. Com o
// PvP ligado um bot machuca o outro no salão, mas não no vestíbulo; desligado, não machuca. O painel da
// parede (moldura verde/vermelha e holograma) acompanha a fase. E o resto continua: a arena do boss com
// PvP sempre, o meio do spawn sem nunca.
// Uso: node teste-pvp-ciclo.js   (depois do "node montar-mapa.js construir mobs")
const t = require('./lib')

const vida = async nome => parseFloat((await t.console(`data get entity ${nome} Health`)).split(':').pop())

/** Põe os dois bots lado a lado em (x, y, z), cura, e o A bate no B. Devolve quanto o B perdeu. */
async function golpe(a, b, [x, y, z]) {
  for (const [bot, dz] of [[a, 0], [b, 1.5]]) {
    await t.console(`execute in minecraft:spawn run minecraft:tp ${bot.username} ${x} ${y} ${z + dz} 0 0`)
    await t.console(`minecraft:effect give ${bot.username} minecraft:instant_health 1 5 true`)
  }
  await t.espera(5000) // o Essentials deixa invulnerável 4 s depois do teleporte
  // os mobs do salão também batem: some com os de perto antes de medir
  await t.console(`execute in minecraft:spawn positioned ${x} ${y} ${z} run minecraft:kill @e[type=!player,type=!text_display,type=!item_frame,distance=..24]`)
  const antes = await vida(b.username)
  const alvo = Object.values(a.entities).find(e => e.type === 'player' && e.username === b.username)
  if (alvo) {
    await a.lookAt(alvo.position.offset(0, 1.6, 0), true)
    a.attack(alvo)
  }
  await t.espera(1200)
  const perdeu = antes - (await vida(b.username))
  console.log(`  golpe em ${x} ${y} ${z}: ${alvo ? 'acertou' : 'B não visto'}, vida ${antes} -> perdeu ${perdeu}`)
  return perdeu
}

const bloco = async (x, y, z, tipo) => /passed/.test(await t.console(`execute in minecraft:spawn if block ${x} ${y} ${z} ${tipo}`))

t.rodar(async () => {
  const a = await t.criarBot(t.nomeNovo('PvpA'))
  const b = await t.criarBot(t.nomeNovo('PvpB'))
  for (const bot of [a, b]) {
    await t.console(`gamemode survival ${bot.username}`)
    await t.console(`minecraft:clear ${bot.username}`)
  }
  await t.console(`minecraft:item replace entity ${a.username} hotbar.0 with minecraft:iron_sword`)
  a.setQuickBarSlot(0)
  const SALAO = [-150.5, 49, 43.5], VESTIBULO = [-86.5, 49, 50.5]

  console.log('\n== PvP ligado ==')
  console.log('  ' + await t.console('areas ciclo mobs ligado'))
  await t.espera(1500)
  t.verificar((await golpe(a, b, SALAO)) > 0, 'com o PvP ligado, um machuca o outro no salão')
  t.verificar((await golpe(a, b, VESTIBULO)) === 0, 'no vestíbulo (antes da linha vermelha) não machuca nem com o PvP ligado')
  await t.console('execute in minecraft:spawn run forceload add -84 31 -83 55')
  await t.espera(2500)
  t.verificar(await bloco(-83, 62, 31, 'red_concrete') && await bloco(-83, 70, 55, 'red_concrete'), 'a moldura do painel fica vermelha')
  const holo = await t.console('execute in minecraft:spawn positioned -84.3 64.6 43.5 run data get entity @e[type=text_display,tag=areas-ciclo-pvp,limit=1,distance=..2] text')
  t.verificar(/LIGADO/.test(holo), 'o holograma da parede diz que o PvP está ligado', holo.slice(0, 160))

  console.log('\n== PvP desligado ==')
  console.log('  ' + await t.console('areas ciclo mobs desligado'))
  await t.espera(1500)
  t.verificar((await golpe(a, b, SALAO)) === 0, 'com o PvP desligado, ninguém machuca ninguém no salão')
  t.verificar(await bloco(-83, 62, 31, 'lime_concrete'), 'a moldura volta pro verde')
  const holo2 = await t.console('execute in minecraft:spawn positioned -84.3 64.6 43.5 run data get entity @e[type=text_display,tag=areas-ciclo-pvp,limit=1,distance=..2] text')
  t.verificar(/DESLIGADO/.test(holo2), 'e o holograma diz que o PvP está desligado', holo2.slice(0, 160))
  await t.console('execute in minecraft:spawn run forceload remove -84 31 -83 55')

  console.log('\n== O resto do spawn não muda ==')
  t.verificar((await golpe(a, b, [0.5, 33, 160.5])) > 0, 'na arena do boss o PvP fica sempre ligado')
  t.verificar((await golpe(a, b, [0.5, 65, 60.5])) === 0, 'no meio do spawn o PvP fica sempre desligado')

  await t.terminar(a, b)
})
