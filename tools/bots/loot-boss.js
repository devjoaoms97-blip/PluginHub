// Cadastra o loot do boss (BossPlugin) do zero: apaga o que tinha e põe cada item na mão de um bot op,
// que roda o /boss additem (chão, chance em %) ou o /boss addchampionitem (campeão, peso). Itens de PvP
// no lugar de totem e garrafa de XP; a armadura vem com Proteção V, acima do máximo do jogo (decisões
// de 2026-11-06). O Mapa do Tesouro não entra aqui: o TesourosPlugin já faz todo boss deixar 1.
// Uso (servidor ligado): node loot-boss.js
const t = require('./lib')

const ARMADURA = 'protection:5,unbreaking:3'

// [item com componentes, chance (%) ou peso]
const CHAO = [
  [`netherite_helmet[enchantments={${ARMADURA}}]`, 8],
  [`netherite_chestplate[enchantments={${ARMADURA}}]`, 8],
  [`netherite_leggings[enchantments={${ARMADURA}}]`, 8],
  [`netherite_boots[enchantments={${ARMADURA},feather_falling:4}]`, 8],
  ['netherite_sword[enchantments={sharpness:5,unbreaking:3,looting:3}]', 12],
  ['netherite_axe[enchantments={sharpness:5,unbreaking:3,efficiency:5}]', 12],
  ['bow[enchantments={power:5,unbreaking:3,flame:1}]', 10],
  ['golden_apple 8', 50],
  ['diamond 32', 40],
  ['netherite_ingot 2', 15],
  ['enchanted_golden_apple 1', 15],
  ['enchanted_book[stored_enchantments={mending:1}]', 10],
]

const CAMPEAO = [
  [`netherite_chestplate[enchantments={${ARMADURA},mending:1}]`, 25],
  ['netherite_sword[enchantments={sharpness:5,fire_aspect:2,looting:3,unbreaking:3,mending:1}]', 25],
  ['netherite_axe[enchantments={sharpness:5,unbreaking:3,mending:1}]', 20],
  ['enchanted_golden_apple 2', 20],
  ['elytra[enchantments={unbreaking:3}]', 10],
]

t.rodar(async () => {
  const bot = await t.criarBot(t.nomeNovo('Bloot'), { comandos: true })
  const nome = bot.username
  await t.console(`op ${nome}`)
  await t.espera(500)
  let msgs = await t.capturar(bot, () => bot.chat('/boss limparloot'))
  t.verificar(msgs.some(m => m.includes('apagados')), 'o loot antigo foi apagado', msgs[0])

  const cadastrar = async (lista, comando, aceito) => {
    let ok = 0
    for (const [item, valor] of lista) {
      const [id, qtd = 1] = item.split(' ')
      const r = await t.console(`minecraft:item replace entity ${nome} weapon.mainhand with ${id} ${qtd}`)
      if (/error|erro|unknown|desconhecid/i.test(r)) {
        console.log(`  ✘ ${item}: ${r}`)
        continue
      }
      await t.espera(300)
      const m = await t.capturar(bot, () => bot.chat(`/boss ${comando} ${valor}`), 800)
      if (m.some(x => x.includes(aceito))) ok++
      else console.log(`  ✘ ${item}: ${m.join(' | ')}`)
    }
    return ok
  }

  console.log('\n== Chão ==')
  const chao = await cadastrar(CHAO, 'additem', 'loot do chão')
  t.verificar(chao === CHAO.length, `${chao} de ${CHAO.length} itens no chão`)
  console.log('\n== Campeão ==')
  const campeao = await cadastrar(CAMPEAO, 'addchampionitem', 'prêmios do campeão')
  t.verificar(campeao === CAMPEAO.length, `${campeao} de ${CAMPEAO.length} itens no sorteio do campeão`)

  await t.console(`minecraft:item replace entity ${nome} weapon.mainhand with air`)
  await t.console(`deop ${nome}`)
  await t.terminar(bot)
})
