// O foco de 2026-10-27: Bancário (folha de cheque, cheque saindo do saldo, depósito com taxa,
// cancelamento com devolução e a Mine-sena), Cambista (fragmentos por encantamento, só nos itens que fazem sentido),
// Compactador, achados e perdidos, teleporte por comando barrado nas arenas, prestígio
// de skill com a skill em uso no placar, parry com escudo e Boss elite.
// Uso: node teste-foco.js   (depois do "node montar-mapa.js construir oficinas")
const t = require('./lib')
const { Vec3 } = require('vec3')

// NPCs (mundo spawn) e de onde o bot fala com cada um: [onde o NPC está, onde o bot fica, yaw do bot]
// o Bancário fica no Banco do fim da avenida, em cima da plataforma (um degrau acima do chão)
const BANCARIO = [new Vec3(0.5, 66, -179.5), [0.5, -177.5], 180, 66]
const CAMBISTA = [new Vec3(14.5, 65, -180.5), [14.5, -177.5], 180]
const COMPACTADOR = [new Vec3(-13.5, 65, -180.5), [-13.5, -177.5], 180]

// o nome do bot tem números: o valor é o que vem depois do "$"
const saldo = async nome => parseFloat((await t.console(`balance ${nome}`)).split('$').pop().replace(/[^\d.]/g, ''))
const nomeDo = slot => (slot ? JSON.stringify(slot.customName ?? slot.components ?? slot.nbt ?? '') : '')
// O inventário que o Mineflayer enxerga fica pra trás depois de um menu: contar e segurar vão pelo console.
const FOLHA = 'paper[custom_data={PublicBukkitValues:{"chequeplugin:folha":1b}}]'
const CHEQUE = 'paper[custom_data~{PublicBukkitValues:{"chequeplugin:cheque_valor":1000.0d}}]'
async function quantos(nome, item) {
  const r = await t.console(`minecraft:clear ${nome} ${item} 0`)
  return /No items/i.test(r) ? 0 : Number((r.match(/(\d+)/) || [, 0])[1])
}
/** Põe o item na mão (slot 0 da barra, que o bot deixa selecionado). */
const naMao = (nome, item) => t.console(`minecraft:item replace entity ${nome} hotbar.0 with ${item}`)

/** Vai até o NPC e clica nele. Com janela = true, espera o menu abrir e devolve a janela. */
async function falar(bot, [onde, [x, z], yaw, y = 65], janela = true) {
  await t.console(`execute in minecraft:spawn run minecraft:tp ${bot.username} ${x} ${y} ${z} ${yaw} 0`)
  await t.espera(1500)
  const npc = Object.values(bot.entities).find(e => e.position.distanceTo(onde) < 1 && e.name === 'mannequin')
  if (!npc) return null
  if (!janela) {
    bot.activateEntity(npc)
    return npc
  }
  return new Promise(ok => {
    const fim = setTimeout(() => ok(null), 3000)
    bot.once('windowOpen', j => { clearTimeout(fim); ok(j) })
    bot.activateEntity(npc)
  })
}

