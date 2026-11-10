// Adaga (skill Crítico do SkillsPlugin): o Ferreiro transforma a espada da mão em Adaga e desfaz, cobrando
// pelo material; golpe com ela treina Crítico em vez de Espadas; o baú lendário dos tesouros tem a Adaga
// pronta (o mesmo item serializado que o chão do boss usa) e o item com o custom_data dela é reconhecido.
// Uso: node teste-adaga.js
const t = require('./lib')
const { Vec3 } = require('vec3')

const FERREIRO = new Vec3(14.5, 65, -24.5)
const SLOT_ADAGA = 47
const ADAGA_PRONTA = 'netherite_sword[custom_data={PublicBukkitValues:{"skillsplugin:adaga_critico":1b}}]'

// tira o nick antes de ler o número (o nick do bot pode ter dígitos)
const saldo = async nome => parseFloat((await t.console(`balance ${nome}`)).split(nome).join('').replace(/[^\d,.]/g, '').replace(/,/g, ''))
const texto = item => item ? JSON.stringify(item.components ?? item.nbt ?? '') + (item.customName ?? '') : ''
const ehAdaga = async nome => /adaga_critico/.test(await t.console(`data get entity ${nome} SelectedItem.components."minecraft:custom_data"`))

async function abrir(bot) {
  await t.console(`execute in minecraft:spawn run minecraft:tp ${bot.username} ${FERREIRO.x} 65 ${FERREIRO.z - 3} 0 0`)
  await t.espera(1500)
  const npc = Object.values(bot.entities).find(e => e.position.distanceTo(FERREIRO) < 1 && e.name === 'mannequin')
  if (!npc) return null
  return new Promise(ok => {
    const fim = setTimeout(() => ok(null), 3000)
    bot.once('windowOpen', janela => { clearTimeout(fim); ok(janela) })
    bot.activateEntity(npc)
  })
}

