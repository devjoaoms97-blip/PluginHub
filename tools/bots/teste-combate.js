// Combate no estilo 1.8 (CombatePlugin): sem recarga de ataque, bloqueio com a espada (só sem
// escudo na segunda mão) e golpe com a arma da segunda mão. Luta numa arena de X1 vazia, onde o
// PvP vale. Precisa das arenas (node montar-arenas.js). Uso: node teste-combate.js
const t = require('./lib')

const W = 'execute in minecraft:arenas run'

/** Vida que o alvo perde com um golpe do atacante (espera a vida voltar a cheia antes). */
async function golpe(atacante, alvo, comoBater) {
  await t.console(`effect give ${alvo.username} minecraft:instant_health 1 10 true`)
  await t.espera(800)
  const antes = alvo.health
  const e = Object.values(atacante.entities).find(x => x.username === alvo.username)
  await atacante.lookAt(e.position.offset(0, 1.6, 0), true)
  await comoBater(e)
  await t.espera(700)
  return antes - alvo.health
}

const bater = atacante => e => atacante.attack(e)
const baterComASegunda = atacante => e => atacante._client.write('use_entity', {
  target: e.id, mouse: 0, sneaking: false, hand: 1, location: { x: 0, y: 0, z: 0 },
})

t.rodar(async () => {
  const a = await t.criarBot(t.nomeNovo('Cma')), b = await t.criarBot(t.nomeNovo('Cmb'))
  for (const bot of [a, b]) {
    await t.console(`gamemode survival ${bot.username}`)
    await t.console(`minecraft:clear ${bot.username}`)
    await t.console(`minecraft:give ${bot.username} diamond_sword`)
  }
  await t.console(`${W} minecraft:tp ${a.username} 99.5 65 40.5 -90 0`)
  await t.console(`${W} minecraft:tp ${b.username} 101.5 65 40.5 90 0`)
  await t.espera(1500)
  for (const bot of [a, b]) await bot.equip(bot.inventory.items().find(i => i.name === 'diamond_sword'), 'hand')
  await t.espera(500)

  console.log('\n== Sem recarga ==')
  const velocidade = Number(((await t.console(`attribute ${a.username} minecraft:attack_speed get`)).match(/([\d.]+)\s*$/) || [])[1])
  t.verificar(velocidade > 100, 'velocidade de ataque enorme (barra sempre cheia)', `${velocidade}`)
  const g1 = await golpe(a, b, bater(a))
  // dois golpes com 0,55 s de intervalo (logo depois do meio segundo de invulnerabilidade): sem o
  // plugin, a barra da espada (0,625 s) não teria enchido e o segundo sairia com uns 80% do dano
  const g2 = await golpe(a, b, async e => { a.attack(e); await t.espera(550); a.attack(e) })
  // compara com o golpe isolado: a skill Defesa de quem apanha sobe e muda um pouco o dano
  t.verificar(g2 >= g1 * 1.9, 'golpes rápidos saem com o dano inteiro (dois seguidos tiram o dobro de um)',
    `um golpe ${g1.toFixed(1)}, dois seguidos ${g2.toFixed(1)}`)

  console.log('\n== Tempo entre golpes (configurável) ==')
  const rajada = () => golpe(a, b, async e => { a.attack(e); await t.espera(150); a.attack(e) })
  await t.console('combate intervalo 10')
  const padrao = await rajada()
  t.verificar(padrao < g1 * 1.4, 'padrão (10 ticks): o 2º golpe em 0,15 s não conta', `${padrao.toFixed(1)} (um golpe: ${g1.toFixed(1)})`)
  await t.console('combate intervalo 0')
  const semEspera = await rajada()
  t.verificar(semEspera >= g1 * 1.8, 'com 0: os dois golpes em 0,15 s contam', `${semEspera.toFixed(1)}`)
  await t.console('combate intervalo 10')

  console.log('\n== Bloqueio com a espada ==')
  const comp = await t.console(`data get entity ${b.username} SelectedItem.components`)
  t.verificar(comp.includes('blocks_attacks'), 'a espada na mão ganhou o bloqueio')
  b.activateItem()
  await t.espera(400)
  const bloqueado = await golpe(a, b, bater(a))
  b.deactivateItem()
  t.verificar(bloqueado > 0 && bloqueado <= g1 * 0.6, 'bloqueando, o golpe tira metade', `${bloqueado.toFixed(1)} de ${g1.toFixed(1)}`)

  await t.console(`minecraft:item replace entity ${b.username} weapon.offhand with minecraft:shield`)
  await t.espera(600)
  const comEscudo = await t.console(`data get entity ${b.username} SelectedItem.components`)
  t.verificar(!comEscudo.includes('blocks_attacks'), 'com escudo na segunda mão, a espada não bloqueia (o escudo bloqueia)')
  await t.console(`minecraft:item replace entity ${b.username} weapon.offhand with minecraft:air`)

  console.log('\n== Duas armas ==')
  await t.console(`minecraft:item replace entity ${a.username} weapon.mainhand with minecraft:air`)
  await t.console(`minecraft:item replace entity ${a.username} weapon.offhand with minecraft:diamond_sword`)
  await t.espera(600)
  const soco = await golpe(a, b, bater(a))
  const segunda = await golpe(a, b, baterComASegunda(a))
  t.verificar(soco <= 2, 'com a mão principal vazia, o soco tira pouco', soco.toFixed(1))
  t.verificar(segunda >= g1 * 0.9, 'o botão direito bate com a espada da segunda mão', `${segunda.toFixed(1)} (com a principal: ${g1.toFixed(1)})`)
  const offhand = await t.console(`data get entity ${a.username} equipment.offhand.id`)
  t.verificar(offhand.includes('diamond_sword'), 'a espada continua na segunda mão depois do golpe', offhand)

  console.log('\n== Cada mão com o seu tempo, e armas diferentes ==')
  await t.console(`minecraft:item replace entity ${a.username} weapon.mainhand with minecraft:diamond_sword`)
  await t.console(`minecraft:item replace entity ${a.username} weapon.offhand with minecraft:diamond_axe`)
  await t.espera(600)
  // espada e machado com 0,15 s de intervalo, no tempo padrão (10 ticks): cada mão tem o seu tempo
  const alternado = await golpe(a, b, async e => { a.attack(e); await t.espera(150); baterComASegunda(a)(e) })
  t.verificar(alternado >= g1 * 1.9, 'espada e machado alternados em 0,15 s: os dois golpes contam', `${alternado.toFixed(1)} (só a espada: ${g1.toFixed(1)})`)
  const mesmaMao = await golpe(a, b, async e => { baterComASegunda(a)(e); await t.espera(150); baterComASegunda(a)(e) })
  t.verificar(mesmaMao < g1 * 1.6, 'a mesma mão duas vezes em 0,15 s: só um golpe conta', mesmaMao.toFixed(1))

  console.log('\n== Soco com a segunda mão ==')
  await t.console(`minecraft:item replace entity ${a.username} weapon.offhand with minecraft:air`)
  await t.espera(600)
  const socoSegunda = await golpe(a, b, baterComASegunda(a))
  t.verificar(socoSegunda > 0 && socoSegunda <= 2, 'com a segunda mão vazia, o botão direito é um soco', socoSegunda.toFixed(1))
  const principalDepois = await t.console(`data get entity ${a.username} SelectedItem.id`)
  t.verificar(principalDepois.includes('diamond_sword'), 'e a espada continua na mão principal')

  await t.terminar(a, b)
})
