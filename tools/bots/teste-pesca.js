// Loot próprio da pesca (SkillsPlugin, pescador.loot): o bot pesca de verdade num tanque com a chance
// em 100% (a pesca vem especial e troca o que o anzol puxou), e com a pesca parada valendo 0 (a segunda
// pesca do mesmo lugar, sem mexer a câmera, vem normal; mexendo, volta a vir especial). Depois: /peixes,
// /peixes vender, o Baú do Mar e os itens de outros plugins (fragmento, spawner, máquina, combustível).
// Põe um pescador.loot de teste no config do servidor e devolve o original no fim.
// Uso: node teste-pesca.js
const fs = require('fs')
const path = require('path')
const t = require('./lib')

const CONFIG = path.join(process.env.MC_TESTE || 'C:\\mc-teste', 'plugins', 'SkillsPlugin', 'config.yml')
const JAR_CONFIG = path.join(__dirname, '..', '..', 'skills', 'skills-plugin', 'src', 'main', 'resources', 'config.yml')
const TANQUE = { x: 300, y: 120, z: 300 }

const saldo = async nome => parseFloat((await t.console(`balance ${nome}`)).split(nome).join('').replace(/[^\d,.]/g, '').replace(/,/g, ''))

/** O bloco "loot:" do config do jar, com a chance e a pesca parada do teste. */
function lootDeTeste() {
  const jar = fs.readFileSync(JAR_CONFIG, 'utf8').replace(/\r\n/g, '\n')
  const inicio = jar.indexOf('\n  loot:\n') + 1
  const fim = jar.indexOf('\n\n', inicio)
  return jar.slice(inicio, fim)
    .replace(/chance-base: [\d.]+/, 'chance-base: 1.0')
    .replace(/minutos: [\d.]+/, 'minutos: 0')
    .replace(/fator: [\d.]+/, 'fator: 0')
}

/** Lança e espera puxar alguma coisa (até 40 s). Devolve as mensagens do chat do meio tempo. */
async function pescar(bot) {
  const inicio = bot.mensagens.length
  try {
    await Promise.race([bot.fish(), t.espera(40000).then(() => { throw new Error('nada fisgou em 40 s') })])
  } catch (e) {
    console.log('  pesca:', e.message)
    bot.activateItem() // recolhe a linha
  }
  await t.espera(1500)
  return bot.mensagens.slice(inicio)
}

