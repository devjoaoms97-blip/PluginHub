// Voto com recompensa (VotoPlugin, 2026-11-18): o lembrete no login, o voto pelo Votifier (/testvote) dá a
// Chave de Voto e o bônus do dia da sequência, o segundo voto do dia só a chave, o 7º dia seguido dá chave
// extra, a Festa do Voto dá chave pra todo mundo online, e o ranking do mês paga as Chaves do Boss.
// Precisa do Votifier (azuvotifier) e do CaixasPlugin. Uso: node teste-voto.js
const t = require('./lib')

const ganchos = bot => bot.inventory.items().filter(i => i.name === 'tripwire_hook').reduce((n, i) => n + i.count, 0)

t.rodar(async () => {
  console.log('\n== Lembrete no login ==')
  let bot
  const lembrete = []
  const nome = t.nomeNovo('Vto')
  bot = await t.criarBot(nome)
  bot.on('messagestr', m => lembrete.push(m))
  await t.espera(10000)
  t.verificar(lembrete.some(m => /ainda não votou hoje/.test(m) && /VOTAR/.test(m)), 'quem não votou hoje recebe o lembrete com o botão', lembrete.find(m => /votou/.test(m)))
  await t.console(`minecraft:clear ${nome}`)
  await t.console('votar admin festa 0')

  console.log('\n== O voto ==')
  let msgs = await t.capturar(bot, () => t.console(`testvote username=${nome} serviceName=SiteTeste`), 2500)
  t.verificar(ganchos(bot) === 1, 'o voto dá uma Chave de Voto', `${ganchos(bot)} chave(s)`)
  t.verificar(msgs.some(m => /Obrigado por votar \(SiteTeste\)/.test(m) && /1\/7/.test(m)), 'agradece e mostra o dia 1 da sequência', msgs.find(m => /Obrigado/.test(m)))
  t.verificar(msgs.some(m => /\+R\$ .* pelo dia 1/.test(m)), 'o primeiro voto do dia paga o bônus em coins', msgs.find(m => /pelo dia/.test(m)))
  t.verificar(msgs.some(m => new RegExp(`${nome} votou no servidor`).test(m)), 'o voto é anunciado no chat', msgs.find(m => /votou no servidor/.test(m)))

  msgs = await t.capturar(bot, () => t.console(`testvote username=${nome} serviceName=OutroSite`), 2500)
  t.verificar(ganchos(bot) === 2 && !msgs.some(m => /pelo dia/.test(m)), 'o segundo voto do dia dá a chave, sem bônus de novo', `${ganchos(bot)} | ${msgs.join(' | ').slice(0, 160)}`)

  msgs = await t.capturar(bot, () => bot.chat('/votar'), 1500)
  t.verificar(msgs.some(m => /Sequência: 1\/7 dias \(votou hoje\)/.test(m)) && msgs.some(m => /votos este mês: 2/.test(m)), 'o /votar mostra a sequência e os votos do mês', msgs.join(' | ').slice(0, 240))

  console.log('\n== Sete dias seguidos ==')
  await t.console(`votar admin sequencia ${nome} 6`)
  await t.console(`minecraft:clear ${nome}`)
  msgs = await t.capturar(bot, () => t.console(`testvote username=${nome} serviceName=SiteTeste`), 2500)
  t.verificar(msgs.some(m => /pelo dia 7/.test(m)) && ganchos(bot) === 2, 'no 7º dia seguido, o bônus do dia 7 e uma chave extra', `${ganchos(bot)} | ${msgs.find(m => /pelo dia/.test(m))}`)

  console.log('\n== Festa do Voto ==')
  await t.console('votar admin festa 49')
  await t.console(`minecraft:clear ${nome}`)
  msgs = await t.capturar(bot, () => t.console(`testvote username=${nome} serviceName=SiteTeste`), 2500)
  t.verificar(msgs.some(m => /FESTA DO VOTO/.test(m)), 'o 50º voto faz a Festa do Voto', msgs.find(m => /FESTA/.test(m)))
  t.verificar(ganchos(bot) === 2, 'e todo mundo online ganha chave (a do voto e a da festa)', `${ganchos(bot)}`)

  console.log('\n== Ranking do mês ==')
  await t.console(`minecraft:clear ${nome}`)
  msgs = await t.capturar(bot, () => t.console('votar admin fimdomes'), 2500)
  t.verificar(msgs.some(m => /Ranking de votos/.test(m) && new RegExp(nome).test(m)), 'o ranking do mês é anunciado', msgs.find(m => /Ranking/.test(m)))
  t.verificar(ganchos(bot) >= 1, 'quem votou ganha Chaves do Boss pelo ranking', `${ganchos(bot)} chave(s)`)
  msgs = await t.capturar(bot, () => bot.chat('/votar'), 1500)
  t.verificar(msgs.some(m => /votos este mês: 0/.test(m)), 'e o mês recomeça do zero', msgs.find(m => /este mês/.test(m)))

  await t.console(`minecraft:clear ${nome}`)
  await t.console('votar admin festa 0')
  await t.terminar(bot)
})
