// Conexão: o bot entra, fala no chat e sai. Primeiro teste depois de montar o servidor.
const t = require('./lib')

t.rodar(async () => {
  const bot = await t.criarBot('BotTeste')
  t.verificar(true, `entrou pela versão ${bot.version} em ${bot.entity.position}`)
  const msgs = await t.capturar(bot, () => bot.chat('oi'))
  t.verificar(msgs.some(m => m.includes('oi')), 'o chat chegou de volta')
  await t.terminar(bot)
})
