// Vínculo com o Discord: um bot gera o código no jogo (/vincular) e o teste faz o papel do bot do
// Discord, chamando a API local do DiscordPlugin como o discord-bot faz. Confere o vínculo, a
// recompensa (5 bilhetes grátis da Mine-sena, uma vez só), os erros (código errado, reusado, conta
// já vinculada) e o desvincular. Não precisa do Discord.
// Uso: node teste-vinculo.js
const t = require('./lib')
const fs = require('fs')

const PASTA = process.env.MC_TESTE || 'C:/mc-teste'
const config = fs.readFileSync(`${PASTA}/plugins/DiscordPlugin/config.yml`, 'utf8')
const bloco = config.slice(config.search(/^api:/m))
const TOKEN = (bloco.match(/^\s+token:\s*"?([0-9a-f]+)"?/m) || [])[1]
const PORTA = (bloco.match(/^\s+porta:\s*(\d+)/m) || [])[1] || 8155

async function api(metodo, rota, corpo) {
  const r = await fetch(`http://127.0.0.1:${PORTA}${rota}`, {
    method: metodo,
    headers: { Authorization: `Bearer ${TOKEN}`, 'Content-Type': 'application/json' },
    body: corpo && JSON.stringify(corpo),
  })
  return r.json()
}

async function codigo(bot) {
  const msgs = await t.capturar(bot, () => bot.chat('/vincular'))
  const m = msgs.join('\n').match(/Seu código: ([A-Z0-9]{6})/)
  return m && m[1]
}

t.rodar(async () => {
  t.verificar(!!TOKEN, 'o DiscordPlugin tem api.token no config')
  const semToken = await (await fetch(`http://127.0.0.1:${PORTA}/saude`)).json()
  t.verificar(semToken.ok === false, 'a API recusa pedido sem token', semToken.erro)

  const nome = t.nomeNovo('Vinc')
  const discordId = String(100000000000000000n + BigInt(Date.now()))
  const outroDiscord = String(BigInt(discordId) + 1n)
  const bot = await t.criarBot(nome)

  const c1 = await codigo(bot)
  t.verificar(!!c1, '/vincular mostra um código de 6 caracteres', c1)

  let r = await api('POST', '/vinculo', { codigo: 'ZZZZZZ', discordId, discordNome: 'teste', recompensa: true })
  t.verificar(r.ok === false, 'código errado é recusado', r.erro)

  r = await api('POST', '/vinculo', { codigo: c1.toLowerCase(), discordId, discordNome: 'teste', recompensa: true })
  t.verificar(r.ok && r.nick === nome, 'o código vincula a conta (aceita minúsculas)', JSON.stringify(r))
  t.verificar(r.recompensado === true, 'a primeira vez dá a recompensa')

  const minesena = await t.capturar(bot, () => bot.chat('/minesena'))
  t.verificar(minesena.some(m => /5 bilhete\(s\) grátis/.test(m)), 'o jogador ganhou 5 bilhetes grátis da Mine-sena')

  r = await api('POST', '/vinculo', { codigo: c1, discordId, discordNome: 'teste', recompensa: true })
  t.verificar(r.ok === false, 'o mesmo código não vale duas vezes', r.erro)

  const c2 = await codigo(bot)
  t.verificar(!c2, 'quem já vinculou não gera outro código')

  const lista = await api('GET', '/vinculos')
  t.verificar(lista.vinculos.some(v => v.discordId === discordId && v.nick === nome), 'o vínculo aparece em /vinculos')

  const info = await t.console(`discord vinculo ${nome}`)
  t.verificar(info.includes(discordId), '/discord vinculo mostra o Discord do jogador')

  r = await api('POST', '/desvincular', { discordId })
  t.verificar(r.ok && r.nick === nome, 'desvincular pelo Discord funciona')

  const c3 = await codigo(bot)
  r = await api('POST', '/vinculo', { codigo: c3, discordId: outroDiscord, discordNome: 'outro', recompensa: true })
  t.verificar(r.ok && r.recompensado === false, 'vincular de novo (outro Discord) não dá recompensa de novo', JSON.stringify(r))

  const c4 = await codigo(bot)
  t.verificar(!c4, 'conta vinculada não gera código pra outro Discord')

  await t.console(`discord desvincular ${nome}`)
  const fim = await api('GET', '/vinculos')
  t.verificar(!fim.vinculos.some(v => v.nick === nome), '/discord desvincular (staff) desfaz o vínculo')

  await t.terminar(bot)
})
