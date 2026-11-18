// Porto da /warp pesca (PescaPlugin, 2026-11-17): a warp chega no porto, o /pesca lista as barraquinhas,
// alugar cobra e é uma por jogador, na barraquinha a vara puxa e lança sozinha e o peixe vai pro barril (não
// pro inventário), só o dono abre o barril, "Pegar tudo" leva pro inventário, e o fim do aluguel libera a
// barraquinha e guarda o estoque pro /pesca retirar.
// Precisa do porto (montar-mapa.js construir loja porto marcar). Uso: node teste-pesca-porto.js
const { Vec3 } = require('vec3')
const t = require('./lib')

const W = 'execute in minecraft:spawn run'
const saldo = async nome => Number(((await t.console(`balance ${nome}`)).match(/\$([\d,.]+)/) || [, 'NaN'])[1].replace(/,/g, ''))
const linhaDa = async id => (await t.console('pesca admin lista')).split(/\r?\n/).find(l => l.startsWith(id + ':')) || ''

/** Abre o barril da barraquinha e devolve a janela (ou null). */
async function abrirBarril(bot, [x, y, z]) {
  const janela = new Promise(ok => {
    const fim = setTimeout(() => ok(null), 3000)
    bot.once('windowOpen', w => { clearTimeout(fim); ok(w) })
  })
  await bot.activateBlock(bot.blockAt(new Vec3(x, y, z)))
  return janela
}

t.rodar(async () => {
  const a = await t.criarBot(t.nomeNovo('Psa')), b = await t.criarBot(t.nomeNovo('Psb'))
  for (const bot of [a, b]) {
    await t.console(`gamemode survival ${bot.username}`)
    await t.console(`minecraft:clear ${bot.username}`)
  }
  await t.console('pesca admin liberar 1')
  await t.console('pesca admin liberar 2')

  console.log('\n== Chegada ==')
  a.chat('/warp pesca')
  await t.espera(5000) // 3 s de espera do Essentials pra quem não é VIP
  const p = a.entity.position
  t.verificar(p.x > 140 && p.x < 158 && p.z > 5 && p.z < 12, 'a /warp pesca chega no porto', `${p.x.toFixed(1)} ${p.y.toFixed(1)} ${p.z.toFixed(1)}`)
  t.verificar(/passed/.test(await t.console(`${W} execute if block 91 65 8 minecraft:barrel`)), 'a barraquinha 1 tem o barril (o estoque)')
  const lista = await t.capturar(a, () => a.chat('/pesca'), 1000)
  t.verificar(lista.some(m => /Barraquinhas de pesca/.test(m)) && lista.some(m => /Livres: .*\[1\]/.test(m)), 'o /pesca lista as barraquinhas livres', lista.find(m => /Livres/.test(m)))

  console.log('\n== Aluguel ==')
  await t.console(`eco set ${a.username} 50000`)
  await t.console(`eco set ${b.username} 50000`)
  const msgs = await t.capturar(a, () => a.chat('/pesca alugar 1'), 1000)
  t.verificar(await saldo(a.username) === 40000 && /Psa/.test(await linhaDa('1')), 'alugar cobra R$ 10.000 e a barraquinha fica dele', `${await saldo(a.username)} | ${await linhaDa('1')}`)
  t.verificar(msgs.some(m => /alugou a barraquinha/.test(m)), 'e ele é avisado de como funciona', msgs.find(m => /alugou/.test(m)))
  const outra = await t.capturar(a, () => a.chat('/pesca alugar 2'), 1000)
  t.verificar(outra.some(m => /uma por jogador/.test(m)) && await saldo(a.username) === 40000, 'é uma por jogador (e não cobra a segunda)', outra.find(m => /barraquinha/.test(m)))

  console.log('\n== Pescando parado ==')
  // vara com Isca III pra o teste não demorar (com o dobro de espera: de 2 a 30 s por peixe)
  await t.console(`minecraft:item replace entity ${a.username} weapon.mainhand with minecraft:fishing_rod[enchantments={"minecraft:lure":3}]`)
  await t.console(`${W} minecraft:tp ${a.username} 92.5 65 5.5 180 25`)
  await t.espera(5000) // o Essentials deixa invulnerável 4 s depois do teleporte
  a.activateItem() // o primeiro lançamento é do jogador; depois, ela lança sozinha
  await t.espera(300)
  a.deactivateItem()
  let pilhas = 0
  for (let i = 0; i < 40 && pilhas < 2; i++) {
    await t.espera(3000)
    pilhas = Number(((await linhaDa('1')).match(/estoque (\d+)/) || [, 0])[1])
  }
  const inv = t.inventario(a)
  t.verificar(pilhas >= 2, 'parado na barraquinha, a vara puxou e lançou sozinha (2 pescas no estoque)', `${pilhas} pilha(s) em até 2 min`)
  t.verificar(Object.keys(inv).every(k => k === 'fishing_rod'), 'o peixe foi pro estoque, não pro inventário', JSON.stringify(inv))

  console.log('\n== O barril ==')
  const alheio = await t.capturar(b, async () => {
    await t.console(`${W} minecraft:tp ${b.username} 92.5 65 9.5 180 30`)
    await t.espera(1500)
    await abrirBarril(b, [91, 65, 8])
  }, 800)
  t.verificar(alheio.some(m => /barraquinha de .*Psa/.test(m)), 'outro jogador não abre o barril da barraquinha', alheio.find(m => /barraquinha/.test(m)))
  await t.console(`${W} minecraft:tp ${a.username} 92.5 65 6.5 0 30`)
  await t.espera(1500)
  const janela = await abrirBarril(a, [91, 65, 8])
  const titulo = janela ? JSON.stringify(janela.title) : ''
  t.verificar(!!janela && /Estoque da barraquinha 1/.test(titulo) && janela.slots.slice(0, 45).some(s => s), 'o dono abre o estoque no barril', titulo)
  if (janela) {
    await a.clickWindow(47, 0, 0) // Pegar tudo
    await t.espera(1000)
    a.closeWindow(a.currentWindow || janela)
  }
  const depois = t.inventario(a)
  t.verificar(Object.keys(depois).some(k => k !== 'fishing_rod'), '"Pegar tudo" leva o pescado pro inventário', JSON.stringify(depois))

  console.log('\n== Fim do aluguel ==')
  await t.console(`minecraft:clear ${a.username}`)
  await t.console(`${W} minecraft:tp ${a.username} 150.5 65 10.5 180 0`)
  await t.console(`${W} minecraft:tp ${b.username} 150.5 65 10.5 180 0`)
  // um peixe no estoque, pra ver que ele vai pra retirada
  await t.console(`pesca admin vencer 1`)
  t.verificar(/1: livre/.test(await linhaDa('1')), 'vencido, a barraquinha fica livre de novo', await linhaDa('1'))
  const deNovo = await t.capturar(b, () => b.chat('/pesca alugar 1'), 1000)
  t.verificar(/Psb/.test(await linhaDa('1')), 'e outro jogador pode alugar', deNovo.find(m => /barraquinha/.test(m)))
  await t.console('pesca admin liberar 1')

  await t.terminar(a, b)
})
