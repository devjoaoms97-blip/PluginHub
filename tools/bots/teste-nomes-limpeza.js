// Tag no nome acima da cabeça (PlacarPlugin: um time por jogador em cada scoreboard, com a tag que o
// jogador escolheu no /tag do VipPlugin, só uma) e a limpeza de item em chunk descarregado (ItemCleanerPlugin: sai quando o chunk carrega).
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
  const prefixoDe = () => {
    const time = Object.values(b.teams || {}).find(tm => (tm.members || []).includes(a.username))
    return time ? t.semCor(String(time.prefix?.toString?.() ?? time.prefix ?? '')).trim() : null
  }
  let prefixo = prefixoDe()
  t.verificar(prefixo !== null && /Fazendeiro/i.test(prefixo), 'sem VIP, quem olha vê o rank acima da cabeça do outro', `"${prefixo}"`)
  await t.console(`vip add ${a.username} diamante`)
  await t.espera(4500)
  prefixo = prefixoDe()
  t.verificar(/Diamante/.test(prefixo) && !/Fazendeiro/.test(prefixo), 'com VIP, só a tag do VIP (não todas juntas)', `"${prefixo}"`)
  await t.capturar(a, () => a.chat('/tag rank'), 500)
  await t.espera(4500)
  prefixo = prefixoDe()
  t.verificar(/Fazendeiro/.test(prefixo) && !/Diamante/.test(prefixo), '/tag rank troca pro rank', `"${prefixo}"`)
  await t.capturar(a, () => a.chat('/tag nenhuma'), 500)
  await t.espera(4500)
  prefixo = prefixoDe()
  t.verificar(prefixo === '', '/tag nenhuma tira a tag', `"${prefixo}"`)
  await t.console(`vip remove ${a.username}`)
  await t.capturar(a, () => a.chat('/tag vip'), 500) // sem VIP: recusa

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
