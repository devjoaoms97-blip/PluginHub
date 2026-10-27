// Ponte com o Discord: um servidor HTTP local faz o papel do Discord e guarda o que chega. Aponta
// o webhook do DiscordPlugin pra ele, dispara o teste, a abertura do Gladiador e um leilão de
// terreno, e confere o nome de cada categoria e o texto sem cores. Volta o config no fim.
// Uso: node teste-discord.js
const t = require('./lib')
const fs = require('fs')
const http = require('http')

const PASTA = process.env.MC_TESTE || 'C:/mc-teste'
const CONFIG = `${PASTA}/plugins/DiscordPlugin/config.yml`
const PORTA = 8775 // a 8765 é do PacotePlugin (resource pack)

t.rodar(async () => {
  const recebidas = []
  const servidor = http.createServer((req, res) => {
    let corpo = ''
    req.on('data', c => { corpo += c })
    req.on('end', () => {
      try { recebidas.push(JSON.parse(corpo)) } catch { recebidas.push({ erro: corpo }) }
      res.writeHead(204)
      res.end()
    })
  })
  await new Promise(ok => servidor.listen(PORTA, '127.0.0.1', ok))
  const esperar = async (filtro, ms = 5000) => {
    for (let s = 0; s < ms / 250 && !recebidas.some(filtro); s++) await t.espera(250)
    return recebidas.find(filtro)
  }

  const original = fs.readFileSync(CONFIG, 'utf8')
  fs.writeFileSync(CONFIG, original.replace(/^webhook-url: ".*"/m, `webhook-url: "http://127.0.0.1:${PORTA}/webhook"`))
  try {
    await t.console('discord reload')

    let m = await (t.console('discord teste gladiador').then(() => esperar(x => x.content?.includes('teste'))))
    t.verificar(m && m.username === 'Gladiador', 'o /discord teste chegou com o nome da categoria', m && `${m.username}: ${m.content}`)
    t.verificar(m && m.allowed_mentions && m.allowed_mentions.parse.length === 0, 'sem menções (@everyone)')

    await t.console('gladiador abrir individual 0')
    m = await esperar(x => x.content?.includes('GLADIADOR'))
    t.verificar(!!m, 'a abertura do Gladiador foi pro Discord', m && m.content.slice(0, 80))
    t.verificar(m && !m.content.includes('§'), 'sem os códigos de cor do Minecraft')
    await t.console('gladiador cancelar')

    await t.console('terreno admin dono 9;9 BotTeste')
    await t.console('terreno admin leiloar 9;9')
    m = await esperar(x => x.content?.includes('Leilão de imóvel'))
    t.verificar(m && m.username === 'Leilões', 'o leilão de terreno foi pro Discord (categoria leilões)', m && `${m.username}: ${m.content.slice(0, 80)}`)
    await t.console('terreno admin remover 9;9')

    fs.writeFileSync(CONFIG, original)
    await t.console('discord reload')
  } finally {
    fs.writeFileSync(CONFIG, original)
  }
  servidor.close()
  await t.terminar()
})
