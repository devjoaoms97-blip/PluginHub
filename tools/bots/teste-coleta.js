// Venda automática na mina, pra todos (sem VIP): o que se quebra vira dinheiro na hora, com o
// "+R$" na barra, e não sobra nada no inventário nem no chão.
// Uso: node teste-coleta.js [mina]   (padrão: pedreira; o bot precisa ter o rank dela)
const t = require('./lib')

const dinheiro = r => Number((r.match(/\$([\d,.]+)/) || [, 'NaN'])[1].replace(/,/g, ''))

t.rodar(async () => {
  const mina = t.mina(process.argv[2] || 'pedreira')
  const bot = await t.criarBot('BotTeste')
  await t.console('vip remove BotTeste')
  await t.console('gamemode survival BotTeste')
  await t.console('minecraft:clear BotTeste')
  // os comandos com minecraft: evitam as versões da Essentials (o give dela dá 64 de cada)
  await t.console('minecraft:give BotTeste diamond_pickaxe 1')
  await t.irParaMina('BotTeste', mina)

  console.log('\n== Minerar sem VIP ==')
  const antes = dinheiro(await t.console('balance BotTeste'))
  let quebrados = 0
  const msgs = await t.capturar(bot, async () => { quebrados = await t.minerar(bot, mina, 12) })
  const depois = dinheiro(await t.console('balance BotTeste'))
  const inv = t.inventario(bot)
  t.verificar(depois > antes, 'o que foi quebrado virou dinheiro na hora', `${quebrados} blocos, ${antes} → ${depois}`)
  t.verificar(msgs.some(m => m.includes('+R$')), 'com o "+R$" na barra', msgs.find(m => m.includes('+R$')))
  t.verificar(Object.keys(inv).length === 1, 'nada do minerado no inventário', JSON.stringify(inv))
  t.verificar(t.itensNoChao(bot).length === 0, 'nada no chão (nem item, nem orbe)', JSON.stringify(t.itensNoChao(bot)))

  await t.console('minecraft:clear BotTeste')
  await t.terminar(bot)
})
