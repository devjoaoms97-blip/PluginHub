// Fazendas, fase 2 (o visual): as 12 construções, cada uma com os blocos e os bichos dela, giradas pro
// lado de quem colocou (metade com a frente ao sul, metade ao norte); os bichos ficam parados; a Escritura,
// a Ração e o Adubo usam a textura do pacote; o fazendeiro segura o item do tipo.
// Uso: node teste-fazendas-visual.js
const t = require('./lib')
const { Vec3 } = require('vec3')

const CELULA = 37
const TIPOS = ['horta', 'galinheiro', 'pomar', 'pocilga', 'trigal', 'curral', 'ovelhas', 'apiario', 'aboboras', 'cogumelos', 'verrugas', 'chorus']
// blocos característicos de cada uma, em coordenadas locais (frente ao norte): [lx, ly, lz, bloco]
const ASSINATURA = {
  horta: [[-2, 0, 0, 'carrots[age=7]'], [-1, 0, 1, 'potatoes'], [0, -1, 0, 'water'], [2, 1, 2, 'carved_pumpkin']],
  galinheiro: [[-2, 1, 2, 'hay_block'], [-2, 0, 2, 'birch_planks'], [2, 0, 1, 'hay_block']],
  pomar: [[-1, 2, 0, 'oak_log'], [-1, 3, 0, 'oak_leaves[persistent=true]'], [1, 3, 1, 'flowering_azalea_leaves']],
  pocilga: [[-1, 0, 2, 'composter'], [2, 0, 2, 'hay_block']],
  trigal: [[-2, 2, 2, 'stripped_spruce_log'], [1, 0, 0, 'wheat[age=7]'], [-2, 4, 1, 'white_wool']],
  curral: [[2, 0, 2, 'water_cauldron[level=3]'], [-2, 1, 2, 'hay_block']],
  ovelhas: [[-2, 1, 2, 'pink_wool'], [-1, 0, 2, 'light_gray_wool']],
  apiario: [[2, 1, 2, 'beehive[honey_level=5]'], [-2, 0, 2, 'stripped_birch_log']],
  aboboras: [[-2, 0, -1, 'pumpkin'], [2, 0, 1, 'melon'], [-1, 0, -1, 'attached_pumpkin_stem']],
  cogumelos: [[0, 0, 1, 'mushroom_stem'], [0, 3, 1, 'red_mushroom_block'], [0, -1, 0, 'mycelium']],
  verrugas: [[1, 0, 0, 'nether_wart[age=3]'], [0, 0, 2, 'soul_campfire'], [3, 0, 0, 'nether_brick_fence']],
  chorus: [[0, 4, 2, 'chorus_flower'], [0, -1, 0, 'end_stone'], [3, 0, 0, 'end_stone_brick_wall'], [3, 1, 3, 'end_rod']],
}
const BICHOS = { galinheiro: ['chicken', 4], pocilga: ['pig', 2], curral: ['cow', 2], ovelhas: ['sheep', 3], apiario: ['bee', 3], pomar: ['item_display', 4] }
const MAO = { horta: 'iron_hoe', galinheiro: 'egg', trigal: 'wheat', ovelhas: 'shears', chorus: 'chorus_fruit' }

/** Local → mundo, com a frente f = [fx, fz] (como o Construcao.paraMundo). */
const paraMundo = (f, lx, lz) => [lx * -f[1] + lz * -f[0], lx * f[0] + lz * -f[1]]

