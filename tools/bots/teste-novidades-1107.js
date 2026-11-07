// O lote de 2026-11-07: /sethome barrado no mundo dos tesouros; /echest com 9 espaços (sem VIP) e 54 no
// Diamante, guardando os itens; /ricos com a posição de quem pediu; a skill de profissão no placar com a
// picareta na mão; e a barra das missões no alto da tela.
// Uso: node teste-novidades-1107.js
const t = require('./lib')

t.rodar(async () => {
  const bot = await t.criarBot(t.nomeNovo('Nov'), { comandos: true })
  const nome = bot.username
  const barras = []
  bot.on('bossBarCreated', b => barras.push(t.semCor(b.title.toString())))
  bot.on('bossBarUpdated', b => barras.push(t.semCor(b.title.toString())))

  console.log('\n== /sethome no mundo dos tesouros ==')
  await t.console(`execute in minecraft:tesouros run minecraft:tp ${nome} 40.5 100 40.5`)
  await t.espera(2500)
  let msgs = await t.capturar(bot, () => bot.chat('/sethome tesouro'), 1500)
  t.verificar(msgs.some(m => /não dá pra marcar uma home/i.test(m)), 'no mundo dos tesouros não dá /sethome', msgs.join(' | '))
  await t.console(`execute in minecraft:spawn run minecraft:tp ${nome} 0.5 65 43.5`)
  await t.espera(2000)

  console.log('\n== /echest ==')
  const abrir = async () => {
    const janela = new Promise(res => { bot.once('windowOpen', res); setTimeout(() => res(null), 4000) })
    bot.chat('/echest')
    return janela
  }
  let w = await abrir()
  t.verificar(!!w && w.inventoryStart === 9, 'sem VIP, o /echest abre com 9 espaços', w ? `${w.inventoryStart} espaços` : 'não abriu')
  // põe um diamante no slot 0 do baú (do inventário do bot)
  await t.console(`minecraft:give ${nome} minecraft:diamond 5`)
  await t.espera(800)
  if (w) {
    bot.closeWindow(w)
    w = await abrir()
    const diamante = w && w.slots.findIndex((s, i) => i >= w.inventoryStart && s && s.name === 'diamond')
    if (w && diamante >= 0) {
      await bot.clickWindow(diamante, 0, 0)
      await bot.clickWindow(0, 0, 0)
    }
    await t.espera(500)
    bot.closeWindow(w)
  }
  await t.espera(800)
  w = await abrir()
  t.verificar(!!w && w.slots[0] && w.slots[0].name === 'diamond' && w.slots[0].count === 5, 'o baú guarda os itens', w && w.slots[0] ? `${w.slots[0].count}x ${w.slots[0].name}` : 'vazio')
  if (w) bot.closeWindow(w)
  await t.console(`vip add ${nome} diamante`)
  await t.espera(1000)
  w = await abrir()
  t.verificar(!!w && w.inventoryStart === 54 && w.slots[0]?.name === 'diamond', 'no VIP Diamante, 54 espaços (com o que já tinha)', w ? `${w.inventoryStart} espaços` : 'não abriu')
  if (w) bot.closeWindow(w)
  await t.console(`vip remove ${nome}`)

  console.log('\n== /ricos ==')
  await t.console(`eco set ${nome} 1234`)
  await t.console('economia recalcular')
  msgs = await t.capturar(bot, () => bot.chat('/ricos'), 1500)
  t.verificar(msgs.some(m => m.includes('Os mais ricos')), 'o /ricos mostra o ranking', msgs[0])
  t.verificar(msgs.some(m => /Você: \d+º|Nov/.test(m)), 'e a posição de quem pediu (ou ele no top)', msgs.find(m => m.includes('Você')) || '')

  console.log('\n== /tophoras ==')
  msgs = await t.capturar(bot, () => bot.chat('/tophoras'), 1500)
  t.verificar(msgs.some(m => m.includes('Quem mais jogou')), 'o /tophoras mostra o ranking', msgs[0])
  t.verificar(msgs.some(m => /\d+h \d{2}min/.test(m)), 'com as horas de cada um', msgs.find(m => /min/.test(m)) || '')

  console.log('\n== A skill no placar ==')
  await t.console(`minecraft:item replace entity ${nome} weapon.mainhand with minecraft:iron_pickaxe`)
  await t.espera(3500) // o placar atualiza a cada 2 s
  // as linhas do placar como o jogador vê (o mineflayer não lê o nome das linhas do placar)
  const placar = await t.console(`placar ver ${nome}`)
  t.verificar(/Skill: Minerador/.test(placar), 'com a picareta na mão, o placar mostra o Minerador', (placar.match(/Skill:[^\n]*/) || [placar.slice(0, 120)])[0])

  console.log('\n== A barra das missões ==')
  t.verificar(barras.some(b => /📋|do dia/.test(b)), 'a missão do dia aparece na barra do alto', barras.slice(-2).join(' | '))

  await t.terminar(bot)
})
