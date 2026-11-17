// Fazendas, fase 5: como um jogador comum (só os comandos da lista do AjudaPlugin), o Corretor Rural e o
// /fazendas loja abrem, os comandos da staff não; o /comandos fala das fazendas (Produção e Terreno) e o
// Seu Juca do Terreno, no spawn, tem as falas delas.
// Uso: node teste-fazendas-jogador.js (depois de "node montar-mapa.js construir dicas", se as falas mudaram)
const t = require('./lib')
const { Vec3 } = require('vec3')
const fs = require('fs')
const path = require('path')

const OZ = 43
const CORRETOR = new Vec3(138.5, 65, OZ - 14 + 0.5)

const janela = (bot, fn) => new Promise(ok => {
  const fim = setTimeout(() => ok(null), 3000)
  bot.once('windowOpen', j => { clearTimeout(fim); setTimeout(() => ok(j), 800) })
  fn()
})

t.rodar(async () => {
  await t.console('comandos reload')
  const bot = await t.criarBot(t.nomeNovo('Jog'), { comandos: true }) // como um jogador: só os comandos da lista
  const nome = bot.username
  await t.espera(1500)
  await t.console(`eco set ${nome} 50000`)
  await t.console(`ranks admin set ${nome} fazendeiro`)

  console.log('\n== Loja ==')
  let msgs = []
  let j = null
  msgs = await t.capturar(bot, async () => { j = await janela(bot, () => bot.chat('/fazendas loja')) }, 300)
  t.verificar(!!j && !msgs.some(m => m.includes('desconhecido')), 'o jogador abre o /fazendas loja', msgs.find(m => m.includes('desconhecido')))
  if (j) bot.closeWindow(bot.currentWindow)
  await t.console(`execute in minecraft:spawn run minecraft:tp ${nome} 138.5 65 ${OZ - 10.5} 180 0`)
  await t.espera(2000)
  const corretor = Object.values(bot.entities).find(e => e.name === 'mannequin' && e.position.distanceTo(CORRETOR) < 1)
  j = corretor && await janela(bot, () => bot.activateEntity(corretor))
  t.verificar(!!j && j.slots[11]?.name === 'egg', 'o Corretor Rural abre a loja pro jogador (o comando dele está na lista)')
  if (j) bot.closeWindow(bot.currentWindow)
  msgs = await t.capturar(bot, () => bot.chat('/fazendas dar ' + nome + ' horta'), 1000)
  t.verificar(msgs.some(m => m.includes('Uso: /fazendas loja')) && !(await t.console(`minecraft:clear ${nome} minecraft:paper 0`)).includes('Found'),
    'os comandos da staff não funcionam pro jogador', msgs[0])

  console.log('\n== /comandos ==')
  msgs = await t.capturar(bot, () => bot.chat('/comandos producao'), 1200)
  t.verificar(msgs.some(m => m.includes('/fazendas loja')) && msgs.some(m => m.includes('Corretor Rural')), 'o /comandos producao explica as fazendas', msgs.find(m => m.includes('fazenda')))
  msgs = await t.capturar(bot, () => bot.chat('/comandos terreno'), 1200)
  t.verificar(msgs.some(m => m.includes('4 fazendas')), 'o /comandos terreno diz quantas fazendas cabem', msgs.find(m => m.includes('fazendas')))

  console.log('\n== Dicas ==')
  const npcs = fs.readFileSync(path.join(process.env.MC_TESTE || 'C:\\mc-teste', 'plugins', 'DicasPlugin', 'npcs.yml'), 'utf8')
    .replace(/\s+/g, ' ') // o YAML quebra as falas longas em várias linhas
  t.verificar(npcs.includes('Corretor Rural') && npcs.includes('quem arrematar leva a fazenda'), 'o Seu Juca do Terreno fala das fazendas')
  await t.terminar(bot)
})
