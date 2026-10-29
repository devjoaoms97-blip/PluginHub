// Mundo arenas (AreasPlugin): PvP só no piso de luta do X1 e do coliseu; as plateias (galeria do X1 e deck
// de vidro do coliseu) e o saguão ficam sem PvP. Uso: node teste-pvp-arenas.js
const t = require('./lib')

const vida = async nome => parseFloat((await t.console(`data get entity ${nome} Health`)).split(':').pop())

/** Põe os dois lado a lado em (x, y, z) do mundo arenas, cura, e o A bate no B. Devolve quanto o B perdeu. */
async function golpe(a, b, [x, y, z]) {
  await t.espera(5500) // o golpe anterior deixou os dois 5 s em combate, e em combate não se teleporta
  for (const [bot, dx] of [[a, 0], [b, 1.5]]) {
    await t.console(`execute in minecraft:arenas run minecraft:tp ${bot.username} ${x + dx} ${y} ${z} 90 0`)
    await t.console(`minecraft:effect give ${bot.username} minecraft:instant_health 1 5 true`)
  }
  await t.espera(5000) // o Essentials deixa invulnerável 4 s depois do teleporte
  const antes = await vida(b.username)
  const alvo = Object.values(a.entities).find(e => e.type === 'player' && e.username === b.username)
  if (alvo) {
    await a.lookAt(alvo.position.offset(0, 1.6, 0), true)
    a.attack(alvo)
  }
  await t.espera(1200)
  const perdeu = antes - (await vida(b.username))
  console.log(`  golpe em ${x} ${y} ${z}: ${alvo ? 'acertou' : 'B não visto'}, perdeu ${perdeu}`)
  return perdeu
}

t.rodar(async () => {
  const a = await t.criarBot(t.nomeNovo('Paa')), b = await t.criarBot(t.nomeNovo('Pab'))
  for (const n of [a.username, b.username]) await t.console(`gamemode survival ${n}`)

  t.verificar((await golpe(a, b, [-600.5, 80, -14.5])) === 0, 'na plateia do X1 não tem PvP')
  t.verificar((await golpe(a, b, [-600.5, 65, 0.5])) > 0, 'no piso do X1 tem PvP')
  t.verificar((await golpe(a, b, [599.5, 77, -65.5])) === 0, 'no deck da plateia do coliseu não tem PvP')
  t.verificar((await golpe(a, b, [600.5, 66, 0.5])) > 0, 'no piso do coliseu tem PvP')
  t.verificar((await golpe(a, b, [0.5, 65, 0.5])) === 0, 'no saguão das arenas não tem PvP')

  await t.terminar(a, b)
})
