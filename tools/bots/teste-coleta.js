// Coleta direta na mina, sem VIP: drop e XP vão pro inventário; com o inventário cheio, só a sobra cai.
// Uso: node teste-coleta.js [mina]   (padrão: pedreira; o bot precisa ter o rank dela)
const t = require('./lib')

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
  const xpAntes = bot.experience.points + bot.experience.level * 1000
  const quebrados = await t.minerar(bot, mina, 12)
  const inv = t.inventario(bot)
  const minerado = Object.entries(inv).filter(([nome]) => nome !== 'diamond_pickaxe').reduce((s, [, n]) => s + n, 0)
  t.verificar(minerado > 0, 'o minerado foi pro inventário', `${quebrados} blocos, ${JSON.stringify(inv)}`)
  t.verificar(t.itensNoChao(bot).length === 0, 'nada no chão (nem item, nem orbe)', JSON.stringify(t.itensNoChao(bot)))
  console.log(`  XP: ${xpAntes} → ${bot.experience.points + bot.experience.level * 1000} (só sobe se saiu minério com XP)`)

  console.log('\n== Inventário cheio ==')
  await t.console('minecraft:clear BotTeste')
  await t.console('minecraft:give BotTeste diamond_pickaxe 1')
  await t.console('minecraft:give BotTeste dirt 2240') // 35 pilhas: enche os outros espaços
  await t.irParaMina('BotTeste', mina) // da calçada a mão alcança poucos blocos: enche de novo
  const msgs = await t.capturar(bot, () => t.minerar(bot, mina, 3))
  t.verificar(t.itensNoChao(bot).length > 0, 'a sobra caiu no chão', JSON.stringify(t.itensNoChao(bot)))
  t.verificar(msgs.some(m => m.includes('Inventário cheio')), 'avisou "Inventário cheio" na barra')

  await t.console('minecraft:clear BotTeste')
  await t.console('minecraft:kill @e[type=item]')
  await t.terminar(bot)
})
