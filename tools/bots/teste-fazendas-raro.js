// Fazendas, fase 4: o achado raro. O teste põe a chance em 100% por safra no config do servidor
// (raro.horas-online bem pequeno) e devolve o valor no fim. Confere: a fazenda acha o item dela (o dono
// é avisado), guarda até ele pegar (holograma e menu), o item vem com o visual e o brilho, não entra em
// receita, e vende no Corretor Rural com o bônus do rank e do VIP, menos o imposto.
// Uso: node teste-fazendas-raro.js
const t = require('./lib')
const { Vec3 } = require('vec3')
const fs = require('fs')
const path = require('path')

const CELULA = 37
const OZ = 43
const CORRETOR = new Vec3(138.5, 65, OZ - 14 + 0.5)
const CONFIG = path.join(process.env.MC_TESTE || 'C:\\mc-teste', 'plugins', 'FazendasPlugin', 'config.yml')
const dinheiro = r => Number((r.match(/\$([\d,.]+)/) || [, 'NaN'])[1].replace(/,/g, ''))

const janela = (bot, fn) => new Promise(ok => {
  const fim = setTimeout(() => ok(null), 3000)
  bot.once('windowOpen', j => { clearTimeout(fim); setTimeout(() => ok(j), 800) })
  fn()
})

