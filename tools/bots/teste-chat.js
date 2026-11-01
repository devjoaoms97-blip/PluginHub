// ChatPlugin. Canais: a mensagem normal só chega a quem está no mesmo mundo ([L]), o /g chega a todos
// ([G]) e a staff vê o local dos outros mundos. Anti-flood: intervalo entre mensagens, mensagem
// repetida (o /msg conta junto com o chat), rajada que silencia, intervalo nos comandos que cobram
// (/pix receber) e o /loja anunciar valendo pra staff também. Uso: node teste-chat.js
const fs = require('fs')
const path = require('path')
const t = require('./lib')

const ir = (bot, mundo) => t.console(`execute in minecraft:${mundo} run minecraft:tp ${bot.username} 0.5 80 0.5`)

t.rodar(async () => {
  const bot = await t.criarBot(t.nomeNovo('Flood'), { antiflood: true })
  const outro = await t.criarBot(t.nomeNovo('Lei'))
  const ouvinte = await t.criarBot(t.nomeNovo('Ouv'))
  const nome = bot.username
  await t.console(`op ${nome}`) // a staff também passa pelo anti-flood (e vê o local dos outros mundos)
  await t.console(`deop ${ouvinte.username}`)
  await t.espera(1000)

  console.log('\n== Canais ==')
  for (const b of [bot, outro, ouvinte]) await ir(b, 'spawn')
  await t.espera(1000)
  let msgs = await t.capturar(ouvinte, () => outro.chat('oi do mesmo mundo'), 1200)
  // o Mineflayer mostra só o texto da mensagem assinada pelo cliente (sem o formato); o formato com o
  // prefixo do canal sai no log do servidor
  const log = fs.readFileSync(path.join(process.env.MC_TESTE || 'C:\\mc-teste', 'ultimo-boot.txt'), 'latin1')
  t.verificar(msgs.some(m => m.includes('oi do mesmo mundo')) && /\[L\][^\n]*oi do mesmo mundo/.test(log.replace(/\x1b\[[\d;]*m/g, '')),
    'no mesmo mundo, o chat local chega (com [L] no formato)', msgs.join(' | '))
  await ir(ouvinte, 'terrenos')
  await ir(bot, 'terrenos')
  await t.espera(1500)
  const antesBot = bot.mensagens.length
  msgs = await t.capturar(ouvinte, () => outro.chat('oi de longe'), 1200)
  t.verificar(!msgs.some(m => m.includes('oi de longe')), 'em outro mundo, o chat local não chega', msgs.join(' | '))
  t.verificar(bot.mensagens.slice(antesBot).some(m => m.includes('[spawn]') && m.includes('oi de longe')),
    'a staff vê o local dos outros mundos, com o nome do mundo', bot.mensagens.slice(antesBot).join(' | '))
  t.verificar(outro.mensagens.some(m => m.includes('Ninguém no seu mundo ouviu')), 'quem falou sozinho no mundo é lembrado do /g')
  msgs = await t.capturar(ouvinte, () => outro.chat('/g oi pra todos'), 1200)
  t.verificar(msgs.some(m => m.includes('[G]') && m.includes('oi pra todos')), 'o /g chega em outro mundo, com [G]', msgs.join(' | '))
  for (const b of [bot, ouvinte]) await ir(b, 'spawn')
  await t.espera(2500)

  console.log('\n== Intervalo e mensagem repetida ==')
  msgs = await t.capturar(outro, async () => {
    bot.chat('primeira mensagem')
    await t.espera(300)
    bot.chat('segunda, rápido demais')
  }, 1200)
  t.verificar(msgs.some(m => m.includes('primeira mensagem')) && !msgs.some(m => m.includes('segunda')),
    'a segunda mensagem em menos de 2 s não sai', msgs.join(' | '))
  t.verificar(bot.mensagens.some(m => m.includes('Calma!')), 'quem mandou é avisado')

  await t.espera(2200)
  msgs = await t.capturar(outro, () => bot.chat('Primeira   mensagem!!'), 1200)
  t.verificar(!msgs.some(m => m.includes('Primeira')) && bot.mensagens.some(m => m.includes('acabou de mandar essa mensagem')),
    'a mesma mensagem (com maiúsculas e pontuação diferentes) não repete', msgs.join(' | '))

  await t.espera(2200)
  msgs = await t.capturar(outro, () => bot.chat(`/msg ${outro.username} primeira mensagem`), 1200)
  t.verificar(!msgs.some(m => m.includes('primeira mensagem')), 'o /msg conta junto com o chat (repetida também)', msgs.join(' | '))

  console.log('\n== Rajada ==')
  for (let i = 0; i < 6; i++) {
    await t.espera(2100)
    bot.chat(`mensagem numero ${'abcdef'[i]}`)
  }
  await t.espera(800)
  t.verificar(bot.mensagens.some(m => m.includes('Muitas mensagens seguidas')), 'a sexta mensagem em 15 s silencia')
  msgs = await t.capturar(outro, async () => { await t.espera(2100); bot.chat('ainda silenciado?') }, 1200)
  t.verificar(!msgs.some(m => m.includes('ainda silenciado')), 'silenciado, nada sai', msgs.join(' | '))

  console.log('\n== Comandos com intervalo ==')
  await t.console(`eco set ${nome} 10000`)
  await t.capturar(bot, () => bot.chat(`/pix receber ${outro.username} 10`))
  await t.capturar(bot, () => bot.chat(`/pix cancelar ${outro.username}`))
  msgs = await t.capturar(bot, () => bot.chat(`/pix receber ${outro.username} 10`))
  t.verificar(msgs.some(m => m.includes('pra usar esse comando de novo')), 'o segundo /pix receber em 10 s espera', msgs.join(' | '))

  console.log('\n== /loja anunciar (staff também espera) ==')
  await t.console('barraca liberar b6')
  await t.capturar(bot, () => bot.chat('/barraca alugar b6'))
  await t.console(`minecraft:give ${nome} minecraft:bread 5`)
  await t.espera(500)
  bot.setQuickBarSlot(0)
  await t.capturar(bot, () => bot.chat('/barraca vender 1 10'))
  await t.capturar(bot, () => bot.chat('/loja anunciar pão'))
  msgs = await t.capturar(bot, () => bot.chat('/loja anunciar pão de novo'))
  t.verificar(msgs.some(m => m.includes('anunciar de novo em')), 'um op também espera o intervalo do anúncio', msgs.join(' | '))
  await t.console('barraca liberar b6')

  await t.console(`deop ${nome}`)
  await t.terminar(bot, outro, ouvinte)
})
