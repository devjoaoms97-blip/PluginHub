// Oficinas da mina (OficinasPlugin + estrutura "oficinas"): os 4 NPCs nas barraquinhas da entrada da
// avenida das minas. Ferreiro repara cobrando, a Bruxa abre a mesa no nível 30, o Picareteiro vende
// picareta e o Sábio vende XP. Também confere a scoreboard lateral (PlacarPlugin).
// Uso: node teste-oficinas.js   (depois do "node montar-mapa.js construir oficinas")
const t = require('./lib')
const { Vec3 } = require('vec3')

const NPCS = {
  ferreiro: new Vec3(14.5, 65, -24.5),
  picareteiro: new Vec3(25.5, 65, -24.5),
  bruxa: new Vec3(-13.5, 65, -24.5),
  sabio: new Vec3(-24.5, 65, -24.5),
}

const saldo = async nome => parseFloat((await t.console(`balance ${nome}`)).replace(/[^\d,.]/g, '').replace(/,/g, ''))

/** Vai até a frente do balcão, clica no NPC e espera a janela abrir. */
async function abrir(bot, onde) {
  await t.console(`execute in minecraft:spawn run minecraft:tp ${bot.username} ${onde.x} 65 ${onde.z - 3} 0 0`)
  await t.espera(1500)
  const npc = Object.values(bot.entities).find(e => e.position.distanceTo(onde) < 1 && e.name === 'mannequin')
  if (!npc) return null
  return new Promise(ok => {
    const fim = setTimeout(() => ok(null), 3000)
    bot.once('windowOpen', janela => { clearTimeout(fim); ok(janela) })
    bot.activateEntity(npc)
  })
}

t.rodar(async () => {
  const lista = await t.console('oficina lista')
  t.verificar(['ferreiro', 'picareteiro', 'bruxa', 'sabio'].every(id => lista.includes(id)), 'as 4 oficinas cadastradas', lista.slice(0, 160))

  const bot = await t.criarBot(t.nomeNovo('Ofi'))
  await t.espera(1000)
  await t.console(`eco set ${bot.username} 100000`)

  console.log('\n== Ferreiro ==')
  await t.console(`minecraft:clear ${bot.username}`)
  await t.console(`minecraft:give ${bot.username} diamond_pickaxe[damage=1200]`)
  let janela = await abrir(bot, NPCS.ferreiro)
  t.verificar(!!janela && janela.slots[0]?.name === 'diamond_pickaxe', 'o ferreiro mostra a picareta gasta', janela?.title)
  if (janela) {
    // o saldo não serve aqui: o reparo pode concluir a missão diária de reparar, que paga mais que ele
    const msgs = await t.capturar(bot, () => bot.clickWindow(0, 0, 0), 1200)
    const dano = await t.console(`data get entity ${bot.username} Inventory[{id:"minecraft:diamond_pickaxe"}].components."minecraft:damage"`)
    t.verificar(!/\d+$/.test(dano.trim()) || / 0$/.test(dano.trim()), 'a picareta saiu reparada', dano)
    t.verificar(msgs.some(m => /reparou 1 item\(ns\) por R\$ [1-9]/.test(m)), 'e o reparo foi cobrado', msgs.join(' | '))
    bot.closeWindow(janela)
  }

  console.log('\n== Picareteiro ==')
  janela = await abrir(bot, NPCS.picareteiro)
  t.verificar(!!janela && janela.slots[1]?.name === 'iron_pickaxe', 'o picareteiro mostra as picaretas', janela?.slots.slice(0, 4).map(s => s?.name).join(','))
  if (janela) {
    const antes = await saldo(bot.username)
    await bot.clickWindow(1, 0, 0)
    await t.espera(1200)
    const tem = bot.inventory.items().some(i => i.name === 'iron_pickaxe')
    const depois = await saldo(bot.username)
    t.verificar(tem && depois < antes, 'comprou a picareta de ferro', `${antes} → ${depois}`)
    bot.closeWindow(janela)
  }

  console.log('\n== Sábio ==')
  await t.console(`minecraft:xp set ${bot.username} 0 levels`)
  janela = await abrir(bot, NPCS.sabio)
  t.verificar(!!janela && janela.slots[10]?.name === 'experience_bottle', 'o sábio mostra os pacotes de XP', janela?.title)
  if (janela) {
    await bot.clickWindow(11, 0, 0) // +5 níveis
    await t.espera(1200)
    t.verificar(bot.experience.level === 5, 'comprou 5 níveis', `nível ${bot.experience.level}`)
    bot.closeWindow(janela)
  }

  console.log('\n== Bruxa ==')
  // sem lápis no inventário: a bruxa põe o dela
  await t.console(`minecraft:xp set ${bot.username} 30 levels`)
  await t.console(`minecraft:clear ${bot.username} lapis_lazuli`)
  const custos = []
  const ouvir = pacote => { if (pacote.property <= 2) custos[pacote.property] = pacote.value }
  bot._client.on('craft_progress_bar', ouvir)
  janela = await abrir(bot, NPCS.bruxa)
  t.verificar(!!janela && /enchant/.test(janela.type), 'a bruxa abre a mesa de encantamento', janela?.type)
  if (janela) {
    const picareta = bot.inventory.items().find(i => i.name === 'iron_pickaxe')
    if (picareta) {
      await bot.clickWindow(picareta.slot - bot.inventory.inventoryStart + janela.inventoryStart, 0, 0)
      await bot.clickWindow(0, 0, 0)
      await t.espera(1500)
    }
    t.verificar(Math.max(...custos.filter(Number.isFinite)) === 30, 'com o poder máximo (30 na terceira opção)', JSON.stringify(custos))
    t.verificar(janela.slots[1]?.name === 'lapis_lazuli', 'o lápis já vem na mesa (o jogador não precisa ter)', janela.slots[1]?.name)
    // o botão de encantar é um pacote próprio (não é clique em slot): 3ª opção, a de 30 níveis
    bot._client.write('enchant_item', { windowId: janela.id, enchantment: 2 })
    await t.espera(1500)
    const encantada = bot.inventory.slots.concat(janela.slots).some(i => i?.name === 'iron_pickaxe' && i.enchants?.length)
    t.verificar(encantada || bot.experience.level < 30, 'encantou sem lápis, gastando só o XP', `nível ${bot.experience.level}`)
    bot.closeWindow(janela)
    await t.espera(800)
    const lapis = bot.inventory.items().filter(i => i.name === 'lapis_lazuli').length
    t.verificar(lapis === 0, 'ao fechar, o lápis da bruxa não vai pro inventário', `${lapis} pilha(s)`)
  }
  bot._client.removeListener('craft_progress_bar', ouvir)

  console.log('\n== Placar ==')
  // o bot não lê o nome customizado das linhas: a staff vê as linhas como o jogador vê
  const placar = t.semCor(await t.console(`placar ver ${bot.username}`))
  t.verificar(/Saldo: R\$/.test(placar) && /Rank:/.test(placar) && /Online: \d+/.test(placar) && /Próximo boss: \S/.test(placar),
    'a scoreboard lateral com saldo, rank, boss e online', placar.replace(/\s+/g, ' ').slice(0, 220))

  await t.terminar(bot)
})
