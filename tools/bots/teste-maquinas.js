// Máquinas (MaquinasPlugin + PacotePlugin): o pacote de texturas é servido, o Maquinista do balcão
// Máquinas vende a máquina e o combustível, a máquina entra no terreno desenhada pelo modelo próprio
// (barreira + item display), abastece, produz pelo relógio, coleta, vende os produtos e recolhe.
// Uso: node teste-maquinas.js
const t = require('./lib')
const http = require('http')
const { Vec3 } = require('vec3')

const dinheiro = r => Number((r.match(/\$([\d,.]+)/) || [, 'NaN'])[1].replace(/,/g, ''))
const saldo = async nome => dinheiro(await t.console(`balance ${nome}`))
const MAQUINISTA = new Vec3(112.5, 65, 43 + 14.5) // balcão Máquinas, lado sul da loja
const CELULA = 37
const contar = async (nome, item) => Number(((await t.console(`minecraft:clear ${nome} ${item} 0`)).match(/Found (\d+)/) || [, 0])[1])

const baixar = url => new Promise(ok => http.get(url, r => {
  let tam = 0
  r.on('data', d => { tam += d.length })
  r.on('end', () => ok({ status: r.statusCode, tam }))
}).on('error', () => ok({ status: 0, tam: 0 })))

/** Espera a próxima janela abrir (com os itens) depois de rodar fn. */
const janela = (bot, fn) => new Promise(ok => {
  const fim = setTimeout(() => ok(null), 3000)
  bot.once('windowOpen', j => { clearTimeout(fim); setTimeout(() => ok(j), 800) })
  fn()
})