t.rodar(async () => {
  const original = fs.readFileSync(CONFIG, 'utf8')
  let bot
  try {
    fs.writeFileSync(CONFIG, original.replace(/\s*$/, '\n') + '\npescador:\n' + lootDeTeste() + '\n')
    await t.console('skills admin reload')

    const { x, y, z } = TANQUE
    bot = await t.criarBot(t.nomeNovo('Pesc'))
    const nome = bot.username
    await t.espera(1000)
    await t.console(`eco set ${nome} 0`)
    await t.console(`minecraft:clear ${nome}`)
    await t.console(`minecraft:give ${nome} fishing_rod[enchantments={lure:3,unbreaking:3}]`)
    // tanque de água de 5x5, 3 de fundo, com borda de pedra (depois do tp: antes, o chunk não está carregado)
    await t.console(`minecraft:effect give ${nome} slow_falling 10 0 true`)
    await t.console(`execute in minecraft:spawn run minecraft:tp ${nome} ${x + 0.5} ${y + 2} ${z - 2.5} 0 30`)
    await t.espera(2000)
    await t.console(`execute in minecraft:spawn run fill ${x - 4} ${y - 4} ${z - 4} ${x + 4} ${y + 4} ${z + 4} air`)
    await t.console(`execute in minecraft:spawn run fill ${x - 3} ${y - 4} ${z - 3} ${x + 3} ${y - 1} ${z + 3} stone`)
    await t.console(`execute in minecraft:spawn run fill ${x - 2} ${y - 3} ${z - 2} ${x + 2} ${y - 1} ${z + 2} water`)
    await t.console(`execute in minecraft:spawn run minecraft:tp ${nome} ${x + 0.5} ${y} ${z - 2.5} 0 30`)
    await t.espera(1500)
    await bot.equip(bot.inventory.items().find(i => i.name === 'fishing_rod'), 'hand')

    console.log('\n== Pesca especial (chance 100%) ==')
    let msgs = await pescar(bot)
    t.verificar(msgs.some(m => /Pesca .+! Você fisgou/.test(m)), 'a pesca veio especial', msgs.filter(m => /Pesca|fisgou/.test(m)).join(' | '))
    await t.espera(1000)
    const especiais = bot.inventory.items().filter(i => i.customName || i.name === 'enchanted_book')
    t.verificar(especiais.length > 0, 'e o item especial chegou no inventário', bot.inventory.items().map(i => i.name).join(','))

    console.log('\n== Pesca parada (fator 0) ==')
    msgs = await pescar(bot)
    t.verificar(!msgs.some(m => /Você fisgou/.test(m)), 'do mesmo lugar e sem mexer a câmera: pesca normal', msgs.filter(m => /fisgou/.test(m)).join(' | '))
    await bot.look(bot.entity.yaw + 0.2, bot.entity.pitch, true)
    msgs = await pescar(bot)
    t.verificar(msgs.some(m => /Você fisgou/.test(m)), 'mexendo a câmera, volta a vir especial', msgs.filter(m => /fisgou/.test(m)).join(' | '))

    console.log('\n== /peixes e /peixes vender ==')
    await t.console(`minecraft:clear ${nome}`)
    await t.console(`peixes dar ${nome} peixe:brasa 2`)
    await t.console(`peixes dar ${nome} peixe:lambari 3`)
    await t.console(`minecraft:give ${nome} cod 5`)
    await t.espera(500)
    msgs = await t.capturar(bot, () => bot.chat('/peixes'))
    t.verificar(msgs.some(m => /Peixe-Brasa vale R\$ 25\.000 \(você tem 2\)/.test(m)), '/peixes lista os peixes com preço e quantos tem', msgs.join(' | ').slice(0, 300))
    t.verificar(msgs.some(m => /chance de pesca especial: [\d,]+%/.test(m)), 'e a chance de pesca especial', msgs.find(m => /chance/.test(m)))
    msgs = await t.capturar(bot, () => bot.chat('/peixes vender'))
    t.verificar(msgs.some(m => /R\$ 50\.900/.test(m)) && (await saldo(nome)) === 50900, 'vendeu 2 Peixe-Brasa + 3 Lambari por R$ 50.900', msgs.join(' | '))
    t.verificar(bot.inventory.items().some(i => i.name === 'cod' && !i.customName && i.count === 5), 'o bacalhau comum ficou')

    console.log('\n== Baú do Mar ==')
    await t.console(`eco set ${nome} 0`)
    await t.console(`peixes dar ${nome} bau:lendario`)
    await t.espera(500)
    await bot.equip(bot.inventory.items().find(i => i.name === 'chest'), 'hand')
    const antesItens = bot.inventory.items().length
    msgs = await t.capturar(bot, async () => { bot.activateItem(); await t.espera(200); bot.deactivateItem() }, 1500)
    const coins = await saldo(nome)
    t.verificar(msgs.some(m => /abriu o Baú do Mar Lendário/.test(m)), 'abriu o Baú do Mar Lendário', msgs.join(' | ').slice(0, 300))
    t.verificar(coins >= 25000 && coins <= 40000, 'deu entre R$ 25 mil e 40 mil', coins)
    t.verificar(!bot.inventory.items().some(i => i.name === 'chest') && bot.inventory.items().length >= antesItens + 1, 'gastou o baú e deu os itens',
      bot.inventory.items().map(i => i.name + 'x' + i.count).join(','))
    const bloco = await t.console(`execute in minecraft:spawn if block ${x} ${y} ${z - 2} chest`)
    t.verificar(/failed/i.test(bloco), 'o baú não foi colocado no chão', bloco)

    console.log('\n== Itens dos outros plugins ==')
    for (const item of ['fragmento', 'combustivel', 'spawner:blaze', 'maquina:lapidadora', 'vara', 'adaga', 'livro:mending:1']) {
      const r = t.semCor(await t.console(`peixes dar ${nome} ${item} 2`))
      t.verificar(/^Deu /.test(r), `/peixes dar ${item}`, r)
    }
    const vara = await t.console(`data get entity ${nome} Inventory[{id:"minecraft:fishing_rod"}].components."minecraft:custom_data"`)
    t.verificar(/vara_pescador/.test(vara), 'a Vara do Pescador tem a marca', vara)
    await t.console(`minecraft:clear ${nome}`)
    await t.console(`peixes dar ${nome} vara`)
    await t.espera(500)
    await bot.equip(bot.inventory.items().find(i => i.name === 'fishing_rod'), 'hand')
    msgs = await t.capturar(bot, () => bot.chat('/peixes'))
    const comVara = Number((msgs.find(m => /chance/.test(m)) || '').match(/([\d,]+)%/)?.[1].replace(',', '.'))
    t.verificar(comVara >= 125, 'com a Vara do Pescador a chance sobe 25%', comVara)

  } finally {
    // o t.terminar encerra o processo: o config volta antes dele, inclusive se o teste der erro
    fs.writeFileSync(CONFIG, original)
    await t.console('skills admin reload')
    await t.console(`execute in minecraft:spawn run fill ${TANQUE.x - 4} ${TANQUE.y - 4} ${TANQUE.z - 4} ${TANQUE.x + 4} ${TANQUE.y + 4} ${TANQUE.z + 4} air`)
  }
  await t.terminar(bot)
})
