// Warps (2026-11-06): a /warp arenas e a /warp mercado não existem mais; e a /warp tesouros, recursos,
// nether e end (que não são do Essentials: o TesourosPlugin e o RecursosPlugin interceptam o comando)
// esperam os 3 s do Essentials como as outras, pra quem não é VIP.
// Uso: node teste-warps.js
const t = require('./lib')

t.rodar(async () => {
  const bot = await t.criarBot(t.nomeNovo('Warp'))
  const nome = bot.username
  const noSpawn = () => t.console(`execute in minecraft:spawn run minecraft:tp ${nome} 0.5 65 43.5`)
  await noSpawn()
  await t.espera(1500)

  console.log('\n== Warps que saíram ==')
  for (const w of ['arenas', 'mercado']) {
    const msgs = await t.capturar(bot, () => bot.chat(`/warp ${w}`), 2000)
    t.verificar(bot.game.dimension.includes('spawn') || !/arenas/.test(bot.game.dimension), `a /warp ${w} não leva a lugar nenhum`, msgs.join(' | '))
    t.verificar(msgs.some(m => /não existe|does not exist|inexistente/i.test(m)), `a /warp ${w} não existe`, msgs.join(' | '))
  }

  console.log('\n== A espera de 3 s ==')
  // o RecursosPlugin tem 30 s entre um teleporte e outro (recursos.admin pula; a espera do Essentials, não)
  await t.console(`lp user ${nome} permission set recursos.admin true`)
  for (const w of ['tesouros', 'recursos', 'nether', 'end']) {
    await noSpawn()
    await t.espera(1500)
    const antes = bot.entity.position.clone()
    const inicio = Date.now()
    // a chegada é a hora do "Bem-vindo" (pela posição não dava: no End o dragão vivo mata o bot na chegada)
    let chegou = 0
    const ouvir = m => { if (!chegou && /Bem-vindo/.test(m)) chegou = Date.now() }
    bot.on('messagestr', ouvir)
    bot.chat(`/warp ${w}`)
    await t.espera(1500)
    const aos15 = bot.entity.position.distanceTo(antes)
    // o de recursos procura um lugar seguro antes da espera: até 15 s pra chegar
    while (Date.now() - inicio < 15000 && !chegou) await t.espera(250)
    bot.off('messagestr', ouvir)
    const levou = ((chegou || Date.now()) - inicio) / 1000
    t.verificar(aos15 < 1 && levou >= 3 && levou < 15, `a /warp ${w} espera ~3 s antes de levar`, `parado em 1,5 s; chegou em ${levou.toFixed(1)} s`)
  }
  await t.console(`lp user ${nome} permission unset recursos.admin`)

  await noSpawn()
  await t.terminar(bot)
})
