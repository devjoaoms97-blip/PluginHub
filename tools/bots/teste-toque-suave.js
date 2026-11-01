// Toque Suave em spawner natural (SpawnersPlugin): no mundo de recursos, o spawner de zumbi quebrado com
// picareta com Toque Suave vira o "Spawner de Zumbi" da loja; sem Toque Suave não dá nada; o de traça
// (que a loja não tem) não sai nem com Toque Suave. E os tipos novos estão na loja.
// Uso: node teste-toque-suave.js
const t = require('./lib')
const { Vec3 } = require('vec3')

const MUNDO = 'recursos'

t.rodar(async () => {
  const bot = await t.criarBot(t.nomeNovo('Toque'))
  const nome = bot.username
  await t.console(`deop ${nome}`)
  await t.console(`gamemode survival ${nome}`)
  await t.console(`effect give ${nome} minecraft:resistance 600 4 true`)
  await t.console(`execute in minecraft:${MUNDO} run minecraft:time set day`)
  await t.console(`execute in minecraft:${MUNDO} run minecraft:tp ${nome} 0.5 200 0.5`)
  await t.espera(1500)
  await t.console(`execute in minecraft:${MUNDO} run minecraft:fill -2 199 -2 6 205 2 minecraft:air`)
  await t.console(`execute in minecraft:${MUNDO} run minecraft:fill -2 198 -2 6 198 2 minecraft:stone`)
  await t.console(`execute in minecraft:${MUNDO} run minecraft:tp ${nome} 0.5 199 0.5 -90 0`)
  await t.espera(1000)

  /** Põe um spawner do mob em (x, 199, 0), quebra com a picareta dada e conta os spawners que caíram. */
  async function quebrar(x, mob, picareta) {
    // mob de outra rodada (é noite no mundo de recursos) não mata o bot no meio do teste
    await t.console(`execute in minecraft:${MUNDO} positioned 0 199 0 run minecraft:kill @e[type=!player,type=!item,distance=..48]`)
    await t.console(`execute in minecraft:${MUNDO} run minecraft:tp ${nome} 0.5 199 0.5 -90 0`)
    await t.console(`minecraft:clear ${nome}`)
    await t.console(`minecraft:give ${nome} ${picareta}`)
    await t.console(`execute in minecraft:${MUNDO} run minecraft:setblock ${x} 199 0 minecraft:spawner{SpawnData:{entity:{id:"minecraft:${mob}"}},RequiredPlayerRange:0s,SpawnCount:0s}`)
    await t.espera(800)
    bot.setQuickBarSlot(0)
    // o bot.dig do Mineflayer quebra com ferramenta encantada nessa versão ("enchantments is not
    // iterable"): começa e termina a quebra pelos pacotes (spawner com picareta de diamante: ~1 s)
    const msgs = await t.capturar(bot, async () => {
      const location = { x, y: 199, z: 0 }
      await bot.lookAt(new Vec3(x + 0.5, 199.5, 0.5), true)
      bot._client.write('block_dig', { status: 0, location, face: 4, sequence: 1 })
      await t.espera(1600)
      bot._client.write('block_dig', { status: 2, location, face: 4, sequence: 2 })
    }, 2500)
    const caiu = await t.console(`execute in minecraft:${MUNDO} positioned ${x} 199 0 if entity @e[type=item,distance=..4,nbt={Item:{id:"minecraft:spawner"}}]`)
    const inv = await t.console(`minecraft:clear ${nome} minecraft:spawner 0`)
    await t.console(`execute in minecraft:${MUNDO} positioned ${x} 199 0 run minecraft:kill @e[type=item,distance=..5]`)
    return { msgs, ganhou: caiu.includes('passed') || /Found [1-9]/.test(inv) }
  }

  console.log('\n== Spawner natural ==')
  let r = await quebrar(2, 'zombie', 'minecraft:diamond_pickaxe[minecraft:enchantments={"minecraft:silk_touch":1}]')
  t.verificar(r.ganhou, 'zumbi com Toque Suave: vira o Spawner de Zumbi da loja', r.msgs.join(' | '))
  r = await quebrar(3, 'zombie', 'minecraft:diamond_pickaxe')
  t.verificar(!r.ganhou, 'sem Toque Suave: não dá nada (como no jogo)', r.msgs.join(' | '))
  r = await quebrar(4, 'silverfish', 'minecraft:diamond_pickaxe[minecraft:enchantments={"minecraft:silk_touch":1}]')
  t.verificar(!r.ganhou, 'traça (fora da loja): não sai nem com Toque Suave', r.msgs.join(' | '))

  console.log('\n== Tipos novos na loja ==')
  for (const tipo of ['aranha_caverna', 'zumbi_deserto', 'afogado', 'errante', 'pantanoso', 'piglin_zumbi', 'esqueleto_wither', 'breeze']) {
    const res = await t.console(`spawners dar ${nome} ${tipo} 1`)
    t.verificar(!/inválid|não existe|Tipos:/i.test(res), `tipo ${tipo} existe`, res)
  }
  await t.console(`minecraft:clear ${nome}`)

  await t.terminar(bot)
})
