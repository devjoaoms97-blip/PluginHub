// Skills de profissão (SkillsPlugin, 2026-11-02): Minerador (XP por bloco da mina e o 3x3, cujos blocos
// extras só vão pra venda automática e não dão XP), Lenhador (tora a mais e derrubar a árvore natural no
// mundo de recursos, mas não a de folhas colocadas), Agricultor (colheita dupla e o bônus na venda do
// Fazendeiro) e Pescador (XP por pesca, mapa do tesouro e Fragmento Arcano).
// As chances do jogo são baixas: o teste põe chances de 100% no config do servidor, recarrega
// (/skills admin reload) e devolve o config original no fim.
// Uso: node teste-profissoes.js   (com a pedreira, o mundo de recursos e a loja montados)
const fs = require('fs')
const path = require('path')
const t = require('./lib')
const { Vec3 } = require('vec3')

const CONFIG = path.join(process.env.MC_TESTE || 'C:\\mc-teste', 'plugins', 'SkillsPlugin', 'config.yml')
// 0.02 por nível = 100% no 50 (98% no 49)
const FORCADO = `
minerador:
  chance-3x3-por-nivel: 0.02
agricultor:
  chance-colheita-dupla-por-nivel: 0.02
pescador:
  chance-mapa-por-nivel: 0.02
  chance-fragmento-por-nivel: 0.02
lenhador:
  chance-tora-extra-por-nivel: 0.02
  chance-derrubar-por-nivel: 0.02
`
const R = 'minecraft:recursos'
const Z = 43 // centro do spawn
const FAZENDEIRO = new Vec3(73.5, 65, Z + 14.5)

const dinheiro = r => Number((r.match(/\$([\d,.]+)/) || [, 'NaN'])[1].replace(/,/g, ''))

