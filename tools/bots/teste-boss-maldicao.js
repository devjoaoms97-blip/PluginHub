// Boss que ninguém derrota no tempo limite vai embora e amaldiçoa o servidor: XP de skill -10% até
// o próximo boss morrer (ou 24 h). Precisa de "tempo-limite-minutos: 2" e "avisos-fuga-minutos: [1]"
// no config do BossPlugin do servidor de testes (o padrão é 60 min); o teste leva uns 3 minutos.
// Uso: node teste-boss-maldicao.js
const t = require('./lib')

// XP de cada skill do bot ("x/y XP" no /skills ver)
async function xpPorSkill(bot) {
  const msgs = await t.capturar(bot, () => bot.chat('/skills ver'), 1500)
  const xp = {}
  for (const m of msgs) {
    const r = m.match(/^(.+?) - Nível \d+ \(([\d.]+)\//)
    if (r) xp[r[1]] = Number(r[2].replace(/\./g, ''))
  }
  return xp
}

// o bot mata um zumbi de 20 de vida com um golpe de 20 e devolve o XP que a skill da arma ganhou (a que
// mais subiu: Regeneração e Agilidade sobem sozinhas um pouco com o tempo)
async function xpDeUmAbate(bot) {
  const antes = await xpPorSkill(bot)
  await t.console(`execute in minecraft:spawn run summon minecraft:zombie 0.5 65 24.5 {Tags:["teste_maldicao"],NoAI:1b,Health:20f}`)
  await t.espera(500)
  await t.console(`damage @e[tag=teste_maldicao,limit=1] 20 minecraft:player_attack by ${bot.username}`)
  await t.espera(1000)
  await t.console('minecraft:kill @e[tag=teste_maldicao]')
  const depois = await xpPorSkill(bot)
  return Math.max(0, ...Object.keys(depois).map(k => depois[k] - (antes[k] || 0)))
}

t.rodar(async () => {
  await t.console('boss stop')
  const normal = await t.criarBot(t.nomeNovo('Mal'))
  await t.console(`execute in minecraft:spawn run minecraft:tp ${normal.username} 0.5 65 20.5`)
  await t.espera(1000)
  const xpNormal = await xpDeUmAbate(normal)
  t.verificar(xpNormal > 0, `sem maldição, o abate dá ${xpNormal} de XP`)

  console.log('\n== O boss vai embora (limite de 2 min) ==')
  await t.console('boss start zombie')
  let msgs = await t.capturar(normal, () => t.espera(1000), 150000)
  t.verificar(msgs.some(m => /vai embora em 1 min/.test(m)), 'avisa 1 min antes', msgs.filter(m => /embora/.test(m))[0])
  t.verificar(msgs.some(m => /não foi derrotado/.test(m)), 'e anuncia a maldição pro servidor', msgs.filter(m => /não foi derrotado/.test(m))[0])
  const r = await t.console('boss stop')
  t.verificar(/Nenhum boss/.test(r), 'o boss sumiu', r)

  console.log('\n== Durante a maldição ==')
  const amaldicoado = await t.criarBot(t.nomeNovo('Mal'))
  await t.console(`execute in minecraft:spawn run minecraft:tp ${amaldicoado.username} 0.5 65 20.5`)
  await t.espera(1000)
  const xpMaldito = await xpDeUmAbate(amaldicoado)
  t.verificar(Math.abs(xpMaldito - xpNormal * 0.9) <= 1, `o mesmo abate dá 10% a menos (${xpMaldito} de ${xpNormal})`)
  msgs = await t.capturar(amaldicoado, () => amaldicoado.chat('/boss proximo'))
  t.verificar(msgs.some(m => /O mal reina/.test(m)), 'o /boss proximo mostra a maldição', msgs[0])

  console.log('\n== Derrotar o próximo boss quebra a maldição ==')
  await t.console('boss start zombie')
  await t.espera(1500)
  msgs = await t.capturar(normal, () => t.console(`damage @e[type=zombie,limit=1,sort=furthest] 100000 minecraft:player_attack by ${normal.username}`), 3000)
  t.verificar(msgs.some(m => /maldição foi quebrada/.test(m)), 'a maldição é quebrada', msgs.filter(m => /maldição/.test(m))[0])
  msgs = await t.capturar(normal, () => normal.chat('/boss proximo'))
  t.verificar(!msgs.some(m => /O mal reina/.test(m)), 'e some do /boss proximo', msgs[0])

  await t.console('boss stop')
  await t.console('minecraft:kill @e[type=item]')
  await t.terminar(normal)
  await t.terminar(amaldicoado)
})
