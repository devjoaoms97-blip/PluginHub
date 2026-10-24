// Loja Oficial (EconomyShopPlugin + estrutura "loja"): os 7 vendedores no prédio do portão leste do
// spawn, compra e venda pelo menu do vendedor, desconto do VIP na compra, a trava da venda (no último
// rank com VIP, vender do inventário não passa de 90% do menor preço de compra) e os comandos de
// fora (/shop, /sell) fechados pros jogadores.
// Uso: node teste-loja.js   (depois do "node montar-mapa.js construir loja")
const t = require('./lib')
const { Vec3 } = require('vec3')

const Z = 43 // centro do spawn
const FAZENDEIRO = new Vec3(73.5, 65, Z + 14.5)
const MINERADOR = new Vec3(112.5, 65, Z - 13.5)
const dinheiro = r => Number((r.match(/\$([\d,.]+)/) || [, 'NaN'])[1].replace(/,/g, ''))
const saldo = async nome => dinheiro(await t.console(`balance ${nome}`))

/** Vai até a frente do balcão (olhando pro vendedor), clica e espera o menu. */
async function abrir(bot, npc, onde, yaw) {
  await t.console(`execute in minecraft:spawn run minecraft:tp ${bot.username} ${onde.x} 65 ${onde.z} ${yaw} 0`)
  await t.espera(1500)
  const e = Object.values(bot.entities).find(x => x.position.distanceTo(npc) < 1 && x.name === 'mannequin')
  if (!e) return null
  return new Promise(ok => {
    const fim = setTimeout(() => ok(null), 3000)
    // os itens chegam logo depois da janela abrir
    bot.once('windowOpen', j => { clearTimeout(fim); setTimeout(() => ok(j), 800) })
    bot.activateEntity(e)
  })
}

const slotDe = (janela, nome) => janela.slots.findIndex((s, i) => i < janela.inventoryStart && s?.name === nome)

t.rodar(async () => {
  const lista = await t.console('shop admin npc lista')
  const ids = ['minerador', 'construtor', 'lenhador', 'fazendeiro', 'cacador', 'jardineiro', 'alquimista']
  t.verificar(ids.every(id => lista.includes(id)), 'os 7 vendedores colocados', lista.slice(0, 120))

  const bot = await t.criarBot(t.nomeNovo('Loj'))
  const nome = bot.username
  await t.espera(1000)
  await t.console(`eco set ${nome} 10000`)

  console.log('\n== Fora da loja ==')
  let msgs = await t.capturar(bot, () => bot.chat('/shop'))
  t.verificar(msgs.some(m => m.includes('Loja Oficial')), '/shop manda pra Loja Oficial', msgs[0])
  msgs = await t.capturar(bot, () => bot.chat('/sell all'))
  // sem a permissão, o servidor esconde o comando ("Unknown command")
  t.verificar(msgs.some(m => m.includes('Loja Oficial') || /Unknown|desconhecido/i.test(m)), '/sell fechado pros jogadores', msgs[0])
  await t.capturar(bot, () => bot.chat('/warp loja'), 2000)
  t.verificar(Math.abs(bot.entity.position.x - 67.5) < 2 && Math.abs(bot.entity.position.z - (Z + 0.5)) < 2,
    '/warp loja leva pra entrada da loja', `${bot.entity.position}`)

  console.log('\n== Fazendeiro ==')
  await t.console(`minecraft:clear ${nome}`)
  await t.console(`minecraft:give ${nome} wheat 64`)
  let janela = await abrir(bot, FAZENDEIRO, new Vec3(73.5, 65, Z + 11.5), 0)
  t.verificar(!!janela && slotDe(janela, 'wheat') >= 0, 'o fazendeiro abre o menu da categoria Farm',
    janela ? janela.slots.slice(0, 6).map(s => s?.name).join(',') : 'não abriu')
  if (janela && slotDe(janela, 'wheat') < 0) janela = null
  if (janela) {
    let antes = await saldo(nome)
    await bot.clickWindow(slotDe(janela, 'wheat'), 0, 1) // shift + esquerdo: vende tudo
    await t.espera(1200)
    const depois = await saldo(nome)
    // o inventário do bot demora a atualizar: confere pelo servidor (clear com 0 só conta)
    const sobrou = await t.console(`minecraft:clear ${nome} wheat 0`)
    t.verificar(depois > antes && !/Found \d+/.test(sobrou), 'vendeu todo o trigo', `${antes} → ${depois} | ${sobrou}`)
    janela = bot.currentWindow
    antes = depois
    await bot.clickWindow(slotDe(janela, 'carrot'), 1, 0) // direito: compra 1
    await t.espera(1200)
    const pago = antes - await saldo(nome)
    t.verificar(bot.inventory.items().some(i => i.name === 'carrot') && Math.abs(pago - 3) < 0.01, 'comprou 1 cenoura pelo preço cheio (R$ 3)', `pagou ${pago}`)
    // VIP Diamante: 12% de desconto na compra
    await t.console(`lp user ${nome} permission set vip.desconto.diamante true`)
    await t.espera(500)
    antes = await saldo(nome)
    await bot.clickWindow(slotDe(bot.currentWindow, 'carrot'), 1, 0)
    await t.espera(1200)
    const pagoVip = antes - await saldo(nome)
    t.verificar(Math.abs(pagoVip - 2.64) < 0.01, 'com VIP Diamante, 12% de desconto (R$ 2,64)', `pagou ${pagoVip}`)
    bot.closeWindow(bot.currentWindow)
  }

  console.log('\n== Trava da venda ==')
  // último rank (x2,0) + VIP Diamante (+12%): o diamante renderia R$ 134; a trava segura em 90% de R$ 105,60
  await t.console(`ranks admin set ${nome} rei`)
  await t.console(`minecraft:give ${nome} diamond 1`)
  janela = await abrir(bot, MINERADOR, new Vec3(112.5, 65, Z - 10.5), 180)
  t.verificar(!!janela && slotDe(janela, 'diamond') >= 0, 'o minerador abre o menu de Minérios',
    janela ? janela.slots.slice(0, 6).map(s => s?.name).join(',') : 'não abriu')
  if (janela && slotDe(janela, 'diamond') < 0) janela = null
  if (janela) {
    const antes = await saldo(nome)
    await bot.clickWindow(slotDe(janela, 'diamond'), 0, 0)
    await t.espera(1200)
    const recebeu = await saldo(nome) - antes
    const teto = 120 * 0.88 * 0.9 * 0.98
    t.verificar(recebeu > 0 && recebeu <= teto + 0.01, `a trava segura a venda (no máximo R$ ${teto.toFixed(2)})`, `recebeu ${recebeu.toFixed(2)}`)
    bot.closeWindow(bot.currentWindow)
  }

  await t.console(`lp user ${nome} permission unset vip.desconto.diamante`)
  await t.console(`ranks admin set ${nome} campones`)
  await t.console(`minecraft:clear ${nome}`)
  await t.terminar(bot)
})