t.rodar(async () => {
  console.log('\n== Pacote de texturas ==')
  const pacote = await baixar('http://localhost:8765/pacote.zip')
  t.verificar(pacote.status === 200 && pacote.tam > 1000, 'o pacote.zip é servido pelo PacotePlugin', `${pacote.status}, ${pacote.tam} bytes`)

  const bot = await t.criarBot(t.nomeNovo('Maq'))
  const nome = bot.username
  await t.espera(1500)
  await t.console(`eco set ${nome} 500000`)
  await t.console(`minecraft:clear ${nome}`)
  await t.console(`ranks admin set ${nome} fazendeiro`)

  console.log('\n== Compra no Maquinista ==')
  await t.console(`execute in minecraft:spawn run minecraft:tp ${nome} 112.5 65 ${43 + 11.5} 0 0`)
  await t.espera(1500)
  const npc = Object.values(bot.entities).find(e => e.position.distanceTo(MAQUINISTA) < 1 && e.name === 'mannequin')
  let j = npc && await janela(bot, () => bot.activateEntity(npc))
  t.verificar(!!j && j.slots[2]?.name === 'barrier', 'o Maquinista abre a loja das máquinas', j ? j.slots.slice(2, 7).map(s => s?.name).join(',') : 'não abriu')
  if (!j) return t.terminar(bot)
  await bot.clickWindow(2, 0, 0) // o Tear
  await t.espera(1200)
  await bot.clickWindow(22, 0, 0) // 16 células
  await t.espera(1200)
  bot.closeWindow(bot.currentWindow)
  t.verificar(await contar(nome, 'barrier') === 1 && await contar(nome, 'paper') === 16, 'comprou o Tear e 16 Células de Combustível')

  console.log('\n== No terreno ==')
  let msgs = await t.capturar(bot, () => bot.chat('/terreno auto'), 3000)
  const [gx, gz] = ((msgs.join(' ').match(/terreno (-?\d+;-?\d+)/) || [])[1] || '0;0').split(';').map(Number)
  await t.console(`execute in minecraft:terrenos run minecraft:tp ${nome} ${gx * CELULA + 16.5} 65 ${gz * CELULA + 12.5} 0 0`)
  await t.espera(1500)
  msgs = await t.capturar(bot, () => bot.chat('/terreno comprar'), 1500)
  t.verificar(msgs.some(m => m.includes('comprado')), 'comprou um terreno', msgs[0])
  await bot.equip(bot.inventory.items().find(i => i.name === 'barrier'), 'hand')
  await t.espera(300)
  const chao = bot.blockAt(bot.entity.position.offset(0, -1, 2))
  await bot.placeBlock(chao, new Vec3(0, 1, 0)).catch(() => {})
  await t.espera(1000)
  const lugar = chao.position.offset(0, 1, 0)
  const [x, y, z] = [lugar.x, lugar.y, lugar.z]
  const modelo = await t.console(`execute in minecraft:terrenos positioned ${x + 0.5} ${y + 0.5} ${z + 0.5} run data get entity @e[type=item_display,limit=1,sort=nearest,distance=..1] item.components`)
  t.verificar((await t.console(`execute in minecraft:terrenos if block ${x} ${y} ${z} minecraft:barrier`)).includes('passed')
    && modelo.includes('servidor:maquina_tear'), 'o Tear entrou, desenhado pelo modelo do pacote', modelo.slice(0, 120))

  console.log('\n== Produção ==')
  j = await janela(bot, () => bot.activateBlock(bot.blockAt(lugar)))
  t.verificar(!!j, 'clicar na máquina abre o menu dela')
  if (!j) return t.terminar(bot)
  await bot.clickWindow(12, 0, 0) // abastecer
  await t.espera(1200)
  t.verificar(await contar(nome, 'paper') === 0, 'abasteceu com as 16 Células')
  bot.closeWindow(bot.currentWindow)
  await t.console('maquinas acelerar 30') // 30 min: 30 tecidos (1 por minuto, 0,25 Célula cada)
  j = await janela(bot, () => bot.activateBlock(bot.blockAt(lugar)))
  await bot.clickWindow(10, 0, 0) // coletar
  await t.espera(1200)
  bot.closeWindow(bot.currentWindow)
  const tecidos = await contar(nome, 'paper')
  t.verificar(tecidos === 30, 'em 30 minutos fez 30 Tecidos Finos, e coletou', `${tecidos}`)

  console.log('\n== Venda ==')
  await t.console(`execute in minecraft:spawn run minecraft:tp ${nome} 112.5 65 ${43 + 11.5} 0 0`)
  await t.espera(1500)
  const npc2 = Object.values(bot.entities).find(e => e.position.distanceTo(MAQUINISTA) < 1 && e.name === 'mannequin')
  j = npc2 && await janela(bot, () => bot.activateEntity(npc2))
  const antes = await saldo(nome)
  if (j) {
    await bot.clickWindow(38, 0, 0) // o Tecido Fino
    await t.espera(1200)
    bot.closeWindow(bot.currentWindow)
  }
  const recebeu = await saldo(nome) - antes
  t.verificar(recebeu > 0 && await contar(nome, 'paper') === 0, 'vendeu os 30 tecidos no balcão', `+${recebeu.toFixed(2)}`)

  console.log('\n== Recolher ==')
  await t.console(`execute in minecraft:terrenos run minecraft:tp ${nome} ${gx * CELULA + 16.5} 65 ${gz * CELULA + 12.5} 0 0`)
  await t.espera(1500)
  j = await janela(bot, () => bot.activateBlock(bot.blockAt(lugar)))
  if (j) await bot.clickWindow(22, 0, 0)
  await t.espera(1200)
  t.verificar((await t.console(`execute in minecraft:terrenos if block ${x} ${y} ${z} minecraft:air`)).includes('passed')
    && await contar(nome, 'barrier') === 1, 'recolher devolve a máquina e tira o bloco')

  await t.capturar(bot, () => bot.chat('/terreno abandonar confirmar'), 1500)
  await t.console(`ranks admin set ${nome} campones`)
  await t.console(`minecraft:clear ${nome}`)
  await t.terminar(bot)
})
