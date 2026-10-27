// Spawners empilhados (SpawnersPlugin): o vendedor do balcão Spawners da Loja Oficial vende, o
// spawner só entra no terreno, empilha clicando com outro igual, a pilha de mobs ("x2 Porco") nasce
// num lugar livre em cima ou em volta do spawner, um abate mata a pilha inteira com os drops de
// todos, e quebrar devolve os spawners.
// Uso: node teste-spawners.js   (leva ~1 min: espera o ciclo de 20 s)
const t = require('./lib')
const { Vec3 } = require('vec3')

const dinheiro = r => Number((r.match(/\$([\d,.]+)/) || [, 'NaN'])[1].replace(/,/g, ''))
const saldo = async nome => dinheiro(await t.console(`balance ${nome}`))
const DOMADOR = new Vec3(125.5, 65, 43 - 13.5) // balcão Spawners, lado norte da loja
const CELULA = 37 // terreno de 32 + rua de 5 (TerrenosPlugin)

t.rodar(async () => {
  const bot = await t.criarBot(t.nomeNovo('Spw'))
  const nome = bot.username
  await t.espera(1000)
  await t.console(`eco set ${nome} 200000`)
  await t.console(`minecraft:clear ${nome}`)

  console.log('\n== Compra no Domador ==')
  await t.console(`execute in minecraft:spawn run minecraft:tp ${nome} 125.5 65 ${43 - 10.5} 180 0`)
  await t.espera(1500)
  const npc = Object.values(bot.entities).find(e => e.position.distanceTo(DOMADOR) < 1 && e.name === 'mannequin')
  const janela = npc && await new Promise(ok => {
    const fim = setTimeout(() => ok(null), 3000)
    bot.once('windowOpen', j => { clearTimeout(fim); setTimeout(() => ok(j), 800) })
    bot.activateEntity(npc)
  })
  const slotPorco = janela ? janela.slots.findIndex((s, i) => i < janela.inventoryStart && s?.name === 'pig_spawn_egg') : -1
  t.verificar(slotPorco >= 0, 'o Domador abre a loja de spawners', janela ? janela.slots.slice(0, 4).map(s => s?.name).join(',') : 'não abriu')
  if (slotPorco < 0) return t.terminar(bot)
  const antes = await saldo(nome)
  await bot.clickWindow(slotPorco, 0, 0)
  await t.espera(800)
  await bot.clickWindow(slotPorco, 0, 0)
  await t.espera(1000)
  bot.closeWindow(bot.currentWindow)
  const pago = antes - await saldo(nome)
  t.verificar(Math.abs(pago - 16000) < 0.01 && bot.inventory.items().some(i => i.name === 'spawner' && i.count === 2),
    'comprou 2 spawners de porco (R$ 16.000)', `pagou ${pago}`)

  console.log('\n== Fora do terreno ==')
  const spawnerNaMao = async () => { await bot.equip(bot.inventory.items().find(i => i.name === 'spawner'), 'hand'); await t.espera(300) }
  await t.console(`execute in minecraft:spawn run minecraft:tp ${nome} 0.5 65 50.5`)
  await t.espera(1200)
  await spawnerNaMao()
  let msgs = await t.capturar(bot, () => bot.placeBlock(bot.blockAt(bot.entity.position.offset(0, -1, 2)), new Vec3(0, 1, 0)).catch(() => {}), 1200)
  // quem barra primeiro pode ser o AreasPlugin (o spawn não aceita bloco nenhum), a proteção do mundo ou
  // o próprio plugin: o que importa é não entrar
  t.verificar(msgs.some(m => /só pode ser colocado no seu terreno|não pode construir|não dá pra colocar/.test(m)), 'no spawn não deixa colocar', msgs[0])

  console.log('\n== No terreno ==')
  msgs = await t.capturar(bot, () => bot.chat('/terreno auto'), 3000)
  const id = (msgs.join(' ').match(/terreno (-?\d+;-?\d+)/) || [])[1]
  const [gx, gz] = (id || '0;0').split(';').map(Number)
  // o /terreno auto deixa na rua: entra no meio do terreno pra comprar
  await t.console(`execute in minecraft:terrenos run minecraft:tp ${nome} ${gx * CELULA + 16.5} 65 ${gz * CELULA + 12.5} 0 0`)
  await t.espera(1500)
  msgs = await t.capturar(bot, () => bot.chat('/terreno comprar'), 1500)
  t.verificar(msgs.some(m => m.includes('comprado')), 'comprou um terreno pra testar', msgs[0])
  // a pilha de uma rodada anterior (o terreno é o mesmo) juntaria com a nova: some com ela antes
  await t.console(`execute in minecraft:terrenos positioned ${gx * CELULA + 16} 65 ${gz * CELULA + 16} run minecraft:kill @e[type=pig,distance=..48]`)
  await spawnerNaMao()
  const chao = bot.blockAt(bot.entity.position.offset(0, -1, 2))
  await bot.placeBlock(chao, new Vec3(0, 1, 0)).catch(() => {})
  await t.espera(800)
  const lugar = chao.position.offset(0, 1, 0)
  const bloco = bot.blockAt(lugar)
  t.verificar(bloco?.name === 'spawner', 'o spawner entrou no terreno', bloco?.name)

  await spawnerNaMao()
  msgs = await t.capturar(bot, () => bot.activateBlock(bot.blockAt(lugar)), 1000)
  const lista = await t.console('spawners lista')
  t.verificar(/x2 em terrenos/.test(t.semCor(lista)), 'clicar com outro igual empilha (x2)', t.semCor(lista).slice(0, 140))

  console.log('\n== Pilha de mobs ==')
  await t.espera(22000) // um ciclo
  // a pilha é o porco com "xN" no nome; nasce em cima ou em volta do spawner e anda como um porco normal
  const pilha = () => Object.values(bot.entities).find(e => e.name === 'pig' && /x\d+/.test(t.semCor(JSON.stringify(e.metadata)))
    && e.position.distanceTo(lugar) < 12)
  let porco = pilha()
  const nomePilha = porco ? t.semCor(JSON.stringify(porco.metadata)) : ''
  t.verificar(!!porco && /x2 /.test(nomePilha) && /Porco/.test(nomePilha), 'a pilha nasceu com os 2 porcos ("x2 Porco")', nomePilha.slice(0, 80) || 'não achei')
  // ela anda: traz pra frente do bot antes de bater
  await t.console(`execute in minecraft:terrenos at ${nome} run minecraft:tp @e[type=pig,distance=..16,limit=1,sort=nearest] ^ ^ ^2`)
  await t.espera(800)
  porco = pilha()

  // um abate mata a pilha toda: os drops dos 2 porcos caem de uma vez
  await t.console(`minecraft:kill @e[type=item,distance=..20,x=${lugar.x},y=${lugar.y},z=${lugar.z}]`)
  await t.console(`minecraft:give ${nome} netherite_sword`)
  await bot.equip(bot.inventory.items().find(i => i.name === 'netherite_sword'), 'hand')
  const idDaPilha = porco?.id
  for (let i = 0; i < 12 && porco; i++) {
    await bot.lookAt(porco.position.offset(0, 0.5, 0))
    bot.attack(porco)
    await t.espera(700)
    porco = bot.entities[idDaPilha]
  }
  await t.espera(800)
  const outra = Object.values(bot.entities).find(e => e.name === 'pig' && e.position.distanceTo(lugar) < 12)
  t.verificar(!!idDaPilha && !porco && !outra, 'um abate mata a pilha inteira (não sobra x1)', outra ? t.semCor(JSON.stringify(outra.metadata)).slice(0, 80) : '')
  // a carne cai no chão ou o bot do lado já pega
  const carne = t.itensNoChao(bot).filter(n => String(n).startsWith('porkchop')).reduce((s, n) => s + Number(String(n).split('x')[1] || 0), 0)
    + Number(((await t.console(`minecraft:clear ${nome} porkchop 0`)).match(/Found (\d+)/) || [, 0])[1])
  t.verificar(carne >= 2, 'e derrubou a carne dos 2 porcos de uma vez', `${carne} carne(s)`)

  console.log('\n== Quebrar devolve ==')
  // pode haver spawners de outros jogadores no servidor: confere que a conta caiu em um
  const colocados = async () => Number((t.semCor(await t.console('spawners lista')).match(/colocados: (\d+)/) || [, -1])[1])
  const antesDeQuebrar = await colocados()
  await bot.dig(bot.blockAt(lugar), true).catch(() => {})
  await t.espera(1500)
  // os spawners caem no chão do bloco quebrado (o item é o mesmo da loja, com o tipo marcado)
  const noChao = t.itensNoChao(bot).filter(n => String(n).startsWith('spawner'))
  const depois = await colocados()
  // caem no chão, e o bot do lado às vezes já pega: vale no chão ou no inventário
  const naMao = Number(((await t.console(`minecraft:clear ${nome} spawner 0`)).match(/Found (\d+)/) || [, 0])[1])
  t.verificar((noChao.some(n => String(n) === 'spawnerx2') || naMao === 2) && depois === antesDeQuebrar - 1, 'quebrar devolve os 2 spawners',
    `chão ${noChao} | inventário ${naMao} | colocados ${antesDeQuebrar} → ${depois}`)

  await t.capturar(bot, () => bot.chat('/terreno abandonar confirmar'), 1500)
  await t.console(`minecraft:clear ${nome}`)
  await t.terminar(bot)
})
