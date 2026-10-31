// Creepers dos spawners (a pilha) e da /warp mobs não explodem: nem miram no jogador. O de controle,
// sem marca, explode do lado do bot (prova que o teste pegaria a explosão).
// Uso: node teste-creeper.js
const t = require('./lib')

t.rodar(async () => {
  const bot = await t.criarBot(t.nomeNovo('Crp'))
  const nome = bot.username
  await t.espera(1500)
  await t.console(`gamemode survival ${nome}`)
  // um lugar plano e vazio do mundo de recursos, longe de tudo (a explosão do controle não estraga nada importante)
  await t.console(`execute in minecraft:recursos run minecraft:tp ${nome} 900.5 200 900.5`)
  await t.console(`execute in minecraft:recursos run fill 895 199 895 905 199 905 minecraft:obsidian`)
  await t.console(`execute in minecraft:recursos run minecraft:tp ${nome} 900.5 200 900.5`)
  await t.espera(2000)

  // a marca é a mesma que o plugin põe (PublicBukkitValues)
  const caso = async (rotulo, marca, deveExplodir) => {
    await t.console(`minecraft:effect give ${nome} minecraft:resistance 30 4 true`)
    await t.console(`minecraft:effect give ${nome} minecraft:instant_health 1 5 true`)
    await t.espera(500)
    const nbt = marca ? `{Tags:["teste_creeper"],BukkitValues:{${marca}}}` : `{Tags:["teste_creeper"]}`
    await t.console(`execute in minecraft:recursos run summon minecraft:creeper 902.5 200 900.5 ${nbt}`)
    await t.espera(8000)
    const vivo = (await t.console(`execute in minecraft:recursos if entity @e[type=creeper,tag=teste_creeper]`)).includes('passed')
    t.verificar(deveExplodir ? !vivo : vivo, rotulo, vivo ? 'o creeper continua lá' : 'o creeper explodiu')
    await t.console(`execute in minecraft:recursos run minecraft:kill @e[type=creeper,tag=teste_creeper]`)
  }
  await caso('controle: creeper comum explode do lado do jogador', null, true)
  await caso('creeper da pilha de spawner não explode', '"spawnersplugin:pilha":5', false)
  await caso('creeper da /warp mobs não explode', '"spawnersplugin:warp-mobs":"teste"', false)

  await t.console(`execute in minecraft:recursos run fill 895 199 895 905 199 905 minecraft:air`)
  await t.console(`minecraft:tp ${nome} 0 65 0`)
  await t.terminar(bot)
})
