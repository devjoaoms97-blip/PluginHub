// Modelos 3D dos bosses (2026-11-17): cada um dos 6 bosses (menos o Wither) nasce com o modelo do
// FreeMinecraftModels por cima (o mob fica invisível), e o modelo sai quando o boss sai. Os modelos estão
// no pacote do FMM. Precisa dos .bbmodel em plugins/FreeMinecraftModels/models (tools/pacote/bosses).
// Uso: node teste-boss-modelos.js
const fs = require('fs')
const path = require('path')
const t = require('./lib')

const PASTA = process.env.MC_TESTE || 'C:\\mc-teste'
const BOSSES = { zombie: 'boss_tita', skeleton: 'boss_carrasco', spider: 'boss_rainha', zombified_piglin: 'boss_acougueiro', creeper: 'boss_estilhaco', witch: 'boss_feiticeira' }

t.rodar(async () => {
  // um jogador por perto: sem ninguém, o boss pode não ser carregado no chunk da arena
  const bot = await t.criarBot(t.nomeNovo('Bmod'))
  await t.console(`gamemode spectator ${bot.username}`)
  await t.console(`execute in minecraft:spawn run minecraft:tp ${bot.username} 0.5 60 195.5`)
  await t.espera(2000)
  await t.console('boss stop')

  console.log('\n== Os modelos estão no pacote do FMM ==')
  const zip = fs.readFileSync(path.join(PASTA, 'plugins', 'FreeMinecraftModels', 'output', 'FreeMinecraftModels.zip')).toString('latin1')
  for (const modelo of Object.values(BOSSES)) {
    t.verificar(zip.includes(`models/${modelo}/`), `o pacote tem o modelo ${modelo}`)
  }

  console.log('\n== Cada boss nasce com o modelo ==')
  for (const [id, modelo] of Object.entries(BOSSES)) {
    await t.console(`boss start ${id}`)
    await t.espera(3000)
    const r = await t.console('boss modelo')
    t.verificar(/com o modelo 3D/.test(r), `o boss ${id} está com o ${modelo}`, r)
    await t.console('boss stop')
    await t.espera(1500)
  }

  console.log('\n== O Wither fica como é ==')
  await t.console('boss start wither')
  await t.espera(3000)
  const w = await t.console('boss modelo')
  t.verificar(/sem modelo/.test(w), 'o Wither continua sem modelo', w)
  await t.console('boss stop')

  await t.console(`gamemode survival ${bot.username}`)
  await t.terminar(bot)
})