t.rodar(async () => {
  const bot = await t.criarBot(t.nomeNovo('Vis'))
  const nome = bot.username
  await t.espera(1500)
  await t.console(`eco set ${nome} 1000000`)
  await t.console(`minecraft:clear ${nome}`)
  await t.console(`ranks admin set ${nome} rei`)
  const tp = async (x, z) => {
    await t.console(`execute in minecraft:terrenos run minecraft:tp ${nome} ${x} 65 ${z} 0 30`)
    await t.espera(1200)
  }
  const bloco = async (x, y, z, b) => (await t.console(`execute in minecraft:terrenos if block ${x} ${y} ${z} minecraft:${b}`)).includes('passed')
  const quantos = async (tipo, x, z) => Number(((await t.console(
    `execute in minecraft:terrenos positioned ${x + 0.5} 65 ${z + 0.5} if entity @e[type=${tipo},distance=..4.5]`)).match(/count: (\d+)/i) || [, 0])[1])

  let msgs = await t.capturar(bot, () => bot.chat('/terreno auto'), 3000)
  const [gx, gz] = ((msgs.join(' ').match(/terreno (-?\d+;-?\d+)/) || [])[1] || '0;0').split(';').map(Number)
  const mx = gx * CELULA, mz = gz * CELULA
  await tp(mx + 16.5, mz + 12.5)
  msgs = await t.capturar(bot, () => bot.chat('/terreno comprar'), 1500)
  t.verificar(msgs.some(m => m.includes('comprado')), 'comprou um terreno', msgs[0])

  console.log('\n== Texturas do pacote ==')
  await t.console(`fazendas dar ${nome} horta`)
  await t.console(`fazendas dar ${nome} racao 1`)
  await t.console(`fazendas dar ${nome} adubo 1`)
  // pelo dado de cada item (a posição no inventário muda se o jogador ganhou outra coisa ao entrar)
  const modelo = async (chave, valor) => (await t.console(`data get entity ${nome} Inventory[{components:{"minecraft:custom_data":{PublicBukkitValues:{"fazendasplugin:${chave}":"${valor}"}}}}].components."minecraft:item_model"`)).match(/"([^"]+)"$/)?.[1]
  const modelos = [await modelo('escritura', 'horta'), await modelo('insumo', 'racao'), await modelo('insumo', 'adubo')]
  t.verificar(modelos.join() === 'servidor:escritura,servidor:racao,servidor:adubo', 'a Escritura, a Ração e o Adubo usam a textura do pacote', modelos.join())
  await t.console(`minecraft:clear ${nome}`)

  // quatro vagas por terreno; cada rodada põe 4, confere e a staff tira
  const vagas = [[mx + 6, mz + 8], [mx + 16, mz + 8], [mx + 26, mz + 8], [mx + 11, mz + 22]]
  for (let rodada = 0; rodada < 3; rodada++) {
    const tipos = TIPOS.slice(rodada * 4, rodada * 4 + 4)
    console.log(`\n== ${tipos.join(', ')} ==`)
    for (let i = 0; i < tipos.length; i++) {
      const tipo = tipos[i]
      const [x, z] = vagas[i]
      // metade com a frente ao sul (o bot clica do sul), metade ao norte
      const sul = (rodada * 4 + i) % 2 === 0
      const f = sul ? [0, 1] : [0, -1]
      await t.console(`fazendas dar ${nome} ${tipo}`)
      await tp(x + 0.5, z + (sul ? 1.5 : -0.5))
      const papel = bot.inventory.items().find(it => it.name === 'paper')
      if (papel) await bot.equip(papel, 'hand')
      await t.espera(300)
      const alvo = bot.blockAt(new Vec3(x, 64, z))
      await bot.activateBlock(alvo, new Vec3(0, 1, 0)).catch(() => {})
      await t.espera(800)
      msgs = await t.capturar(bot, () => bot.activateBlock(alvo, new Vec3(0, 1, 0)).catch(() => {}), 1500)
      const ok = msgs.some(m => m.includes('construída'))
      t.verificar(ok, `${tipo}: construída (frente ao ${sul ? 'sul' : 'norte'})`, ok ? '' : msgs.join(' | '))
      if (!ok) continue
      const faltando = []
      for (const [lx, ly, lz, b] of ASSINATURA[tipo]) {
        const [dx, dz] = paraMundo(f, lx, lz)
        if (!await bloco(x + dx, 64 + 1 + ly, z + dz, b)) faltando.push(`${b} em ${lx},${ly},${lz}`)
      }
      t.verificar(faltando.length === 0, `${tipo}: os blocos dele estão no lugar, girados`, faltando.join('; '))
      if (tipo === 'aboboras') {
        // o caule ligado aponta pra abóbora depois de girar (local: oeste)
        const [dx, dz] = paraMundo(f, -1, -1)
        const lado = sul ? 'east' : 'west'
        t.verificar(await bloco(x + dx, 65, z + dz, `attached_pumpkin_stem[facing=${lado}]`), 'aboboras: o caule girou junto e aponta pra abóbora', lado)
      }
      const [ax, az] = paraMundo(f, 0, -3)
      t.verificar(await bloco(x + ax, 65, z + az, 'air'), `${tipo}: a abertura do fazendeiro fica na frente`)
      if (BICHOS[tipo]) {
        await t.espera(500)
        const [bicho, n] = BICHOS[tipo]
        const achou = await quantos(bicho, x, z)
        t.verificar(achou === n, `${tipo}: ${n} ${bicho} parado(s)`, `${achou}`)
      }
      if (MAO[tipo]) {
        const [nx, nz] = paraMundo(f, 0, -3)
        const r = await t.console(`execute in minecraft:terrenos positioned ${x + nx + 0.5} 65 ${z + nz + 0.5} run data get entity @e[type=mannequin,limit=1,sort=nearest,distance=..1] equipment.mainhand.id`)
        t.verificar(r.includes(`minecraft:${MAO[tipo]}`), `${tipo}: o fazendeiro segura ${MAO[tipo]}`, r.slice(-40))
      }
    }
    // os bichos não andam (sem IA)
    if (rodada === 0) {
      const pos = async () => (await t.console(`execute in minecraft:terrenos positioned ${vagas[1][0] + 0.5} 65 ${vagas[1][1] + 0.5} run data get entity @e[type=chicken,limit=1,sort=nearest,distance=..4.5] Pos`)).match(/\[(.*)\]/)?.[1]
      const antes = await pos()
      await t.espera(4000)
      t.verificar(antes && antes === await pos(), 'as galinhas ficam paradas', antes)
    }
    await t.console(`op ${nome}`)
    for (const [x, z] of vagas.slice(0, tipos.length)) {
      await tp(x + 0.5, z - 6.5)
      await bot.lookAt(new Vec3(x + 0.5, 64.5, z + 0.5))
      await t.espera(300)
      bot.chat('/fazendas remover')
      await t.espera(1000)
    }
    await t.console(`deop ${nome}`)
    await t.console(`minecraft:clear ${nome}`)
    const sobrou = []
    for (const [x, z] of vagas.slice(0, tipos.length)) {
      if (!await bloco(x, 64, z, 'grass_block') || !await bloco(x, 67, z + 1, 'air')) sobrou.push(`${x},${z}`)
    }
    // sobra algum bicho, enfeite ou fazendeiro dentro do terreno? (só a caixa dele: o vizinho pode ter a pilha de um spawner)
    const bichos = await t.console(`execute in minecraft:terrenos if entity @e[type=!player,type=!item,x=${mx},y=60,z=${mz},dx=31,dy=12,dz=31]`)
    t.verificar(sobrou.length === 0 && !bichos.includes('passed'), 'recolhidas: o chão volta e os bichos somem', `${sobrou.join(' ')} ${bichos}`)
  }
  await t.terminar(bot)
})
