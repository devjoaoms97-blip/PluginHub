// Abate no mundo das arenas não paga Bounty nem conta no placar de abates do clã (dá pra combinar
// a luta). Duelo entre dois clãs diferentes, com recompensa na cabeça de quem perde.
// Precisa das arenas (node montar-arenas.js). Uso: node teste-farm-arenas.js
const t = require('./lib')

const dinheiro = r => Number((r.match(/\$([\d,.]+)/) || [, 'NaN'])[1].replace(/,/g, ''))
const saldo = async nome => dinheiro(await t.console(`balance ${nome}`))
const abates = msgs => Number((msgs.join(' ').match(/[Aa]bates:? *§?f?(\d+)/) || [])[1])

t.rodar(async () => {
  const a = await t.criarBot(t.nomeNovo('Fma')), b = await t.criarBot(t.nomeNovo('Fmb')), c = await t.criarBot(t.nomeNovo('Fmc'))
  for (const bot of [a, b, c]) {
    await t.console(`gamemode survival ${bot.username}`)
    await t.console(`minecraft:clear ${bot.username}`)
    await t.console(`eco set ${bot.username} 50000`)
  }
  await t.console(`minecraft:give ${a.username} diamond_sword`)
  const tagA = 'A' + Date.now().toString(36).slice(-4), tagB = 'B' + Date.now().toString(36).slice(-4)
  await t.capturar(a, () => a.chat(`/cla criar ${tagA} Farm A`))
  await t.capturar(b, () => b.chat(`/cla criar ${tagB} Farm B`))
  let msgs = await t.capturar(c, () => c.chat(`/bounty colocar ${b.username} 10000`))
  t.verificar(msgs.some(m => /recompensa|cabeça/i.test(m)), 'recompensa colocada na cabeça do perdedor', msgs[0])
  msgs = await t.capturar(a, () => a.chat(`/cla info ${tagA}`))
  const abatesAntes = abates(msgs)
  console.log(`  abates do clã ${tagA} antes: ${abatesAntes}`)

  await t.capturar(b, () => a.chat(`/duelo ${b.username}`))
  await t.capturar(a, () => b.chat('/duelo aceitar'), 6500)
  const saldoAntes = await saldo(a.username)
  msgs = await t.capturar(a, () => t.lutar(a, b), 2000)
  t.verificar(msgs.some(m => m.includes('venceu')), 'o duelo terminou')
  t.verificar(msgs.some(m => m.includes('não contam')), 'o Bounty avisa que abate em duelo não paga', msgs.find(m => m.includes('recompensa')))
  t.verificar(await saldo(a.username) - saldoAntes < 1000, 'e não pagou a recompensa', `+${await saldo(a.username) - saldoAntes}`)
  msgs = await t.capturar(c, () => c.chat(`/bounty ver ${b.username}`))
  t.verificar(msgs.some(m => m.includes('10.000') || m.includes('9.000')), 'a recompensa continua valendo', msgs[0])

  await t.espera(21000) // o vencedor sai da arena (lá os comandos ficam bloqueados)
  msgs = await t.capturar(a, () => a.chat(`/cla info ${tagA}`))
  t.verificar(abates(msgs) === abatesAntes, 'o placar de abates do clã não mudou', `${abatesAntes} → ${abates(msgs)}`)
  await t.terminar(a, b, c)
})
