// Cena pra gravar a divulgação: 8 bots com cara de jogador, de armadura e espada, num Gladiador
// individual de verdade, cada um caçando o oponente mais perto até sobrar um.
// Prepara tudo, inscreve os bots e espera o arquivo LUTAR nesta pasta pra iniciar o evento
// (dá tempo de quem grava se posicionar). Uso: node cena-gladiador.js   e depois criar LUTAR.
const fs = require('fs')
const path = require('path')
const t = require('./lib')

const NOMES = ['LoboNegro', 'Kaique77', 'Duda_Mine', 'Rafa_PvP', 'TioBrasa', 'Nina_Rei', 'ZeDaMina', 'Ferreiro_BR']
const LUTAR = path.join(__dirname, 'LUTAR')
// o coliseu tem o centro em (600, 0) no mundo arenas (ver teste-gladiador.js)
const naArena = bot => bot.entity && Math.hypot(bot.entity.position.x - 600, bot.entity.position.z) <= 80
// Sem pathfinding, os bots não atravessam as ruínas dos pontos oficiais: durante a cena a arena usa
// um anel de 8 pontos perto do centro, olhando pra dentro. Os 16 oficiais voltam no fim.
const ANEL = NOMES.map((_, i) => {
  const a = (i / NOMES.length) * 2 * Math.PI
  const dx = Math.round(Math.sin(a) * 9), dz = Math.round(-Math.cos(a) * 9)
  return [600 + dx + 0.5, 65, dz + 0.5, Math.round(Math.atan2(dx, -dz) * 180 / Math.PI) + 180]
})

function pontosOriginais() {
  const relativos = [[38, 0], [19, 28], [0, 32], [-27, 23], [-38, 0], [-27, -23], [0, -32], [27, -23],
    [46, 22], [18, 42], [-14, 43], [-46, 22], [-46, -22], [-27, -39], [14, -43], [46, -22]]
  return relativos.map(([dx, dz]) => [600 + dx + 0.5, 65, dz + 0.5, Math.round(Math.atan2(dx, -dz) * 180 / Math.PI)])
}

async function definirPontos(staff, pontos) {
  await t.capturar(staff, () => staff.chat('/gladiador arena spawn limpar'), 500)
  for (const [x, y, z, yaw] of pontos) {
    await t.console(`execute in minecraft:arenas run minecraft:tp ${staff.username} ${x} ${y} ${z} ${yaw} 0`)
    await t.espera(700)
    await t.capturar(staff, () => staff.chat('/gladiador arena spawn add'), 400)
  }
}

const KIT = ['diamond_helmet', 'diamond_chestplate', 'diamond_leggings', 'diamond_boots', 'iron_sword', 'shield']
const LUGAR = { helmet: 'head', chestplate: 'torso', leggings: 'legs', boots: 'feet' }

async function equipar(bot) {
  for (const item of bot.inventory.items()) {
    const tipo = Object.keys(LUGAR).find(k => item.name.endsWith(k))
    if (tipo) await bot.equip(item, LUGAR[tipo])
    else if (item.name === 'shield') await bot.equip(item, 'off-hand')
    else if (item.name.endsWith('_sword')) await bot.equip(item, 'hand')
  }
}

// Caça o vivo mais perto, com corrida, pulos e passos de lado de vez em quando, pra não parecer robô.
async function brigar(bot, todos, fim) {
  let vivo = true
  bot.once('death', () => { vivo = false })
  while (vivo && Date.now() < fim && naArena(bot)) {
    const alvos = todos.filter(o => o !== bot && o.vivo && o.entity)
      .map(o => bot.entities[o.entity.id] || Object.values(bot.entities).find(e => e.username === o.username))
      .filter(Boolean)
    const e = alvos.sort((a, b) => bot.entity.position.distanceTo(a.position) - bot.entity.position.distanceTo(b.position))[0]
    if (e) {
      const d = bot.entity.position.distanceTo(e.position)
      await bot.lookAt(e.position.offset(0, 1.6, 0), true)
      bot.setControlState('forward', d > 2.2)
      bot.setControlState('sprint', d > 4)
      bot.setControlState('jump', d > 5 && Math.random() < 0.15)
      const lado = Math.random()
      bot.setControlState('left', d < 4 && lado < 0.2)
      bot.setControlState('right', d < 4 && lado > 0.8)
      if (d < 3.2) bot.attack(e)
    }
    await t.espera(250 + Math.random() * 150)
  }
  bot.clearControlStates()
  bot.vivo = false
}

t.rodar(async () => {
  if (fs.existsSync(LUTAR)) fs.unlinkSync(LUTAR)
  const staff = await t.criarBot('BotStaff')
  await t.console('op BotStaff')
  await t.console('time set 1000 arenas')
  await definirPontos(staff, ANEL)
  staff.quit()
  await t.console('deop BotStaff')

  const bots = []
  try {
    for (const nome of NOMES) {
      const b = await t.criarBot(nome)
      b.vivo = true
      bots.push(b)
      await t.console(`gamemode survival ${nome}`)
      await t.console(`minecraft:clear ${nome}`)
      await t.console(`minecraft:effect clear ${nome}`)
      for (const item of KIT) await t.console(`minecraft:give ${nome} ${item}`)
    }
    await t.espera(1500)
    for (const b of bots) await equipar(b)

    await t.console('gladiador abrir individual 0')
    for (const b of bots) await t.capturar(b, () => b.chat('/gladiador entrar'), 400)
    console.log('\n>>> Bots inscritos. Crie o arquivo LUTAR pra iniciar o Gladiador.\n')
    while (!fs.existsSync(LUTAR)) await t.espera(500)
    fs.unlinkSync(LUTAR)

    await t.console('gladiador iniciar')
    await t.espera(3000)
    // na preparação (15 s) ninguém toma dano, então eles já podem se estranhar desde o começo
    const fim = Date.now() + 4 * 60 * 1000
    await Promise.all(bots.map(b => brigar(b, bots, fim)))
    console.log('\n>>> Briga encerrada. Esperando o vencedor voltar...')
    await t.espera(35000)
  } finally {
    const staff2 = await t.criarBot('BotStaff')
    await t.console('op BotStaff')
    await definirPontos(staff2, pontosOriginais())
    await t.console('deop BotStaff')
    await t.terminar(staff2, ...bots)
  }
})