t.rodar(async () => {
  const original = fs.readFileSync(CONFIG, 'utf8')
  if (!/horas-online: [\d.]+/.test(original)) throw new Error('o config do servidor não tem raro.horas-online (é de uma versão antiga?)')
  fs.writeFileSync(CONFIG, original.replace(/horas-online: [\d.]+/, 'horas-online: 0.001'))
  await t.console('fazendas reload')
  let bot
  try {
    bot = await t.criarBot(t.nomeNovo('Rar'))
    const nome = bot.username
    await t.espera(1500)
    await t.console(`eco set ${nome} 100000`)
    await t.console(`minecraft:clear ${nome}`)
    await t.console(`ranks admin set ${nome} fazendeiro`)
    const tp = async (x, z) => { await t.console(`execute in minecraft:terrenos run minecraft:tp ${nome} ${x} 65 ${z} 0 30`); await t.espera(1200) }
    let msgs = await t.capturar(bot, () => bot.chat('/terreno auto'), 3000)
    const [gx, gz] = ((msgs.join(' ').match(/terreno (-?\d+;-?\d+)/) || [])[1] || '0;0').split(';').map(Number)
    const mx = gx * CELULA, mz = gz * CELULA
    await tp(mx + 16.5, mz + 12.5)
    msgs = await t.capturar(bot, () => bot.chat('/terreno comprar'), 1500)
    t.verificar(msgs.some(m => m.includes('comprado')), 'comprou um terreno', msgs[0])

    console.log('\n== Galinheiro ==')
    const [cx, cz] = [mx + 16, mz + 16]
    await t.console(`fazendas dar ${nome} galinheiro`)
    await tp(cx + 0.5, cz - 1.5)
    await bot.equip(bot.inventory.items().find(i => i.name === 'paper'), 'hand')
    const chao = bot.blockAt(new Vec3(cx, 64, cz))
    await bot.activateBlock(chao, new Vec3(0, 1, 0)).catch(() => {})
    await t.espera(800)
    msgs = await t.capturar(bot, () => bot.activateBlock(chao, new Vec3(0, 1, 0)).catch(() => {}), 1500)
    t.verificar(msgs.some(m => m.includes('construída')), 'o Galinheiro foi construído', msgs[0])
    await t.console(`fazendas dar ${nome} racao 16`)
    await tp(cx + 0.5, cz - 4.5)
    const npc = Object.values(bot.entities).find(e => e.name === 'mannequin' && e.position.distanceTo(new Vec3(cx + 0.5, 65, cz - 2.5)) < 1.5)
    await bot.equip(bot.inventory.items().find(i => i.name === 'paper'), 'hand')
    await t.capturar(bot, () => bot.activateEntity(npc), 1000)

    console.log('\n== Achados ==')
    msgs = await t.capturar(bot, () => t.console('fazendas acelerar 5'), 1500) // 5 safras, 100% de chance cada
    t.verificar(msgs.some(m => /achou 5x Ovo Dourado/.test(m)), 'a fazenda achou 5 Ovos Dourados e o dono foi avisado', msgs.find(m => m.includes('achou')))
    // o texto do holograma vem em pedaços (o console corta o texto inteiro com "...")
    let holo = ''
    for (let i = 0; i < 40; i++) {
      const r = await t.console(`execute in minecraft:terrenos positioned ${cx + 0.5} 67.9 ${cz - 2.5} run data get entity @e[type=text_display,limit=1,sort=nearest,distance=..1.5] text.extra[${i}].text`)
      const m = r.match(/entity data: "(.*)"$/)
      if (!m) break
      holo += m[1]
    }
    t.verificar(/5 achados raros/.test(holo), 'o holograma mostra os achados guardados', holo.slice(-80))
    let j = await janela(bot, () => bot.activateEntity(npc))
    const botao = j?.slots[13]
    t.verificar(botao?.name === 'paper' && /Achados raros: 5/.test(JSON.stringify(botao?.customName ?? botao?.nbt ?? '')), 'o menu mostra os 5 achados, com o visual do item',
      botao ? `${botao.name} ${JSON.stringify(botao.customName ?? '').slice(0, 80)}` : 'sem botão')
    if (j) {
      await bot.clickWindow(13, 0, 0)
      await t.espera(1200)
      bot.closeWindow(bot.currentWindow)
    }
    const ovos = Number(((await t.console(`minecraft:clear ${nome} minecraft:paper[minecraft:custom_data~{PublicBukkitValues:{"fazendasplugin:raro":"galinheiro"}}] 0`)).match(/Found (\d+)/) || [, 0])[1])
    t.verificar(ovos === 5, 'pegou os 5 Ovos Dourados no fazendeiro', `${ovos}`)
    const modelo = await t.console(`data get entity ${nome} Inventory[{components:{"minecraft:custom_data":{PublicBukkitValues:{"fazendasplugin:raro":"galinheiro"}}}}].components."minecraft:item_model"`)
    t.verificar(modelo.includes('minecraft:egg'), 'o Ovo Dourado tem o visual de um ovo (com brilho)', modelo.slice(-30))
    j = await janela(bot, () => bot.activateEntity(npc))
    t.verificar(j?.slots[13]?.name === 'gray_stained_glass_pane', 'depois de pegar, o menu fica sem achados')
    if (j) bot.closeWindow(bot.currentWindow)

    console.log('\n== Receita ==')
    // o achado é papel por baixo: na bancada, com papel normal, não vira livro nem nada
    await t.console(`give ${nome} minecraft:leather 1`)
    const mesa = new Vec3(cx + 3, 65, cz - 6)
    await t.console(`execute in minecraft:terrenos run setblock ${mesa.x} ${mesa.y} ${mesa.z} minecraft:crafting_table`)
    await t.espera(500)
    j = await janela(bot, () => bot.activateBlock(bot.blockAt(mesa)))
    let resultado = 'sem mesa'
    if (j) {
      const ovo = bot.inventory.items().find(i => i.name === 'paper')
      const couro = bot.inventory.items().find(i => i.name === 'leather')
      // livro: 3 papéis + 1 couro; com o Ovo Dourado no lugar dos papéis, não sai nada
      for (const [slot, item] of [[1, ovo], [2, ovo], [3, ovo], [4, couro]]) {
        await bot.clickWindow(j.slots.findIndex((s, k) => k >= 10 && s && s.type === item.type && s.name === item.name), 0, 0)
        await bot.clickWindow(slot, 1, 0) // solta um
        await bot.clickWindow(j.slots.findIndex((s, k) => k >= 10 && !s), 0, 0) // devolve o resto
        await t.espera(200)
      }
      await t.espera(500)
      resultado = bot.currentWindow?.slots[0]?.name || 'nada'
      bot.closeWindow(bot.currentWindow)
    }
    t.verificar(resultado === 'nada', 'o Ovo Dourado não entra em receita (3 dele + couro não dão livro)', resultado)
    await t.console(`execute in minecraft:terrenos run setblock ${mesa.x} ${mesa.y} ${mesa.z} minecraft:air`)

    console.log('\n== Venda no Corretor Rural ==')
    await t.console(`execute in minecraft:spawn run minecraft:tp ${nome} 138.5 65 ${OZ - 10.5} 180 0`)
    await t.espera(2000)
    const corretor = Object.values(bot.entities).find(e => e.name === 'mannequin' && e.position.distanceTo(CORRETOR) < 1)
    const antes = dinheiro(await t.console(`balance ${nome}`))
    j = corretor && await janela(bot, () => bot.activateEntity(corretor))
    t.verificar(j?.slots[49]?.name === 'gold_block', 'o Corretor mostra o botão de vender achados')
    if (j) {
      await bot.clickWindow(49, 0, 0)
      await t.espera(1200)
      bot.closeWindow(bot.currentWindow)
    }
    const recebeu = dinheiro(await t.console(`balance ${nome}`)) - antes
    // 5 × R$ 1.350 (1,5 h do Galinheiro), com o bônus do rank e menos 2% de imposto
    t.verificar(recebeu > 5 * 1350 * 0.95 && recebeu < 5 * 1350 * 1.3, 'vendeu os 5 por ~R$ 6.750', `+${recebeu.toFixed(2)}`)
    const sobrou = Number(((await t.console(`minecraft:clear ${nome} minecraft:paper 0`)).match(/Found (\d+)/) || [, 0])[1])
    t.verificar(sobrou === 0, 'e os itens saíram do inventário', `${sobrou}`)

    // limpeza: a staff tira a fazenda
    await t.console(`op ${nome}`)
    await tp(cx + 0.5, cz - 6.5)
    await bot.lookAt(new Vec3(cx + 0.5, 64.5, cz + 0.5))
    bot.chat('/fazendas remover')
    await t.espera(1000)
    await t.console(`deop ${nome}`)
  } finally {
    // devolve a chance de verdade (o terminar encerra o processo: tem de ser antes dele)
    fs.writeFileSync(CONFIG, original)
    await t.console('fazendas reload')
  }
  await t.terminar(bot)
})
