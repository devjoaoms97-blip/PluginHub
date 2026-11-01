// Tags no nome acima da cabeça (PlacarPlugin: um time por jogador em cada scoreboard, com o prefixo da
// tab) e a limpeza de item em chunk descarregado (ItemCleanerPlugin: sai quando o chunk carrega).
// Uso: node teste-nomes-limpeza.js
const t = require('./lib')

// a mina "tesouro", no fim da avenida (longe do spawn e de qualquer jogador)
const [X, Y, Z] = [19, 66, -165] // em cima da mina (dentro dos blocos, o item é empurrado pra fora)

t.rodar(async () => {
  const a = await t.criarBot(t.nomeNovo('Tag'))
  const b = await t.criarBot(t.nomeNovo('Olha'))
  await t.console(`ranks admin set ${a.username} fazendeiro`)
  await t.espera(4500) // o placar (e os times) atualizam a cada 2 s

  console.log('\n== Nome acima da cabeça ==')
  const time = Object.values(b.teams || {}).find(tm => (tm.members || []).includes(a.username))
  const prefixo = time ? t.semCor(String(time.prefix?.toString?.() ?? time.prefix ?? '')) : ''
  t.verificar(!!time && /Fazendeiro/i.test(prefixo), 'quem olha vê o rank no nome acima da cabeça do outro (time com o prefixo da tab)',
    time ? `${time.team}: "${prefixo}"` : 'sem time')

  console.log('\n== Item em chunk descarregado ==')
  const em = 'execute in minecraft:spawn run '
  await t.console(`${em}forceload add ${X} ${Z}`)
  await t.espera(1500)
  await t.console(`${em}summon minecraft:item ${X} ${Y} ${Z} {Item:{id:"minecraft:cobblestone",count:5},Age:-32768}`)
  await t.espera(500)
  const antes = await t.console(`execute in minecraft:spawn positioned ${X} ${Y} ${Z} if entity @e[type=item,distance=..8]`)
  await t.console(`${em}forceload remove ${X} ${Z}`)
  let descarregou = false
  for (let i = 0; i < 20 && !descarregou; i++) {
    await t.espera(1000)
    descarregou = !(await t.console(`execute in minecraft:spawn if loaded ${X} ${Y} ${Z}`)).includes('passed')
  }
  t.verificar(antes.includes('passed') && descarregou, 'o item ficou no chão e o chunk da mina descarregou', antes)
  await t.console('limparchao agora') // a limpeza não alcança o chunk descarregado
  await t.espera(1500)
  await t.console(`${em}forceload add ${X} ${Z}`)
  await t.espera(2500)
  const depois = await t.console(`execute in minecraft:spawn positioned ${X} ${Y} ${Z} if entity @e[type=item,distance=..8]`)
  t.verificar(!depois.includes('passed'), 'quando o chunk carrega, o item de antes da limpeza sai', depois)
  await t.console(`${em}forceload remove ${X} ${Z}`)

  await t.terminar(a, b)
})
