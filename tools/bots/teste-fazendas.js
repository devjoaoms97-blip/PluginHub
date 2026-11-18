// Fazendas (FazendasPlugin, fase 1): loja e trava de rank, prévia e construção com a Escritura, onde
// não pode colocar, proteção da construção (quebrar, colocar, TNT), menu do fazendeiro (abastecer,
// produzir, coletar, melhorar, atalho do insumo na mão, quem não é dono), recolher (o chão volta e a
// Escritura guarda o nível) e o limite de 8 por terreno.
// Uso: node teste-fazendas.js
const t = require('./lib')
const { Vec3 } = require('vec3')

const dinheiro = r => Number((r.match(/\$([\d,.]+)/) || [, 'NaN'])[1].replace(/,/g, ''))
const saldo = async nome => dinheiro(await t.console(`balance ${nome}`))
const CELULA = 37
const contar = async (nome, item) => Number(((await t.console(`minecraft:clear ${nome} ${item} 0`)).match(/Found (\d+)/) || [, 0])[1])
const bloco = async (x, y, z, tipo) => (await t.console(`execute in minecraft:terrenos if block ${x} ${y} ${z} minecraft:${tipo}`)).includes('passed')

/** Espera a próxima janela abrir (com os itens) depois de rodar fn. */
const janela = (bot, fn) => new Promise(ok => {
  const fim = setTimeout(() => ok(null), 3000)
  bot.once('windowOpen', j => { clearTimeout(fim); setTimeout(() => ok(j), 800) })
  fn()
})

