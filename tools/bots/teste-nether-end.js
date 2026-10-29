// Nether e End de recursos (RecursosPlugin): /nether leva a um chão seguro abaixo do teto, /end leva
// ao quadrado verde do centro (sem PvP em cima dele, com PvP fora), o centro do End não deixa quebrar,
// as gaiolas dos cristais saem, o ovo do dragão não teleporta, o dragão renasce pela staff, /mapa dá o
// mapa do mundo, o reset do Nether regenera o mundo e resgata quem saiu lá dentro. Precisa dos mundos
// recursos_nether e recursos_end (configurar-mundos.ps1). Demora uns 3 minutos.
// Uso: node teste-nether-end.js
const t = require('./lib')
const { Vec3 } = require('vec3')

const N = 'execute in minecraft:recursos_nether run'
const E = 'execute in minecraft:recursos_end run'
const naDimensao = async (dim, nome) => (await t.console(`execute in minecraft:${dim} if entity @a[name=${nome},distance=0..]`)).includes('passed')

t.rodar(async () => {
  const a = await t.criarBot(t.nomeNovo('Nea'))
  let b = await t.criarBot(t.nomeNovo('Neb'))
  const nomeB = b.username
  for (const bot of [a, b]) {
    await t.console(`gamemode survival ${bot.username}`)
    await t.console(`minecraft:clear ${bot.username}`)
    await t.console(`lp user ${bot.username} permission set recursos.admin true`) // sem a espera entre teleportes
    await t.console(`effect give ${bot.username} minecraft:resistance infinite 4 true`) // nada mata os bots no meio do teste
  }

  console.log('\n== /nether ==')
  let msgs = await t.capturar(a, () => a.chat('/nether'), 8000)
  t.verificar(msgs.some(m => m.includes('Bem-vindo ao Nether')), 'foi pro Nether', msgs.find(m => /Bem-vindo|Não achei/.test(m)))
  t.verificar(await naDimensao('recursos_nether', a.username), 'está no recursos_nether')
  const p = a.entity.position
  t.verificar(p.y > 31 && p.y < 127, 'abaixo do teto de bedrock', `${p}`)
  const abaixo = a.blockAt(p.offset(0, -1, 0))
  t.verificar(abaixo && abaixo.boundingBox === 'block' && !/lava|bedrock|magma/.test(abaixo.name), 'em chão firme', abaixo && abaixo.name)
  t.verificar((await t.console(`${N} worldborder get`)).includes('3000'), 'borda de 3.000 blocos no Nether')
  await t.console(`${N} setblock ${Math.floor(p.x) + 1} ${Math.floor(p.y)} ${Math.floor(p.z)} minecraft:diamond_block`)
  const marca = [Math.floor(p.x) + 1, Math.floor(p.y), Math.floor(p.z)]

  console.log('\n== /mapa ==')
  await t.capturar(a, () => a.chat('/mapa'), 1500)
  t.verificar(a.inventory.items().some(i => i.name === 'filled_map'), '/mapa (no Nether) deu o mapa')
  msgs = await t.capturar(a, () => a.chat('/mapa nether'), 1000)
  t.verificar(msgs.some(m => m.includes('já tem')), 'o mesmo mapa não sai duas vezes')
  await t.capturar(a, () => a.chat('/mapa end'), 1000)
  t.verificar(a.inventory.items().filter(i => i.name === 'filled_map').length === 2, '/mapa end deu o mapa do End')

  console.log('\n== /end ==')
  // sem dragão durante o centro e o PvP (a staff renasce ele no fim)
  await t.console(`${E} minecraft:kill @e[type=ender_dragon]`)
  msgs =await t.capturar(a, () => a.chat('/end'), 6000)
  t.verificar(msgs.some(m => m.includes('Bem-vindo ao End')), 'foi pro End')
  t.verificar(await naDimensao('recursos_end', a.username), 'está no recursos_end')
  const pa = a.entity.position
  t.verificar(a.blockAt(pa.offset(0, -1, 0))?.name === 'lime_concrete', 'chegou no quadrado verde', `${pa} sobre ${a.blockAt(pa.offset(0, -1, 0))?.name}`)
  t.verificar(Math.abs(pa.x - 0.5) < 1 && Math.abs(pa.z - 25.5) < 1, 'no centro (0, 25)', `${pa}`)
  t.verificar((await t.console(`${E} worldborder get`)).includes('5000'), 'borda de 5.000 blocos no End')

  console.log('\n== Centro protegido ==')
  const lado = a.blockAt(pa.offset(3, -1, 0))
  const nomeLado = lado?.name
  await a.dig(lado, true).catch(() => {})
  await t.espera(800)
  t.verificar(a.blockAt(pa.offset(3, -1, 0))?.name === nomeLado && nomeLado !== 'air', 'não dá pra quebrar no centro do End', nomeLado)

  console.log('\n== PvP: no quadrado verde não, fora sim ==')
  await t.capturar(b, () => b.chat('/end'), 6000)
  await t.console(`effect clear ${nomeB}`)
  await t.console(`minecraft:effect give ${nomeB} minecraft:instant_health 1 5 true`)
  await t.espera(1500)
  const alvo = () => Object.values(a.entities).find(e => e.username === nomeB)
  let vida = b.health
  for (let i = 0; i < 3; i++) { if (alvo()) { await a.lookAt(alvo().position.offset(0, 1.6, 0)); a.attack(alvo()) } await t.espera(600) }
  t.verificar(b.health >= vida, 'no quadrado verde, ninguém apanha', `${vida} → ${b.health}`)
  await t.console(`${E} minecraft:tp ${a.username} 6.5 ${Math.floor(pa.y) + 2} 25.5`)
  await t.console(`${E} minecraft:tp ${nomeB} 7.5 ${Math.floor(pa.y) + 2} 25.5`)
  await t.espera(2500)
  vida = b.health
  for (let i = 0; i < 3; i++) { if (alvo()) { await a.lookAt(alvo().position.offset(0, 1.6, 0)); a.attack(alvo()) } await t.espera(700) }
  t.verificar(b.health < vida, 'fora do quadrado, o PvP vale', `${vida} → ${b.health}`)

  console.log('\n== Gaiolas e dragão ==')
  await t.console(`${E} minecraft:tp ${a.username} 0.5 ${Math.floor(pa.y)} 25.5`)
  for (let s = 0; s < 35; s++) await t.espera(1000) // a limpeza das gaiolas roda a cada 30 s com gente no End
  const grades = a.findBlocks({ matching: a.registry.blocksByName.iron_bars.id, maxDistance: 80, count: 50 })
  t.verificar(grades.length === 0, 'as gaiolas dos cristais saíram', `${grades.length} grades`)
  // mata o dragão que estiver vivo (num End novo, ele nasce quando alguém chega) e espera a animação (10 s)
  await t.console(`${E} minecraft:kill @e[type=ender_dragon]`)
  await t.espera(12000)
  let r = await t.console('recursos admin dragao')
  t.verificar(r.includes('renascendo'), 'a staff renasce o dragão', r)
  let vivo = false
  for (let s = 0; s < 60 && !vivo; s++) {
    await t.espera(1000)
    vivo = (await t.console(`${E} execute if entity @e[type=ender_dragon]`)).includes('passed')
  }
  t.verificar(vivo, 'o dragão renasceu pelo ritual dos cristais')
  r = await t.console('recursos admin dragao')
  t.verificar(r.includes('já está vivo'), 'com o dragão vivo, não renasce outro', r)

  console.log('\n== Ovo do dragão (só enfeite) ==')
  const yOvo = Math.floor(pa.y)
  await t.console(`${E} setblock 3 ${yOvo} 25 minecraft:dragon_egg`)
  await t.espera(1000)
  const ovo = a.blockAt(new Vec3(3, yOvo, 25))
  if (ovo?.name === 'dragon_egg') {
    await a.activateBlock(ovo).catch(() => {})
    await t.espera(500)
    await a.dig(ovo, true).catch(() => {})
    await t.espera(1500)
  }
  t.verificar(ovo?.name === 'dragon_egg' && (await t.console(`${E} execute if block 3 ${yOvo} 25 minecraft:dragon_egg`)).includes('passed'),
    'o ovo não teleporta nem quebra', ovo?.name)
  await t.console(`${E} setblock 3 ${yOvo} 25 minecraft:air`)

  console.log('\n== Terreno no mapa ==')
  const pixels = async mundo => Number(((await t.console('recursos admin mapa')).match(new RegExp(`${mundo}: (\\d+) de`)) || [, -1])[1])
  const noEnd = await pixels('recursos_end')
  t.verificar(noEnd > 0, 'o mapa do End já tem terreno, de onde os bots andaram', `${noEnd} pixels`)

  console.log('\n== Reset do Nether ==')
  await t.console(`${N} minecraft:tp ${nomeB} ${marca[0] + 0.5} ${marca[1] + 1} ${marca[2] + 1.5}`)
  await t.espera(3000)
  const antesReset = await pixels('recursos_nether')
  t.verificar(antesReset > 0, 'o mapa do Nether tem terreno antes do reset', `${antesReset} pixels`)
  b.quit()
  await t.espera(1000)
  msgs = await t.capturar(a, () => t.console('recursos admin resetar nether agora'), 8000)
  t.verificar(msgs.some(m => m.includes('O Nether foi resetado')), 'o Nether foi resetado, com aviso no chat', msgs.find(m => m.includes('resetado')))
  const depoisReset = await pixels('recursos_nether')
  t.verificar(depoisReset < antesReset, 'o reset apagou o terreno do mapa do Nether (o novo começa quase em branco)', `${antesReset} → ${depoisReset}`)
  await t.console(`${N} forceload add ${marca[0]} ${marca[2]}`)
  await t.espera(2000)
  t.verificar(!(await t.console(`${N} execute if block ${marca[0]} ${marca[1]} ${marca[2]} minecraft:diamond_block`)).includes('passed'),
    'o bloco do Nether antigo sumiu')
  await t.console(`${N} forceload remove ${marca[0]} ${marca[2]}`)
  b = await t.criarBot(nomeB)
  await t.espera(3000)
  t.verificar(b.mensagens.some(m => m.includes('Nether foi resetado enquanto você estava fora')), 'quem saiu no Nether antigo é resgatado')
  t.verificar(await naDimensao('recursos_nether', nomeB) && b.entity.position.y < 127, 'num lugar seguro do Nether novo', `${b.entity.position}`)

  for (const bot of [a, b]) await t.console(`lp user ${bot.username} permission unset recursos.admin`)
  await t.terminar(a, b)
})
