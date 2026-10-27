// Loja de créditos (CreditosPlugin): créditos entregues pelo console (como a loja do Discord fará),
// cosmético comprado com confirmação e já em uso, cor do nome na tab, VIP ativado e código de presente,
// recusa de ativar o mesmo tier por cima e a tag de apoiador.
// Uso: node teste-creditos.js
const t = require('./lib')

const creditos = async nome => Number(((await t.console(`creditos ver ${nome}`)).match(/: (\d+) créditos/) || [, -1])[1])
const tab = (bot, nome) => {
  const d = bot.players[nome]?.displayName
  return d ? (typeof d.toMotd === 'function' ? d.toMotd() : String(d)) : ''
}

t.rodar(async () => {
  const bot = await t.criarBot(t.nomeNovo('Crd'))
  const nome = bot.username
  await t.console(`vip remove ${nome}`)

  /** Abre o /creditos e espera o menu. */
  const abrirLoja = () => new Promise(ok => {
    const fim = setTimeout(() => ok(null), 3000)
    bot.once('windowOpen', j => { clearTimeout(fim); ok(j) })
    bot.chat('/creditos')
  })
  /** Clica no slot (0 = esquerdo, 1 = direito) e espera um menu novo abrir (ou o tempo acabar). */
  const clicar = async (slot, botao = 0) => {
    const novo = new Promise(ok => { const fim = setTimeout(() => ok(null), 1500); bot.once('windowOpen', j => { clearTimeout(fim); ok(j) }) })
    await bot.clickWindow(slot, botao, 0)
    await novo
    await t.espera(300)
  }

  console.log('\n== Créditos ==')
  let msgs = await t.capturar(bot, () => t.console(`creditos dar ${nome} 1000 teste do bot`))
  t.verificar(msgs.some(m => /recebeu 1000 créditos/.test(m)) && await creditos(nome) === 1000, 'o console dá créditos (a loja do Discord vai usar isso)', msgs[0])

  console.log('\n== Cosméticos ==')
  let janela = await abrirLoja()
  t.verificar(!!janela && janela.slots[12]?.name === 'blaze_powder', 'o /creditos abre a loja', janela?.title)
  await clicar(12) // rastros
  msgs = await t.capturar(bot, () => clicar(9)) // 1º clique: pede confirmação
  t.verificar(msgs.some(m => /Clique de novo pra confirmar/.test(m)) && await creditos(nome) === 1000, 'o primeiro clique só pede confirmação', msgs[0])
  msgs = await t.capturar(bot, () => clicar(9))
  t.verificar(msgs.some(m => /comprou/.test(m)) && await creditos(nome) === 950, 'o segundo compra o rastro (50 créditos) e já usa', msgs.join(' | '))
  await clicar(27) // voltar
  await clicar(14) // cor do nome
  await clicar(9)
  await clicar(9)
  t.verificar(await creditos(nome) === 920, 'comprou a cor aqua (30 créditos)', String(await creditos(nome)))
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow)
  await t.espera(1500)
  t.verificar(tab(bot, nome).includes('§b' + nome), 'o nome fica aqua na tab', tab(bot, nome))

  console.log('\n== VIP ==')
  janela = await abrirLoja()
  await clicar(10) // VIP
  await clicar(10) // bronze, esquerdo: confirmação
  msgs = await t.capturar(bot, () => clicar(10), 2500)
  t.verificar(msgs.some(m => /\+30 dias com/.test(m)) && await creditos(nome) === 820, 'ativa o VIP Bronze por 100 créditos', msgs.join(' | ').slice(0, 160))
  await t.espera(1000)
  t.verificar(/Bronze/.test(tab(bot, nome)), 'e a tag do VIP aparece na tab', tab(bot, nome))
  janela = await abrirLoja()
  await clicar(10)
  msgs = await t.capturar(bot, () => clicar(10))
  msgs = await t.capturar(bot, () => clicar(10), 2500)
  const check = await t.console(`vip check ${nome}`)
  const dias = Number((check.match(/expira em (\d+)d/) || [, 0])[1])
  t.verificar(await creditos(nome) === 720 && dias >= 59, 'ativar o mesmo tier de novo soma os 30 dias aos que faltam', check)
  // ativar fecha a loja: abre de novo pra gerar o código
  janela = await abrirLoja()
  await clicar(10)
  await clicar(14, 1) // ouro, direito: confirmação do código
  msgs = await t.capturar(bot, () => clicar(14, 1), 2500)
  const codigo = (msgs.join(' ').match(/1 uso\): (?:VIP-)?([A-Z0-9]+)/) || [])[1]
  t.verificar(!!codigo && await creditos(nome) === 370, 'o clique direito gera um código de VIP Ouro (350 créditos)', msgs.join(' | ').slice(0, 160))
  if (codigo) await t.console(`vip revogarcodigo ${codigo}`)
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow)

  console.log('\n== Apoiador ==')
  msgs = await t.capturar(bot, () => t.console(`apoiador dar ${nome}`), 1500)
  t.verificar(msgs.some(m => /Obrigado pelo apoio/.test(m)), 'a staff (ou o Discord) dá a tag de apoiador', msgs[0])
  t.verificar(/Apoiador/.test(tab(bot, nome)), 'e ela aparece na tab', tab(bot, nome))
  const ver = await t.console(`apoiador ver ${nome}`)
  t.verificar(/apoiador até/.test(ver), 'por 30 dias', ver)

  await t.console(`vip remove ${nome}`)
  await t.console(`creditos tirar ${nome} ${await creditos(nome)} limpeza do teste`)
  await t.terminar(bot)
})
