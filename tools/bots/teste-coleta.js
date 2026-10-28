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

  // toque suave não vale na mina: o bloco não sai inteiro (o minério iria pro inventário), e vende igual
  console.log('\n== Minerar com toque suave ==')
  await t.console('minecraft:clear BotTeste')
  await t.console('minecraft:give BotTeste diamond_pickaxe[enchantments={"minecraft:silk_touch":1}] 1')
  // o Mineflayer não lê o encantamento no formato novo ao calcular o tempo de quebra: tempo fixo, com folga
  const digTime = bot.digTime
  bot.digTime = b => { try { return digTime(b) } catch (e) { return 1500 } }
  await t.irParaMina('BotTeste', mina) // de novo: em volta do lugar de antes já está minerado
  await t.espera(500)
  let quebradosToque = 0
  const msgsToque = await t.capturar(bot, async () => { quebradosToque = await t.minerar(bot, mina, 12) })
  const invToque = t.inventario(bot)
  // pela barra de venda (o saldo pode subir por recompensa de missão)
  t.verificar(quebradosToque > 0 && msgsToque.some(m => m.includes('+R$')), 'com toque suave, o minerado também é vendido',
    `${quebradosToque} blocos, ${msgsToque.find(m => m.includes('+R$'))}`)
  t.verificar(Object.keys(invToque).length === 1, 'e nenhum bloco inteiro vai pro inventário', JSON.stringify(invToque))

  await t.console('minecraft:clear BotTeste')
  await t.terminar(bot)
})
