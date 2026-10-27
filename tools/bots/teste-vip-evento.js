// Códigos de VIP de evento (prêmio): só 3, 7 ou 15 dias; o VIP que dão não tem bônus de ativação nem
// kit mensal, mas tem o kit diário e os benefícios passivos (montaria, por exemplo); o código não pode
// ser vendido; quem já tem VIP guarda pra depois; e o mesmo tier comprado por cima soma os dias.
// Uso: node teste-vip-evento.js
const t = require('./lib')

const codigoDe = r => (r.match(/VIP-([A-Z0-9]+)/) || [])[1]

t.rodar(async () => {
  const bot = await t.criarBot(t.nomeNovo('Evt'))
  const nome = bot.username
  await t.console(`vip remove ${nome}`)
  await t.console(`execute in minecraft:spawn run minecraft:tp ${nome} 0.5 65 20.5`)
  await t.espera(1000)

  console.log('\n== Gerar ==')
  let r = await t.console('vip gerarevento bronze 30')
  t.verificar(/Uso/.test(r) && !codigoDe(r), 'só 3, 7 ou 15 dias (30 é recusado)', r.slice(0, 120))
  r = await t.console('vip gerarevento bronze 7')
  const codigo = codigoDe(r)
  t.verificar(!!codigo, 'gera o código de evento de 7 dias', r.slice(0, 120))
  const outro = codigoDe(await t.console('vip gerarevento prata 3'))

  console.log('\n== Não vende ==')
  await t.console(`eco set ${nome} 100000`)
  let msgs = await t.capturar(bot, () => bot.chat(`/vip vender 5000 ${outro}`))
  t.verificar(msgs.some(m => /não dá pra vender/.test(m)), 'o código de evento não pode ser vendido', msgs[0])

  console.log('\n== Resgatar ==')
  const antes = (await t.console(`balance ${nome}`)).split('$').pop()
  msgs = await t.capturar(bot, () => bot.chat(`/vip resgatar ${codigo}`), 2000)
  t.verificar(msgs.some(m => /de evento por 7 dias/.test(m)), 'resgata o VIP Bronze de evento por 7 dias', msgs.join(' | ').slice(0, 160))
  t.verificar(!msgs.some(m => /Bônus de ativação/.test(m)) && (await t.console(`balance ${nome}`)).split('$').pop() === antes,
    'sem bônus de ativação (nem coins nem XP)', msgs.join(' | ').slice(0, 120))
  r = await t.console(`vip check ${nome}`)
  t.verificar(/de evento/.test(r), 'o /vip check mostra que é de evento', r)
  msgs = await t.capturar(bot, () => bot.chat('/vip kitmensal'))
  t.verificar(msgs.some(m => /não dá o kit mensal/.test(m)), 'o kit mensal é recusado', msgs[0])
  msgs = await t.capturar(bot, () => bot.chat('/vip kit'), 1500)
  t.verificar(msgs.some(m => /Kit de VIP/.test(m)), 'o kit diário vale', msgs.join(' | ').slice(0, 120))
  msgs = await t.capturar(bot, () => bot.chat('/montaria'), 2000)
  t.verificar(!msgs.some(m => /benefício do VIP/.test(m)), 'mas os benefícios passivos valem (a montaria sai)', msgs.join(' | '))
  await t.console(`ride ${nome} dismount`)

  console.log('\n== Quem já tem VIP guarda pra depois ==')
  msgs = await t.capturar(bot, () => bot.chat(`/vip resgatar ${outro}`))
  t.verificar(msgs.some(m => /já tem VIP ativo/.test(m)), 'com VIP ativo, o código de evento fica pra depois', msgs[0])

  console.log('\n== VIP de verdade por cima, do mesmo tier ==')
  await t.console(`vip add ${nome} bronze`)
  r = await t.console(`vip check ${nome}`)
  const dias = Number((r.match(/expira em (\d+)d/) || [, 0])[1])
  t.verificar(!/de evento/.test(r) && dias >= 36, 'soma os 30 dias aos do evento e vira VIP normal', r)
  msgs = await t.capturar(bot, () => bot.chat('/vip kitmensal'), 1500)
  t.verificar(msgs.some(m => /Kit mensal de VIP/.test(m)), 'e o kit mensal passa a valer', msgs.join(' | ').slice(0, 120))

  if (outro) await t.console(`vip revogarcodigo ${outro}`)
  await t.console(`vip remove ${nome}`)
  await t.console(`eco set ${nome} 0`)
  await t.terminar(bot)
})