/** XP atual da skill pelo /skills ver ("Minerador - Nível 49 (12/26.000 XP)"). */
async function xpDe(bot, nome) {
  const msgs = await t.capturar(bot, () => bot.chat('/skills ver'), 1200)
  const linha = msgs.map(t.semCor).find(m => m.startsWith(nome + ' '))
  const m = linha && linha.match(/\(([\d.]+)\//)
  return m ? Number(m[1].replace(/\./g, '')) : NaN
}

/** Blocos que não são ar dentro da caixa da mina, do que o bot enxerga. */
function solidos(bot, caixa) {
  let n = 0
  for (let x = caixa.min[0]; x <= caixa.max[0]; x++)
    for (let y = caixa.min[1]; y <= caixa.max[1]; y++)
      for (let z = caixa.min[2]; z <= caixa.max[2]; z++) {
        const b = bot.blockAt(new Vec3(x, y, z))
        if (b && b.name !== 'air' && b.name !== 'cave_air') n++
      }
  return n
}

// a maldição do boss (boss que escapou) tira 10% do XP de skill: o XP esperado vale entre 90% e 100%
const xpOk = (ganho, esperado) => ganho >= Math.floor(esperado * 0.9) && ganho <= esperado

/** Recolhe o que caiu ali (o bot fica em cima) e conta quantos desse item ele tem, pelo servidor. */
async function recolher(bot, x, y, z, item) {
  await t.console(`execute in ${R} run minecraft:tp ${bot.username} ${x} ${y} ${z}`)
  await t.espera(2000)
  const r = await t.console(`minecraft:clear ${bot.username} ${item} 0`)
  return Number((r.match(/Found (\d+)/) || [, 0])[1])
}

t.rodar(async () => {
  const original = fs.readFileSync(CONFIG, 'utf8')
  fs.writeFileSync(CONFIG, original.replace(/\s*$/, '\n') + FORCADO)
  await t.console('skills admin reload')
  const bot = await t.criarBot(t.nomeNovo('Prof'))
  const nome = bot.username
  try {
    await t.espera(1000)
    await t.console(`vip remove ${nome}`)
    await t.console(`gamemode survival ${nome}`)
    for (const [skill, nivel] of [['minerador', 49], ['agricultor', 50], ['pescador', 49], ['lenhador', 50]]) {
      await t.console(`skills admin setlevel ${nome} ${skill} ${nivel}`)
    }

    console.log('\n== Minerador ==')
    const pedreira = t.mina('pedreira')
    await t.console(`minecraft:clear ${nome}`)
    await t.console(`minecraft:give ${nome} diamond_pickaxe 1`)
    await t.irParaMina(nome, pedreira)
    await t.espera(1000)
    const xp0 = await xpDe(bot, 'Minerador')
    const saldo0 = dinheiro(await t.console(`balance ${nome}`))
    const antes = solidos(bot, pedreira)
    const quebrados = await t.minerar(bot, pedreira, 5)
    await t.espera(1000)
    const sumiram = antes - solidos(bot, pedreira)
    const xp1 = await xpDe(bot, 'Minerador')
    const saldo1 = dinheiro(await t.console(`balance ${nome}`))
    t.verificar(sumiram > quebrados * 2, 'com 98% de chance, cada picaretada quebra o 3x3', `${quebrados} à mão, ${sumiram} sumiram`)
    t.verificar(xpOk(xp1 - xp0, quebrados), 'só os blocos quebrados à mão dão XP (1 cada)', `${xp0} → ${xp1}`)
    t.verificar(saldo1 > saldo0, 'e tudo foi vendido na hora', `${saldo0} → ${saldo1}`)
    t.verificar(t.itensNoChao(bot).length === 0, 'sem nada no chão', JSON.stringify(t.itensNoChao(bot)))

    // plataforma no mundo de recursos: terra, ar em cima, uma piscina e o lugar das árvores
    console.log('\n== Lenhador ==')
    await t.console(`execute in ${R} run minecraft:forceload add -16 -16 16 16`)
    await t.console(`execute in ${R} run fill -6 149 -6 6 149 12 minecraft:dirt`)
    await t.console(`execute in ${R} run fill -6 150 -6 6 165 12 minecraft:air`)
    await t.console(`execute in ${R} run fill -2 147 6 2 149 10 minecraft:water`)
    await t.console(`minecraft:clear ${nome}`)
    await t.console(`minecraft:give ${nome} diamond_axe 1`)
    await t.console(`execute in ${R} run minecraft:tp ${nome} 2.5 150 0.5 90 20`)
    await t.espera(2500)
    const arvore = async persistente => {
      await t.console(`execute in ${R} run fill 0 150 0 0 154 0 minecraft:oak_log`)
      await t.console(`execute in ${R} run fill -2 153 -2 2 156 2 minecraft:oak_leaves[persistent=${persistente}] replace minecraft:air`)
      await t.console('minecraft:kill @e[type=item]')
      await t.espera(800)
    }
    const troncos = () => [150, 151, 152, 153, 154].filter(y => bot.blockAt(new Vec3(0, y, 0))?.name === 'oak_log').length
    await arvore(false)
    const machado = bot.inventory.items().find(i => i.name === 'diamond_axe')
    if (machado) await bot.equip(machado, 'hand')
    await bot.dig(bot.blockAt(new Vec3(0, 150, 0)), true).catch(() => {})
    await t.espera(1500)
    t.verificar(troncos() === 0, 'árvore natural: derrubou a árvore inteira', `${troncos()} troncos ficaram`)
    let n = await recolher(bot, 0.5, 150, 0.5, 'oak_log')
    t.verificar(n === 6, 'os 5 troncos caíram, mais a tora a mais do cortado', `${n} troncos recolhidos`)
    await t.console(`minecraft:clear ${nome} oak_log`)
    await t.console(`execute in ${R} run minecraft:tp ${nome} 2.5 150 0.5 90 20`)
    await t.espera(1500)

    await t.console(`execute in ${R} run fill -2 150 -2 2 160 2 minecraft:air`)
    await arvore(true)
    await bot.dig(bot.blockAt(new Vec3(0, 150, 0)), true).catch(() => {})
    await t.espera(1500)
    t.verificar(troncos() === 4, 'folhas colocadas (persistentes): só o tronco cortado sai', `${troncos()} troncos ficaram`)
    n = await recolher(bot, 0.5, 150, 0.5, 'oak_log')
    t.verificar(n === 2, 'com a tora a mais nele', `${n} troncos recolhidos`)
    await t.console(`execute in ${R} run fill -2 150 -2 2 160 2 minecraft:air`)

    console.log('\n== Agricultor ==')
    await t.console(`execute in ${R} run setblock 0 149 -3 minecraft:farmland`)
    await t.console(`execute in ${R} run setblock 0 150 -3 minecraft:wheat[age=7]`)
    await t.console('minecraft:kill @e[type=item]')
    await t.espera(800)
    await t.console(`execute in ${R} run minecraft:tp ${nome} 0.5 150 -1.5 180 30`)
    await t.espera(1500)
    await bot.dig(bot.blockAt(new Vec3(0, 150, -3)), true).catch(() => {})
    await t.espera(1200)
    n = await recolher(bot, 0.5, 150, -2.5, 'wheat')
    t.verificar(n === 2, 'trigo maduro colhido com a mão: colheita dupla', `${n} trigos recolhidos`)

    console.log('\n== Pescador ==')
    await t.console(`minecraft:clear ${nome}`)
    await t.console(`minecraft:give ${nome} fishing_rod[enchantments={"minecraft:lure":3}] 1`)
    await t.console(`execute in ${R} run minecraft:tp ${nome} 0.5 150 4.5 0 30`)
    await t.espera(1500)
    const vara = bot.inventory.items().find(i => i.name === 'fishing_rod')
    if (vara) await bot.equip(vara, 'hand')
    const xpP0 = await xpDe(bot, 'Pescador')
    let pescou = false
    const msgsPesca = await t.capturar(bot, async () => {
      for (let i = 0; i < 3 && !pescou; i++) {
        await bot.lookAt(new Vec3(0.5, 149, 8.5))
        pescou = await Promise.race([bot.fish().then(() => true, () => false), t.espera(45000).then(() => false)])
      }
    })
    const xpP1 = await xpDe(bot, 'Pescador')
    t.verificar(pescou, 'o bot pescou')
    t.verificar(xpOk(xpP1 - xpP0, 8), 'cada pesca dá 8 XP de Pescador', `${xpP0} → ${xpP1}`)
    t.verificar(msgsPesca.some(m => m.includes('pescou um Mapa do Tesouro')), 'com 98% de chance, veio um mapa do tesouro',
      msgsPesca.find(m => m.includes('Mapa')))
    t.verificar(msgsPesca.some(m => m.includes('pescou um Fragmento Arcano')), 'e um Fragmento Arcano')
    await t.console(`execute in ${R} run minecraft:forceload remove -16 -16 16 16`)

    console.log('\n== Agricultor no Fazendeiro ==')
    await t.console(`minecraft:clear ${nome}`)
    await t.console(`minecraft:give ${nome} wheat 1`)
    await t.console(`execute in minecraft:spawn run minecraft:tp ${nome} 73.5 65 ${Z + 11.5} 0 0`)
    await t.espera(1500)
    const npc = Object.values(bot.entities).find(x => x.position.distanceTo(FAZENDEIRO) < 1 && x.name === 'mannequin')
    const janela = npc && await new Promise(ok => {
      const fim = setTimeout(() => ok(null), 3000)
      bot.once('windowOpen', j => { clearTimeout(fim); setTimeout(() => ok(j), 800) })
      bot.activateEntity(npc)
    })
    const slot = janela ? janela.slots.findIndex((s, i) => i < janela.inventoryStart && s?.name === 'wheat') : -1
    let msgsVenda = []
    if (slot >= 0) msgsVenda = await t.capturar(bot, () => bot.clickWindow(slot, 0, 0), 1200)
    t.verificar(msgsVenda.some(m => m.includes('Agricultor +20')), 'a venda no Fazendeiro tem o bônus do Agricultor (+20% no 50)',
      msgsVenda.find(m => m.includes('Vendeu')) || (janela ? 'sem trigo no menu' : 'o menu não abriu'))
    bot.closeWindow?.(bot.currentWindow)
  } finally {
    fs.writeFileSync(CONFIG, original)
    await t.console('skills admin reload')
    await t.console(`minecraft:clear ${nome}`)
  }
  await t.terminar(bot)
})
