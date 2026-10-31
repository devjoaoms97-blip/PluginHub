// Inventário cheio nas lojas: com o inventário lotado, a compra é recusada com aviso, sem cobrar e
// sem jogar nada no chão (antes cobrava e o item caía, e a Limpeza levava). Com um espaço livre, compra.
// Cobre o Maquinista (máquina e combustível), o Domador (spawner) e o /buy da Loja Oficial.
// Uso: node teste-inventario-cheio.js
const t = require('./lib')
const { Vec3 } = require('vec3')

const dinheiro = r => Number((r.match(/\$([\d,.]+)/) || [, 'NaN'])[1].replace(/,/g, ''))
const saldo = async nome => dinheiro(await t.console(`balance ${nome}`))
const MAQUINISTA = new Vec3(112.5, 65, 43 + 14.5)
const DOMADOR = new Vec3(125.5, 65, 43 - 13.5)

const janela = (bot, fn) => new Promise(ok => {
  const fim = setTimeout(() => ok(null), 3000)
  bot.once('windowOpen', j => { clearTimeout(fim); setTimeout(() => ok(j), 800) })
  fn()
})

t.rodar(async () => {
  const bot = await t.criarBot(t.nomeNovo('Inv'))
  const nome = bot.username
  await t.espera(1500)
  await t.console(`eco set ${nome} 500000`)
  await t.console(`ranks admin set ${nome} fazendeiro`)
  await t.console(`lp user ${nome} permission set shop.comandos true`)

  const encher = async () => {
    await t.console(`minecraft:clear ${nome}`)
    for (let i = 0; i < 36; i++) await t.console(`minecraft:give ${nome} cobblestone 64`)
    await t.espera(800)
  }
  const liberarUm = () => t.console(`minecraft:clear ${nome} cobblestone 64`)
  const noChao = () => t.itensNoChao(bot).filter(n => !String(n).startsWith('cobblestone'))
  const contar = async item => Number(((await t.console(`minecraft:clear ${nome} ${item} 0`)).match(/Found (\d+)/) || [, 0])[1])

  // compra pelo menu de um NPC: abre, clica no slot, devolve as mensagens
  const comprarNoNpc = async (lugar, pos, slot, botaoDireito = 0) => {
    await t.console(`execute in minecraft:spawn run minecraft:tp ${nome} ${lugar}`)
    await t.espera(1500)
    const npc = Object.values(bot.entities).find(e => e.position.distanceTo(pos) < 1 && e.name === 'mannequin')
    if (!npc) return ['NPC não encontrado']
    const j = await janela(bot, () => bot.activateEntity(npc))
    if (!j) return ['menu não abriu']
    const idx = typeof slot === 'function' ? slot(j) : slot
    const msgs = await t.capturar(bot, () => bot.clickWindow(idx, botaoDireito, 0), 1200)
    if (bot.currentWindow) bot.closeWindow(bot.currentWindow)
    return msgs
  }

  const caso = async (rotulo, item, comprar) => {
    console.log(`\n== ${rotulo} ==`)
    await encher()
    let antes = await saldo(nome)
    let msgs = await comprar()
    await t.espera(500)
    const depois = await saldo(nome)
    t.verificar(msgs.some(m => m.includes('inventário está cheio')) && depois === antes && noChao().length === 0,
      `cheio: recusa, avisa, não cobra e nada cai no chão`, `${msgs.find(m => /cheio|comprou/.test(m)) || msgs[0]} | ${antes} → ${depois} | chão: ${noChao()}`)
    await liberarUm()
    antes = await saldo(nome)
    msgs = await comprar()
    await t.espera(500)
    const pago = antes - await saldo(nome)
    const tem = await contar(item)
    t.verificar(pago > 0 && tem > 0 && noChao().length === 0, `com um espaço livre, compra e o item vem pro inventário`, `pagou ${pago}, ${tem}x ${item}`)
  }

  const balcaoMaq = `112.5 65 ${43 + 11.5} 0 0`
  await caso('Maquinista: máquina', 'barrier', () => comprarNoNpc(balcaoMaq, MAQUINISTA, 2))
  await caso('Maquinista: combustível', 'paper', () => comprarNoNpc(balcaoMaq, MAQUINISTA, 22))
  await caso('Domador: spawner', 'spawner', () => comprarNoNpc(`125.5 65 ${43 - 10.5} 180 0`, DOMADOR,
    j => j.slots.findIndex((s, i) => i < j.inventoryStart && s?.name === 'pig_spawn_egg')))
  await caso('Loja Oficial: /buy', 'oak_log', () => t.capturar(bot, () => bot.chat('/buy oak_log 16'), 1200))

  await t.console(`lp user ${nome} permission unset shop.comandos`)
  await t.console(`ranks admin set ${nome} campones`)
  await t.console(`minecraft:clear ${nome}`)
  await t.terminar(bot)
})
