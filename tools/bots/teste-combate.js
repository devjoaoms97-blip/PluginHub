// Combate no estilo 1.8 (CombatePlugin): sem recarga de ataque, tempo entre golpes de 0,1 s (2 ticks),
// bloqueio com a espada (só sem escudo na segunda mão), a segunda mão que não golpeia mais e a besta
// que trava a espada logo depois de disparar. Luta no pátio da arena de X1 (axepvp), onde o PvP vale. Precisa das arenas (node montar-mapa.js). Uso: node teste-combate.js
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
// o clique direito na entidade com a segunda mão (o golpe da segunda mão, que saiu em 2026-11-17)
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
  await t.console(`${W} minecraft:tp ${a.username} -601.5 65 0.5 -90 0`)
  await t.console(`${W} minecraft:tp ${b.username} -599.5 65 0.5 90 0`)
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
  t.verificar(g2 >= g1 * 1.8, 'golpes rápidos saem com o dano inteiro (dois seguidos tiram o dobro de um)',
    `um golpe ${g1.toFixed(1)}, dois seguidos ${g2.toFixed(1)}`)

  console.log('\n== Tempo entre golpes (configurável) ==')
  const rajada = ms => golpe(a, b, async e => { a.attack(e); await t.espera(ms); a.attack(e) })
  await t.console('combate intervalo 5')
  const cedo = await rajada(100)
  t.verificar(cedo < g1 * 1.4, 'com 5 ticks: o 2º golpe em 0,1 s não conta', `${cedo.toFixed(1)} (um golpe: ${g1.toFixed(1)})`)
  const quatroPorSegundo = await rajada(350)
  t.verificar(quatroPorSegundo >= g1 * 1.8, 'com 5 ticks: o 2º golpe em 0,35 s conta', quatroPorSegundo.toFixed(1))
  await t.console('combate intervalo 10')
  const jogo = await rajada(350)
  t.verificar(jogo < g1 * 1.4, 'com 10 (o do jogo): o 2º golpe em 0,35 s não conta', jogo.toFixed(1))
  await t.console('combate intervalo 0')
  const semEspera = await rajada(100)
  t.verificar(semEspera >= g1 * 1.8, 'com 0: os dois golpes em 0,1 s contam', `${semEspera.toFixed(1)}`)
  await t.console('combate intervalo 2')
  const padrao = await rajada(150)
  t.verificar(padrao >= g1 * 1.8, 'padrão (2 ticks): o 2º golpe em 0,15 s conta', padrao.toFixed(1))

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

  console.log('\n== A segunda mão não golpeia ==')
  await t.console(`minecraft:item replace entity ${a.username} weapon.offhand with minecraft:diamond_axe`)
  await t.espera(600)
  const segunda = await golpe(a, b, baterComASegunda(a))
  t.verificar(segunda < 0.5, 'o botão direito no inimigo com machado na segunda mão não bate', segunda.toFixed(1))
  await t.console(`minecraft:item replace entity ${a.username} weapon.offhand with minecraft:air`)
  await t.espera(600)
  const socoSegunda = await golpe(a, b, baterComASegunda(a))
  t.verificar(socoSegunda < 0.5, 'com a segunda mão vazia, o botão direito no inimigo também não bate', socoSegunda.toFixed(1))

  console.log('\n== Disparar a besta trava a espada ==')
  // besta no slot 1 e espada no 2: carrega, dispara pro alto, troca pra espada e bate (dentro do meio segundo)
  await t.console(`minecraft:item replace entity ${a.username} hotbar.0 with minecraft:crossbow`)
  await t.console(`minecraft:item replace entity ${a.username} hotbar.1 with minecraft:diamond_sword`)
  await t.console(`minecraft:give ${a.username} arrow 8`)
  a.setQuickBarSlot(0)
  await t.espera(600)
  await t.console(`effect give ${b.username} minecraft:instant_health 1 10 true`)
  a.activateItem()
  await t.espera(1600)
  a.deactivateItem()
  await t.espera(800)
  const antes = b.health
  const alvo = Object.values(a.entities).find(x => x.username === b.username)
  await a.look(0, Math.PI / 2, true) // pro céu: a flecha não acerta ninguém
  a.activateItem()
  await t.espera(100)
  a.deactivateItem()
  a.setQuickBarSlot(1)
  await a.lookAt(alvo.position.offset(0, 1.6, 0), true)
  await t.espera(50)
  a.attack(alvo)
  await t.espera(700)
  const depoisDeDisparar = antes - b.health
  t.verificar(depoisDeDisparar < 0.5, 'logo depois de disparar, a espada não golpeia', depoisDeDisparar.toFixed(1))
  const meioSegundo = await golpe(a, b, bater(a))
  t.verificar(meioSegundo >= g1 * 0.9, 'meio segundo depois, a espada volta a golpear', meioSegundo.toFixed(1))

  await t.terminar(a, b)
})