t.rodar(async () => {
  const bot = await t.criarBot(t.nomeNovo('Foco'))
  const nome = bot.username
  bot.setQuickBarSlot(0)
  await t.console(`eco set ${nome} 100000`)
  await t.console(`minecraft:clear ${nome}`)

  console.log('\n== Bancário: folha de cheque ==')
  let janela = await falar(bot, BANCARIO)
  t.verificar(!!janela && janela.slots[10]?.name === 'paper' && janela.slots[15] != null, 'o Bancário abre o balcão (folha e depósito)', janela?.title)
  if (janela) {
    await bot.clickWindow(10, 0, 0)
    await t.espera(1000)
    bot.closeWindow(janela)
  }
  t.verificar(await saldo(nome) === 99950, 'a folha custou R$ 50', String(await saldo(nome)))
  t.verificar(await quantos(nome, FOLHA) === 1, 'e veio pro inventário')

  console.log('\n== Cheque ==')
  await t.console(`minecraft:clear ${nome}`)
  await naMao(nome, 'paper') // papel comum não serve: só a folha
  let msgs = await t.capturar(bot, () => bot.chat('/cheque 1000'))
  t.verificar(msgs.some(m => /folha de cheque/i.test(m)), 'com papel comum o /cheque recusa', msgs.join(' | '))
  await naMao(nome, FOLHA)
  msgs = await t.capturar(bot, () => bot.chat('/cheque 1000'))
  t.verificar(msgs.some(m => /criado com sucesso/i.test(m)), 'com a folha, o cheque sai', msgs.join(' | '))
  t.verificar(await saldo(nome) === 98950, 'e os R$ 1.000 saíram do saldo', String(await saldo(nome)))
  t.verificar(await quantos(nome, CHEQUE) === 1, 'e o cheque de R$ 1.000 está no inventário')
  msgs = await t.capturar(bot, () => bot.activateItem())
  t.verificar(msgs.some(m => /Bancário/.test(m)), 'o clique direito no cheque manda ao Bancário', msgs.join(' | '))

  janela = await falar(bot, BANCARIO)
  if (janela) {
    await bot.clickWindow(15, 0, 0)
    await t.espera(1200)
    bot.closeWindow(janela)
  }
  t.verificar(await saldo(nome) === 99940, 'o depósito no Bancário devolve o valor menos 1%', String(await saldo(nome)))
  t.verificar(await quantos(nome, CHEQUE) === 0, 'e o cheque some do inventário')

  // cancelar devolve pra quem emitiu (a folha consumida libera o slot da mão, e o cheque cai nele)
  await t.espera(10000) // o intervalo entre um cheque e outro (limite.cooldown-segundos)
  await naMao(nome, FOLHA)
  await t.capturar(bot, () => bot.chat('/cheque 500'))
  msgs = await t.capturar(bot, () => bot.chat('/cheque cancelar'))
  t.verificar(msgs.some(m => /cancelado/i.test(m)) && await saldo(nome) === 99940, 'cancelar o cheque devolve os R$ 500',
    `${await saldo(nome)} | ${msgs.join(' | ')}`)

  console.log('\n== Mine-sena ==')
  // o pote pode ter sobra de outra rodada: o prêmio é o pote de antes mais as apostas daqui
  const poteDe = r => parseFloat((r.match(/Pote: R\$ ([\d.,]+)/) || [, '0'])[1].replace(/\./g, '').replace(',', '.'))
  await t.console('minesena sortear 100') // zera as apostas pendentes (ninguém joga no 100 aqui)
  const poteAntes = poteDe(await t.console('minesena'))
  await t.console(`eco set ${nome} 100000`)
  janela = await falar(bot, BANCARIO)
  t.verificar(!!janela && janela.slots[13]?.name === 'sunflower', 'o Bancário tem o botão da Mine-sena', janela?.slots[13]?.name)
  if (janela) {
    const lotes = await new Promise(ok => {
      const fim = setTimeout(() => ok(null), 3000)
      bot.once('windowOpen', j => { clearTimeout(fim); ok(j) })
      bot.clickWindow(13, 0, 0)
    })
    t.verificar(!!lotes && lotes.slots[0]?.name === 'white_stained_glass_pane' && lotes.slots[44] != null && lotes.slots[53]?.name === 'arrow',
      'abre os números (1 a 45 na primeira página, com a seta pra próxima)', lotes?.slots[0]?.name)
    if (lotes) {
      // nº 7 duas vezes e nº 8 uma: o menu reabre a cada compra, então espera a janela nova
      for (const slot of [6, 6, 7]) {
        const proxima = new Promise(ok => { const fim = setTimeout(ok, 2000); bot.once('windowOpen', () => { clearTimeout(fim); ok() }) })
        await bot.clickWindow(slot, 0, 0)
        await proxima
        await t.espera(300)
      }
      bot.closeWindow(bot.currentWindow)
    }
  }
  t.verificar(await saldo(nome) === 98500, '3 bilhetes de R$ 500 saíram do saldo', String(await saldo(nome)))
  let info = await t.console('minesena')
  t.verificar(Math.abs(poteDe(info) - (poteAntes + 1500)) < 0.01, 'e foram pro pote', `${poteAntes} → ${poteDe(info)}`)
  msgs = await t.capturar(bot, () => t.console('minesena sortear 7'), 1500)
  t.verificar(msgs.some(m => /MINE-SENA/.test(m) && /\b7\b/.test(m)), 'o sorteio é anunciado no chat', msgs.join(' | ').slice(0, 160))
  t.verificar(msgs.some(m => /Você ganhou na Mine-sena/.test(m)), 'e o ganhador é avisado')
  t.verificar(Math.abs(await saldo(nome) - (98500 + poteAntes + 1500)) < 0.02, 'os 2 bilhetes no 7 levam o pote inteiro (o do 8 não leva nada)',
    `${await saldo(nome)} (pote ${poteAntes + 1500})`)
  info = await t.console('minesena')
  t.verificar(poteDe(info) === 0, 'o pote zera depois de pago', info.slice(0, 80))
  // sem ganhador: acumula
  janela = await falar(bot, BANCARIO)
  if (janela) {
    const lotes = await new Promise(ok => {
      const fim = setTimeout(() => ok(null), 3000)
      bot.once('windowOpen', j => { clearTimeout(fim); ok(j) })
      bot.clickWindow(13, 0, 0)
    })
    if (lotes) {
      const proxima = new Promise(ok => { const fim = setTimeout(ok, 2000); bot.once('windowOpen', () => { clearTimeout(fim); ok() }) })
      await bot.clickWindow(0, 0, 0) // nº 1
      await proxima
      bot.closeWindow(bot.currentWindow)
    }
  }
  msgs = await t.capturar(bot, () => t.console('minesena sortear 2'), 1500)
  info = await t.console('minesena')
  t.verificar(msgs.some(m => /Ninguém acertou/.test(m)) && poteDe(info) === 500, 'sem ganhador, o pote acumula', `${poteDe(info)} | ${msgs.join(' | ').slice(0, 120)}`)
  await t.console('minesena sortear 1') // devolve o pote pro bot e deixa limpo pra próxima rodada

  console.log('\n== Cambista ==')
  await t.console(`eco set ${nome} 10000000`)
  await t.console(`minecraft:clear ${nome}`)
  await naMao(nome, 'diamond_pickaxe[enchantments={"minecraft:efficiency":5}]')
  await t.console(`minecraft:give ${nome} paper[custom_data={PublicBukkitValues:{"rankupplugin:fragmento":1b}}] 64`)
  await t.espera(500)
  janela = await falar(bot, CAMBISTA)
  const ofertas = j => j ? j.slots.slice(9, 27).filter(Boolean).map(nomeDo).join(' ') : ''
  let texto = ofertas(janela)
  t.verificar(/Efici/.test(texto) && /Fortuna/.test(texto) && /Inquebr/.test(texto), 'na picareta: Eficiência, Fortuna e Inquebrável', texto.slice(0, 200))
  t.verificar(!/Afia/.test(texto) && !/Saque/.test(texto) && !/Prote/.test(texto), 'e nada de Afiação, Saque nem Proteção na picareta')
  t.verificar(/64/.test(nomeDo(janela?.slots[31])), 'o menu conta os 64 fragmentos', nomeDo(janela?.slots[31]))
  if (janela) {
    const slot = janela.slots.findIndex((s, i) => i >= 9 && i < 27 && /Efici/.test(nomeDo(s)))
    if (slot >= 0) await bot.clickWindow(slot, 0, 0)
    await t.espera(1500)
    bot.closeWindow(janela)
  }
  let encs = await t.console(`data get entity ${nome} SelectedItem.components."minecraft:enchantments"`)
  t.verificar(/efficiency": 6/.test(encs), 'comprou Eficiência VI na picareta', encs)
  const fragmentos = await quantos(nome, 'paper')
  t.verificar(fragmentos === 49, 'gastou 15 fragmentos', `${fragmentos} sobrando`)

  await naMao(nome, 'diamond_sword')
  janela = await falar(bot, CAMBISTA)
  texto = ofertas(janela)
  t.verificar(/Afia/.test(texto) && /Saque IV/.test(texto) && !/Efici/.test(texto) && !/Fortuna/.test(texto),
    'na espada: Afiação e Saque (a partir do IV), sem Eficiência nem Fortuna', texto.slice(0, 200))
  if (janela) bot.closeWindow(janela)

  console.log('\n== Compactador ==')
  await t.console(`minecraft:clear ${nome}`)
  await t.console(`minecraft:give ${nome} iron_ingot 20`)
  janela = await falar(bot, COMPACTADOR)
  if (janela) {
    await bot.clickWindow(11, 0, 0)
    await t.espera(1200)
    bot.closeWindow(janela)
  }
  const blocos = await quantos(nome, 'iron_block'), lingotes = await quantos(nome, 'iron_ingot')
  t.verificar(blocos === 2 && lingotes === 2, 'compactou 18 lingotes em 2 blocos (sobraram 2)', `${blocos} blocos, ${lingotes} lingotes`)

  console.log('\n== Achados e perdidos ==')
  await t.console(`minecraft:clear ${nome}`)
  await naMao(nome, 'diamond 3')
  await t.espera(800)
  bot.setQuickBarSlot(0)
  await t.espera(300)
  // Q com Ctrl: joga a pilha inteira da mão
  bot._client.write('block_dig', { status: 3, location: { x: 0, y: 0, z: 0 }, face: 0, sequence: 0 })
  await t.espera(300)
  await t.console(`execute in minecraft:spawn run minecraft:tp ${nome} 0 65 43`) // longe do diamante (fora dos 8 blocos)
  await t.espera(1000)
  await t.console('limparchao agora')
  const antesAchados = await saldo(nome)
  janela = await new Promise(ok => {
    const fim = setTimeout(() => ok(null), 3000)
    bot.once('windowOpen', j => { clearTimeout(fim); ok(j) })
    bot.chat('/achados')
  })
  t.verificar(janela?.slots[0]?.name === 'diamond', 'o diamante jogado foi pros achados e perdidos', janela?.slots[0]?.name)
  if (janela) {
    await bot.clickWindow(0, 0, 0)
    await t.espera(1200)
    bot.closeWindow(janela)
  }
  t.verificar(await quantos(nome, 'diamond') === 3 && await saldo(nome) === antesAchados - 100, 'e voltou por R$ 100',
    `${await quantos(nome, 'diamond')} diamantes, ${antesAchados} → ${await saldo(nome)}`)

  console.log('\n== Extrato no Bancário ==')
  // o livro abre na tela (o servidor manda o pacote open_book)
  let livro = false
  const aoLivro = () => { livro = true }
  bot._client.on('open_book', aoLivro)
  janela = await falar(bot, BANCARIO)
  t.verificar(!!janela && janela.slots[16]?.name === 'written_book', 'o Bancário tem o botão do extrato', janela?.slots[16]?.name)
  if (janela) await bot.clickWindow(16, 0, 0)
  await t.espera(1500)
  bot._client.removeListener('open_book', aoLivro)
  t.verificar(livro, 'o extrato abre como livro na tela')
  // o conteúdo é o histórico do PixPlugin: a staff vê o mesmo com /pix admin log
  await t.console(`op ${nome}`)
  msgs = await t.capturar(bot, () => bot.chat(`/pix admin log ${nome}`), 1500)
  await t.console(`deop ${nome}`)
  const extrato = msgs.join(' | ')
  for (const [o, oQue] of [[/Achados e perdidos/, 'a taxa dos achados'], [/Mine-sena/, 'a Mine-sena'], [/Depósito de cheque/, 'o depósito do cheque'],
    [/Cheque cancelado/, 'o cancelamento do cheque'], [/Cheque emitido/, 'o cheque emitido']]) {
    t.verificar(o.test(extrato), `${oQue} está no extrato`)
  }

  console.log('\n== Teleporte nas arenas ==')
  await t.console(`execute in minecraft:arenas run minecraft:tp ${nome} 0 70 0`)
  await t.espera(1500)
  msgs = await t.capturar(bot, () => bot.chat(`/tpa ${nome}`))
  t.verificar(msgs.some(m => /comando de teleporte/.test(m)), 'o /tpa é barrado no mundo das arenas', msgs.join(' | '))
  await t.console(`execute in minecraft:spawn run minecraft:tp ${nome} 0 65 43`)
  await t.espera(1000)

  console.log('\n== Prestígio de skill e placar ==')
  await t.console(`skills admin setlevel ${nome} espadas 50`)
  msgs = await t.capturar(bot, () => bot.chat('/skills prestigio espadas confirmar'))
  t.verificar(msgs.some(m => /prestígio 1/.test(m)), 'o prestígio de Espadas é anunciado', msgs.join(' | '))
  await naMao(nome, 'iron_sword')
  await t.espera(2500) // o placar atualiza a cada 2 s
  const placar = t.semCor(await t.console(`placar ver ${nome}`))
  t.verificar(/Skill: Espadas ✦1 nv 1/.test(placar), 'o placar mostra a skill da mão com o prestígio', placar.replace(/\s+/g, ' ').slice(0, 260))

  console.log('\n== Parry com escudo ==')
  const atacante = await t.criarBot(t.nomeNovo('Atk'))
  await t.console(`minecraft:clear ${nome}`)
  await t.console(`minecraft:item replace entity ${nome} weapon.offhand with shield`)
  // no mundo de recursos, que tem PvP (o spawn não tem, e nas arenas só vale dentro de duelo e Gladiador)
  bot.chat('/recursos')
  // o lugar seguro é procurado aos poucos: espera chegar antes de levar o atacante
  for (let i = 0; i < 30 && !bot.mensagens.some(m => /Bem-vindo ao mundo de recursos/.test(m)); i++) await t.espera(500)
  await t.espera(1000)
  await t.console(`execute at ${nome} run minecraft:tp ${atacante.username} ^ ^ ^2`)
  await t.console(`execute as ${atacante.username} at @s run minecraft:tp @s ~ ~ ~ facing entity ${nome}`)
  await t.console(`execute as ${nome} at @s run minecraft:tp @s ~ ~ ~ facing entity ${atacante.username}`)
  await t.espera(1500)
  const alvo = Object.values(atacante.entities).find(e => e.username === nome)
  // logo depois do teleporte o golpe não conta: espera, e um de aquecimento sem escudo
  await t.espera(1500)
  if (alvo) atacante.attack(alvo)
  await t.espera(2500)
  // a janela é curta (0,3 s) e o tempo entre os dois bots varia: até 3 tentativas, com a recarga do parry entre elas
  msgs = []
  for (let tentativa = 0; tentativa < 3 && !msgs.some(m => /Escudo Perfeito/.test(m)); tentativa++) {
    if (tentativa > 0) await t.espera(3500)
    msgs = await t.capturar(bot, async () => {
      bot.activateItem(true) // levanta o escudo da segunda mão
      await t.espera(100)
      if (alvo) atacante.attack(alvo)
    }, 1500)
    bot.deactivateItem()
  }
  t.verificar(msgs.some(m => /Escudo Perfeito/.test(m)), 'escudo levantado bem na hora do golpe: Escudo Perfeito', msgs.join(' | '))
  const msgsAtk = atacante.mensagens.slice(-5).join(' | ')
  t.verificar(/aparado/.test(msgsAtk), 'e quem bateu é avisado do parry', msgsAtk)

  console.log('\n== Boss elite ==')
  await t.console('boss stop')
  msgs = await t.capturar(bot, () => t.console('boss start zombie elite'), 2500)
  t.verificar(msgs.some(m => /ELITE/.test(m)), 'o /boss start zombie elite anuncia o boss elite', msgs.join(' | ').slice(0, 200))
  await t.console('boss stop')

  await t.console(`eco set ${nome} 0`)
  await t.terminar(bot, atacante)
})
