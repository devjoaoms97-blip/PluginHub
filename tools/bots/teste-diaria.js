// Recompensa diária: aviso ao entrar, dia 1 com o mínimo, não repete no mesmo dia, dia seguinte
// avança a sequência e perder um dia volta pro 1. Usa /diaria admin pra mexer na data.
// Uso: node teste-diaria.js
const t = require('./lib')

const dinheiro = r => Number((r.match(/\$([\d,.]+)/) || [, 'NaN'])[1].replace(/,/g, ''))
const saldo = async nome => dinheiro(await t.console(`balance ${nome}`))

t.rodar(async () => {
  const bot = await t.criarBot(t.nomeNovo('Dia'))
  const nome = bot.username
  await t.espera(2500)
  t.verificar(bot.mensagens.some(m => m.includes('recompensa diária está disponível')), 'aviso ao entrar')
  await t.console(`eco set ${nome} 0`)

  let msgs = await t.capturar(bot, () => bot.chat('/diaria'))
  t.verificar(msgs.some(m => m.includes('Dia 1')), 'resgatou o dia 1', msgs[0])
  t.verificar(await saldo(nome) === 200, 'no primeiro rank, vale o mínimo (R$ 200)', `${await saldo(nome)}`)
  msgs = await t.capturar(bot, () => bot.chat('/diaria'))
  t.verificar(msgs.some(m => m.includes('já resgatou hoje')), 'não repete no mesmo dia')

  await t.console(`diaria admin ontem ${nome}`)
  msgs = await t.capturar(bot, () => bot.chat('/diaria'))
  t.verificar(msgs.some(m => m.includes('Dia 2')), 'no dia seguinte, a sequência avança', msgs[0])

  await t.console(`diaria admin anteontem ${nome}`)
  msgs = await t.capturar(bot, () => bot.chat('/diaria'))
  t.verificar(msgs.some(m => m.includes('Dia 1')), 'perdendo um dia, volta pro dia 1', msgs[0])

  const painel = await t.console('economia tipos DiariaPlugin')
  t.verificar(painel.includes('Recompensa diária'), 'o painel mostra a recompensa diária', painel.slice(0, 100))
  await t.terminar(bot)
})