async function xpPorSkill(bot) {
  const msgs = await t.capturar(bot, () => bot.chat('/skills ver'), 1500)
  const xp = {}
  for (const m of msgs) {
    const r = m.match(/^(.+?) - Nível \d+ \(([\d.]+)\//)
    if (r) xp[r[1].trim()] = Number(r[2].replace(/\./g, ''))
  }
  return xp
}

// um golpe de 10 num zumbi parado; devolve quanto cada skill subiu
async function golpe(bot) {
  const antes = await xpPorSkill(bot)
  await t.console(`execute in minecraft:spawn run summon minecraft:zombie 14.5 65 -30.5 {Tags:["teste_adaga"],NoAI:1b,Health:20f}`)
  await t.espera(500)
  await t.console(`damage @e[tag=teste_adaga,limit=1] 10 minecraft:player_attack by ${bot.username}`)
  await t.espera(800)
  await t.console('minecraft:kill @e[tag=teste_adaga]')
  const depois = await xpPorSkill(bot)
  const ganho = {}
  for (const k of Object.keys(depois)) if (depois[k] - (antes[k] || 0) > 0) ganho[k] = depois[k] - (antes[k] || 0)
  return ganho
}

t.rodar(async () => {
  console.log('\n== Baú lendário ==')
  const loot = t.semCor(await t.console('tesouro loot lendario'))
  t.verificar(/adaga: Adaga/.test(loot), 'a Adaga pronta carrega no loot lendário (nome "Adaga")', loot.match(/adaga:[^\n-]*/)?.[0])

  const bot = await t.criarBot(t.nomeNovo('Adg'))
  const nome = bot.username
  await t.espera(1000)
  await t.console(`eco set ${nome} 100000`)

  console.log('\n== Ferreiro: transformar ==')
  await t.console(`minecraft:clear ${nome}`)
  await t.console(`minecraft:give ${nome} iron_sword[enchantments={sharpness:3}]`)
  await t.espera(500)
  bot.setQuickBarSlot(0)
  let janela = await abrir(bot)
  t.verificar(!!janela && janela.slots[SLOT_ADAGA]?.name === 'iron_sword' && /Transformar/.test(texto(janela.slots[SLOT_ADAGA])),
    'o Ferreiro mostra o botão "Transformar em Adaga"', texto(janela?.slots[SLOT_ADAGA]).slice(0, 160))
  if (janela) {
    const antes = await saldo(nome)
    const msgs = await t.capturar(bot, () => bot.clickWindow(SLOT_ADAGA, 0, 0), 1200)
    const depois = await saldo(nome)
    t.verificar(msgs.some(m => /agora é uma Adaga/.test(m)), 'mensagem de espada afiada', msgs.join(' | '))
    t.verificar(Math.round(antes - depois) === 2000, 'cobrou R$ 2.000 (ferro)', `${antes} → ${depois}`)
    bot.closeWindow(janela)
  }
  await t.espera(500)
  // o item inteiro passa do tamanho da resposta do RCON: um componente por vez
  const encantos = await t.console(`data get entity ${nome} SelectedItem.components."minecraft:enchantments"`)
  const dados = await t.console(`data get entity ${nome} SelectedItem.components."minecraft:custom_data"`)
  t.verificar(/adaga_critico/.test(dados) && /sharpness": 3/.test(encantos), 'a espada da mão virou Adaga e manteve a Afiação', `${dados} | ${encantos}`)

  console.log('\n== Golpe com a Adaga ==')
  let ganho = await golpe(bot)
  t.verificar(ganho['Crítico'] > 0 && !ganho['Espadas'], 'golpe com a Adaga treina Crítico, não Espadas', JSON.stringify(ganho))

  console.log('\n== Ferreiro: desfazer ==')
  janela = await abrir(bot)
  t.verificar(!!janela && /Desfazer/.test(texto(janela.slots[SLOT_ADAGA])), 'com a Adaga na mão o botão é "Desfazer"', texto(janela?.slots[SLOT_ADAGA]).slice(0, 160))
  if (janela) {
    const antes = await saldo(nome)
    const msgs = await t.capturar(bot, () => bot.clickWindow(SLOT_ADAGA, 0, 0), 1200)
    const depois = await saldo(nome)
    t.verificar(msgs.some(m => /desfez a Adaga/.test(m)) && Math.round(antes - depois) === 2000, 'desfez cobrando R$ 2.000', msgs.join(' | '))
    bot.closeWindow(janela)
  }
  await t.espera(500)
  t.verificar(!(await ehAdaga(nome)), 'a espada voltou ao normal')
  ganho = await golpe(bot)
  t.verificar(ganho['Espadas'] > 0 && !ganho['Crítico'], 'e volta a treinar Espadas', JSON.stringify(ganho))

  console.log('\n== Adaga pronta (a do chão do boss e do baú) ==')
  await t.console(`minecraft:clear ${nome}`)
  await t.console(`minecraft:give ${nome} ${ADAGA_PRONTA}`)
  await t.espera(500)
  bot.setQuickBarSlot(0)
  ganho = await golpe(bot)
  t.verificar(ganho['Crítico'] > 0 && !ganho['Espadas'], 'o item com o custom_data da Adaga treina Crítico', JSON.stringify(ganho))
  janela = await abrir(bot)
  t.verificar(!!janela && /Desfazer/.test(texto(janela.slots[SLOT_ADAGA])), 'e o Ferreiro reconhece como Adaga (R$ 20.000 netherite)', texto(janela?.slots[SLOT_ADAGA]).slice(0, 200))
  if (janela) bot.closeWindow(janela)

  console.log('\n== Sem espada ==')
  await t.console(`minecraft:clear ${nome}`)
  await t.console(`minecraft:give ${nome} diamond_pickaxe`)
  await t.espera(500)
  janela = await abrir(bot)
  if (janela) {
    const antes = await saldo(nome)
    const msgs = await t.capturar(bot, () => bot.clickWindow(SLOT_ADAGA, 0, 0), 1200)
    t.verificar(msgs.some(m => /Segure uma espada/.test(m)) && (await saldo(nome)) === antes, 'picareta na mão: recusa sem cobrar', msgs.join(' | '))
    bot.closeWindow(janela)
  }

  await t.terminar(bot)
})
