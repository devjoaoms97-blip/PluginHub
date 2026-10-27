// Agenda do boss com boss fixo (o Wither no domingo) e o código de VIP de evento de 7 dias só pro
// campeão do Wither: o zumbi (boss normal) não dá. O bot "mata" o boss pelo /damage em nome dele.
// Uso: node teste-boss-vip.js
const t = require('./lib')

// bate até o boss morrer (o Wither ignora dano nos primeiros segundos, enquanto "nasce")
async function matar(bot, tipo) {
  for (let i = 0; i < 20; i++) {
    const r = await t.console(`damage @e[type=${tipo},limit=1] 100000 minecraft:player_attack by ${bot.username}`)
    if (/No entity was found|Nenhuma entidade/i.test(r)) return true
    await t.espera(1000)
  }
  return false
}

t.rodar(async () => {
  const bot = await t.criarBot(t.nomeNovo('Bvip'))
  const nome = bot.username
  await t.console('boss stop')

  console.log('\n== Agenda ==')
  let msgs = await t.capturar(bot, () => bot.chat('/boss proximo'))
  t.verificar(msgs.some(m => /Próximo boss/.test(m)), 'o /boss proximo mostra o próximo horário', msgs[0])
  if (new Date().getDay() === 6 && new Date().getHours() >= 20) {
    t.verificar(msgs.some(m => /Flagelo/.test(m)), 'sábado à noite, o próximo é o Wither de domingo', msgs[0])
  }

  console.log('\n== Boss normal não dá VIP ==')
  await t.console('boss start zombie')
  await t.espera(1500)
  msgs = await t.capturar(bot, async () => { await matar(bot, 'zombie') }, 3000)
  t.verificar(msgs.some(m => /campeão/i.test(m)), `${nome} vira o campeão do zumbi`, msgs.join(' | ').slice(0, 160))
  t.verificar(!msgs.some(m => /código de VIP/.test(m)), 'e não ganha código de VIP', msgs.join(' | ').slice(0, 160))

  console.log('\n== O Wither dá o VIP de evento de 7 dias ==')
  await t.console('boss start wither')
  await t.espera(1500)
  msgs = await t.capturar(bot, async () => { await matar(bot, 'wither') }, 3000)
  const linha = msgs.find(m => /código de VIP de evento/.test(m)) || ''
  t.verificar(/7 dias/.test(linha), 'o campeão do Wither ganha o código de VIP de evento de 7 dias', linha || msgs.join(' | ').slice(0, 200))
  const codigo = (linha.match(/dias\): (?:VIP-)?([A-Z0-9]+)/) || [])[1]
  if (codigo) {
    const r = await t.console('vip codigos')
    t.verificar(new RegExp(codigo).test(r), 'o código existe no VipPlugin', r.slice(0, 160))
    await t.console(`vip revogarcodigo ${codigo}`)
  }

  await t.console('boss stop')
  await t.console('minecraft:kill @e[type=item]')
  await t.console(`clear ${nome}`)
  await t.terminar(bot)
})