t.rodar(async () => {
  const bot = await t.criarBot(t.nomeNovo('Faz'))
  const nome = bot.username
  await t.espera(1500)
  await t.console(`eco set ${nome} 5000000`)
  await t.console(`minecraft:clear ${nome}`)
  await t.console(`ranks admin set ${nome} fazendeiro`)
  const tp = async (x, y, z, yaw = 0) => {
    await t.console(`execute in minecraft:terrenos run minecraft:tp ${nome} ${x} ${y} ${z} ${yaw} 30`)
    await t.espera(1200)
  }
  /** Clique direito com o que estiver na mão no topo do bloco (x, y, z). */
  const clicar = (x, y, z) => t.capturar(bot, () => bot.activateBlock(bot.blockAt(new Vec3(x, y, z)), new Vec3(0, 1, 0)).catch(() => {}), 1200)
  const segurarPapel = async () => {
    const i = bot.inventory.items().find(i => i.name === 'paper')
    if (i) await bot.equip(i, 'hand')
    await t.espera(300)
  }
  const fazendeiro = (x, z) => Object.values(bot.entities).find(e => e.name === 'mannequin' && e.position.distanceTo(new Vec3(x, 65, z)) < 1.5)

  console.log('\n== Terreno ==')
  let msgs = await t.capturar(bot, () => bot.chat('/terreno auto'), 3000)
  const [gx, gz] = ((msgs.join(' ').match(/terreno (-?\d+;-?\d+)/) || [])[1] || '0;0').split(';').map(Number)
  const mx = gx * CELULA, mz = gz * CELULA
  await tp(mx + 16.5, 65, mz + 12.5)
  msgs = await t.capturar(bot, () => bot.chat('/terreno comprar'), 1500)
  t.verificar(msgs.some(m => m.includes('comprado')), 'comprou um terreno', msgs[0])

  console.log('\n== Loja ==')
  let j = await janela(bot, () => bot.chat('/fazendas loja'))
  t.verificar(!!j && j.slots[10]?.name === 'carrot' && j.slots[11]?.name === 'egg', 'a loja abre com as fazendas (Horta, Galinheiro...)',
    j ? j.slots.slice(10, 16).map(s => s?.name).join(',') : 'não abriu')
  if (!j) return t.terminar(bot)
  const antes = await saldo(nome)
  await bot.clickWindow(11, 0, 0) // Galinheiro
  await t.espera(1200)
  t.verificar(antes - await saldo(nome) === 12000 && await contar(nome, 'paper') === 1, 'comprou a Escritura do Galinheiro (R$ 12 mil)')
  msgs = await t.capturar(bot, () => bot.clickWindow(15, 0, 0), 1200) // Curral, do Escudeiro
  t.verificar(msgs.some(m => m.includes('libera no rank')) && await contar(nome, 'paper') === 1, 'o Curral não vende pra quem é Fazendeiro', msgs.find(m => m.includes('rank')))
  bot.closeWindow(bot.currentWindow)

  console.log('\n== Onde não pode ==')
  await segurarPapel()
  await tp(mx + 1.5, 65, mz + 4.5)
  msgs = await clicar(mx + 1, 64, mz + 2)
  t.verificar(msgs.some(m => m.includes('sai do terreno')), 'colada na borda, a área sai do terreno', msgs.find(m => m.includes('terreno')))
  await t.console(`execute in minecraft:terrenos run minecraft:setblock ${mx + 16} 65 ${mz + 18} minecraft:stone`)
  await tp(mx + 16.5, 65, mz + 13.5, 0)
  msgs = await clicar(mx + 16, 64, mz + 16)
  t.verificar(msgs.some(m => m.includes('precisa estar livre')), 'com um bloco em cima do chão, não constrói', msgs.find(m => m.includes('livre')))
  await t.console(`execute in minecraft:terrenos run minecraft:setblock ${mx + 16} 65 ${mz + 18} minecraft:air`)

  console.log('\n== Prévia e construção ==')
  const [cx, cz] = [mx + 16, mz + 16]
  msgs = await clicar(cx, 64, cz)
  t.verificar(msgs.some(m => m.includes('Área livre')), '1º clique: mostra a área e pede o segundo', msgs[0])
  msgs = await clicar(cx, 64, cz)
  t.verificar(msgs.some(m => m.includes('construída')), '2º clique: constrói', msgs.find(m => m.includes('constru')))
  t.verificar(await bloco(cx - 3, 65, cz - 3, 'birch_log') && await bloco(cx + 3, 65, cz, 'birch_fence')
    && await bloco(cx, 64, cz + 2, 'birch_planks') && await bloco(cx - 3, 66, cz + 3, 'lantern'), 'a construção 7x7 do Galinheiro está lá (postes, cerca, casinha, lanternas)')
  t.verificar(await bloco(cx, 65, cz - 3, 'air'), 'a abertura da cerca fica do lado de quem colocou (norte)')
  t.verificar(await contar(nome, 'paper') === 0, 'a Escritura saiu da mão')
  const p = bot.entity.position
  t.verificar(p.z < cz - 3.5, 'quem estava dentro foi pra fora da cerca', `${p.x.toFixed(1)} ${p.z.toFixed(1)}`)
  await t.espera(500)
  const npc = fazendeiro(cx + 0.5, cz - 2.5)
  t.verificar(!!npc, 'o fazendeiro está na abertura da cerca')
  const holo = await t.console(`execute in minecraft:terrenos positioned ${cx + 0.5} 68 ${cz - 2.5} if entity @e[type=text_display,distance=..1.5]`)
  t.verificar(holo.includes('passed') || /\d/.test(holo), 'o holograma está em cima dele', holo)

  console.log('\n== Proteção ==')
  await tp(cx + 0.5, 65, cz - 5.5, 0)
  await bot.dig(bot.blockAt(new Vec3(cx + 1, 65, cz - 3)), true).catch(() => {})
  await t.espera(800)
  t.verificar(await bloco(cx + 1, 65, cz - 3, 'birch_fence'), 'o dono não quebra a cerca')
  await t.console(`give ${nome} minecraft:dirt 4`)
  await t.espera(500)
  await bot.equip(bot.inventory.items().find(i => i.name === 'dirt'), 'hand')
  await bot.placeBlock(bot.blockAt(new Vec3(cx, 64, cz - 2)), new Vec3(0, 1, 0)).catch(() => {})
  await t.espera(800)
  t.verificar(await bloco(cx, 65, cz - 2, 'air'), 'nem coloca bloco dentro da área')
  await tp(cx + 0.5, 65, cz - 14.5, 0)
  await t.console(`execute in minecraft:terrenos run minecraft:summon tnt ${cx + 1} 65 ${cz + 1} {fuse:1}`)
  await t.espera(2000)
  t.verificar(await bloco(cx, 64, cz + 1, 'birch_planks') && await bloco(cx + 3, 65, cz + 1, 'birch_fence') && await bloco(cx + 3, 65, cz + 3, 'birch_log'),
    'TNT dentro da área não estraga nada')

  console.log('\n== Menu do fazendeiro ==')
  await t.console(`fazendas dar ${nome} racao 16`)
  await t.console(`minecraft:clear ${nome} minecraft:dirt`)
  await tp(cx + 0.5, 65, cz - 4.5, 0)
  const npc2 = fazendeiro(cx + 0.5, cz - 2.5)
  j = npc2 && await janela(bot, () => bot.activateEntity(npc2))
  t.verificar(!!j && j.slots[4]?.name === 'egg', 'clicar no fazendeiro abre o menu da fazenda')
  if (!j) return t.terminar(bot)
  await bot.clickWindow(12, 0, 0) // abastecer
  await t.espera(1200)
  t.verificar(await contar(nome, 'paper') === 0, 'abasteceu com as 16 Rações')
  bot.closeWindow(bot.currentWindow)
  await t.console('fazendas acelerar 30') // 30 safras do Galinheiro: 30 × R$ 15, gastando 6 Rações
  const antesColeta = await saldo(nome)
  j = await janela(bot, () => bot.activateEntity(npc2))
  await bot.clickWindow(10, 0, 0) // coletar
  await t.espera(1200)
  const recebeu = await saldo(nome) - antesColeta
  t.verificar(recebeu > 400 && recebeu < 600, 'em 30 minutos o cofre juntou ~R$ 450 e a coleta caiu no saldo', `+${recebeu.toFixed(2)}`)
  const antesMelhoria = await saldo(nome)
  await bot.clickWindow(14, 0, 0) // produtividade
  await t.espera(1200)
  t.verificar(antesMelhoria - await saldo(nome) === 3600, 'melhorar a produtividade custa 30% do preço (R$ 3.600)')
  bot.closeWindow(bot.currentWindow)
  await t.console(`fazendas dar ${nome} racao 5`)
  await segurarPapel()
  msgs = await t.capturar(bot, () => bot.activateEntity(npc2), 1200)
  t.verificar(msgs.some(m => m.includes('Abasteceu')) && await contar(nome, 'paper') === 0, 'clicar com a Ração na mão abastece direto', msgs[0])

  console.log('\n== Quem não é o dono ==')
  const outro = await t.criarBot(t.nomeNovo('Viz'))
  await t.espera(1500)
  await t.console(`execute in minecraft:terrenos run minecraft:tp ${outro.username} ${cx + 0.5} 65 ${cz - 4.5} 0 0`)
  await t.espera(1500)
  const npc3 = Object.values(outro.entities).find(e => e.name === 'mannequin' && e.position.distanceTo(new Vec3(cx + 0.5, 65, cz - 2.5)) < 1.5)
  let abriu = null
  msgs = await t.capturar(outro, async () => { abriu = npc3 && await janela(outro, () => outro.activateEntity(npc3)) }, 500)
  t.verificar(!!npc3 && !abriu && msgs.some(m => m.includes('é de')), 'quem não é o dono não abre o menu', msgs[0])
  outro.quit()

  console.log('\n== Recolher ==')
  j = await janela(bot, () => bot.activateEntity(npc2))
  await bot.clickWindow(22, 0, 0)
  await t.espera(1000)
  t.verificar(await bloco(cx - 3, 65, cz - 3, 'birch_log'), 'o primeiro clique em Recolher só pede confirmação')
  await bot.clickWindow(22, 0, 0)
  await t.espera(1500)
  t.verificar(await bloco(cx - 3, 65, cz - 3, 'air') && await bloco(cx, 64, cz, 'grass_block') && await bloco(cx - 3, 64, cz - 3, 'grass_block')
    && await bloco(cx - 3, 66, cz + 3, 'air'), 'confirmado: a construção some e o chão volta como era')
  t.verificar(!fazendeiro(cx + 0.5, cz - 2.5), 'o fazendeiro sumiu')
  // a chave exata da Escritura (o inventário inteiro sai cortado com "...")
  const pdc = chave => t.console(`data get entity ${nome} Inventory[{count:1}].components."minecraft:custom_data".PublicBukkitValues."fazendasplugin:${chave}"`)
  const tipoEsc = await pdc('escritura'), nivel = await pdc('nivel_produtividade')
  t.verificar(/data: "galinheiro"/.test(tipoEsc) && /data: 1$/.test(nivel), 'a Escritura voltou com a Produtividade no nível 1', `${tipoEsc} | ${nivel}`)

  console.log('\n== Limite de 8 por terreno ==')
  await t.console(`minecraft:clear ${nome}`)
  for (let i = 0; i < 9; i++) await t.console(`fazendas dar ${nome} horta`)
  // duas fileiras de 4 (cada 7x7 com 1 bloco de folga) e a nona numa terceira fileira
  const centros = [[mx + 4, mz + 5], [mx + 12, mz + 5], [mx + 20, mz + 5], [mx + 28, mz + 5],
    [mx + 4, mz + 13], [mx + 12, mz + 13], [mx + 20, mz + 13], [mx + 28, mz + 13], [mx + 4, mz + 21]]
  let colocadas = 0
  for (const [x, z] of centros) {
    await tp(x + 0.5, 65, z + 1.5, 0)
    await segurarPapel()
    await clicar(x, 64, z)
    msgs = await clicar(x, 64, z)
    if (msgs.some(m => m.includes('construída'))) colocadas++
  }
  t.verificar(colocadas === 8 && msgs.some(m => m.includes('máximo de fazendas')), 'coloca 8 e a nona é recusada', `${colocadas}; ${msgs.find(m => m.includes('máximo')) || msgs[0]}`)
  const lista = await t.console('fazendas lista')
  t.verificar((lista.match(/horta/g) || []).length >= 8, 'o /fazendas lista mostra as 8 Hortas')

  console.log('\n== Limpeza (staff remove as 8) ==')
  await t.console(`op ${nome}`)
  for (const [x, z] of centros.slice(0, 8)) {
    await tp(x + 0.5, 65, z - 6.5, 0)
    await bot.lookAt(new Vec3(x + 0.5, 64.5, z + 0.5))
    await t.espera(300)
    bot.chat('/fazendas remover')
    await t.espera(1000)
  }
  await t.console(`deop ${nome}`)
  t.verificar(await bloco(mx + 4, 65, mz + 2, 'air') && await bloco(mx + 28, 64, mz + 5, 'grass_block'), 'o /fazendas remover da staff tira a fazenda e devolve o chão')
  await t.terminar(bot)
})
