// Rankup: menu do /ranks (subir clicando), menu do /mina (ir clicando), holograma da mina, rank
// na tab e prestígio (volta pro primeiro rank com a marca ✦ no chat e na tab). Precisa da mina
// pedreira. Uso: node teste-rankup-extras.js [mina]
const t = require('./lib')

/** Abre o menu com o comando e devolve a janela (ou null em 3 s). */
function abrirMenu(bot, comando) {
  return new Promise(ok => {
    const fim = setTimeout(() => ok(null), 3000)
    bot.once('windowOpen', janela => { clearTimeout(fim); ok(janela) })
    bot.chat(comando)
  })
}

const titulo = janela => janela ? JSON.stringify(janela.title) : ''
const fs = require('fs')
const LOG = `${process.env.MC_TESTE || 'C:/mc-teste'}/ultimo-boot.txt`

t.rodar(async () => {
  const mina = t.mina(process.argv[2] || 'pedreira')
  const bot = await t.criarBot(t.nomeNovo('Rkp'))
  const nome = bot.username
  await t.console(`gamemode survival ${nome}`)
  await t.console(`eco set ${nome} 100000`)

  console.log('\n== Menu do /ranks ==')
  let janela = await abrirMenu(bot, '/ranks')
  t.verificar(titulo(janela).includes('Ranks'), 'o /ranks abre o menu', titulo(janela))
  t.verificar(janela.slots[0]?.name === 'emerald' && janela.slots[1]?.name === 'gold_ingot', 'o rank atual (esmeralda) e o próximo (ouro)',
    `${janela.slots[0]?.name}, ${janela.slots[1]?.name}`)
  let msgs = await t.capturar(bot, () => bot.clickWindow(1, 0, 0), 2000)
  t.verificar(msgs.some(m => m.includes('subiu pro rank')), 'clicar no próximo sobe de rank', msgs.find(m => m.includes('subiu')))

  console.log('\n== Menu do /mina ==')
  janela = await abrirMenu(bot, '/mina')
  t.verificar(titulo(janela).includes('Minas'), 'o /mina abre o menu', titulo(janela))
  const slot = janela.slots.findIndex((s, i) => i < janela.inventoryStart && s && s.name !== 'barrier')
  t.verificar(slot >= 0, 'tem uma mina liberada no menu')
  await t.capturar(bot, () => bot.clickWindow(slot, 0, 0), 2500)
  const perto = bot.entity.position.distanceTo(new (require('vec3').Vec3)(mina.spawn[0], mina.spawn[1], mina.spawn[2])) < 30
  t.verificar(perto, 'clicar na mina leva até ela', `${bot.entity.position}`)

  console.log('\n== Holograma ==')
  await t.espera(2500)
  const holo = await t.console(`execute in minecraft:${mina.dimensao} positioned ${mina.spawn[0]} ${mina.spawn[1]} ${mina.spawn[2]} run data get entity @e[type=text_display,limit=1,sort=nearest,distance=..6] text`)
  t.verificar(holo.includes('Mina') && holo.includes(mina.nome), 'holograma com o nome da mina em cima do ponto de teleporte', holo.slice(0, 120))

  console.log('\n== Rank na tab e prestígio ==')
  const tab = () => t.semCor(String(bot.players[nome]?.displayName?.toString?.() ?? ''))
  await t.espera(1000)
  t.verificar(tab().includes('Fazendeiro'), 'o rank aparece na tab', tab())
  msgs = await t.capturar(bot, () => bot.chat('/prestigio confirmar'))
  t.verificar(msgs.some(m => m.includes('ainda não chegou') || m.includes('precisa estar no rank')), 'sem estar no último rank, não tem prestígio')
  await t.console(`ranks admin set ${nome} rei`)
  msgs = await t.capturar(bot, () => bot.chat('/prestigio confirmar'), 2000)
  t.verificar(msgs.some(m => m.includes('prestígio 1')), 'no último rank, o prestígio volta pro primeiro', msgs.find(m => m.includes('prestígio')))
  t.verificar(tab().includes('✦1') && tab().includes('Camponês'), 'a tab mostra o prestígio e o rank novo', tab())
  // o bot recebe só o corpo das mensagens de chat: o formato com o prefixo sai no log do servidor
  bot.chat('oi do prestigio')
  await t.espera(1500)
  const linha = fs.readFileSync(LOG, 'latin1').split('\n').reverse().find(l => l.includes(nome) && l.includes('oi do prestigio')) || ''
  const limpa = linha.replace(/\x1b\[[0-9;]*m/g, '')
  t.verificar(/1.{0,30}Campon/.test(limpa), 'o chat mostra o prestígio e o rank', limpa.slice(-60))

  await t.terminar(bot)
})
