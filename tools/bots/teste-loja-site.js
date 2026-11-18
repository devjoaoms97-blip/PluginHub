// Site, etapa 3 (2026-11-18): a loja de créditos do site, com um Mercado Pago falso (nada é cobrado nem
// chamado de verdade). Confere: o código chega no chat do jogo, não sai outro em seguida nem pra quem está
// offline, código errado é recusado, o certo cria o pedido e o link; pagamento aprovado vira entrega (os
// créditos caem e o pedido fica "entregue"), sem entregar duas vezes; valor menor não entrega; o POST sem
// JSON é recusado. E as páginas novas: regras, novidades, votar (com a sequência do nick) e a prévia.
// Uso: node teste-loja-site.js (com o servidor de teste ligado)
const t = require('./lib')
const fs = require('fs')
const os = require('os')
const path = require('path')
const http = require('http')
const { spawn } = require('child_process')

const MC = process.env.MC_TESTE || 'C:\\mc-teste'
const PORTA_SITE = 8097
const PORTA_MP = 8096
const cfg = fs.readFileSync(path.join(MC, 'plugins', 'DiscordPlugin', 'config.yml'), 'utf8')
const portaApi = (cfg.match(/^\s+porta: (\d+)/m) || [, '8155'])[1]
const token = (cfg.match(/^\s+token: "?([0-9a-f]+)"?/m) || [])[1]

// ---- o Mercado Pago falso: guarda as preferências e devolve os pagamentos que o teste aprovar
const preferencias = {}
const pagamentos = {} // referência → [{...}]
let proximoPagamento = Date.now() % 1e9
const mp = http.createServer((req, res) => {
  let corpo = ''
  req.on('data', d => { corpo += d })
  req.on('end', () => {
    const url = new URL(req.url, 'http://mp')
    res.setHeader('Content-Type', 'application/json')
    if (req.method === 'POST' && url.pathname === '/checkout/preferences') {
      const p = JSON.parse(corpo)
      preferencias[p.external_reference] = p
      return res.end(JSON.stringify({ id: `pref-${p.external_reference}`, init_point: `http://127.0.0.1:${PORTA_MP}/pagar/${p.external_reference}` }))
    }
    if (req.method === 'GET' && url.pathname === '/v1/payments/search') {
      return res.end(JSON.stringify({ results: pagamentos[url.searchParams.get('external_reference')] || [] }))
    }
    res.statusCode = 404
    res.end('{}')
  })
})
const aprovar = (ref, valor) => {
  (pagamentos[ref] ||= []).push({ id: ++proximoPagamento, status: 'approved', currency_id: 'BRL', transaction_amount: valor, payment_type_id: 'bank_transfer' })
}

const pasta = fs.mkdtempSync(path.join(os.tmpdir(), 'loja-site-'))
const base = `http://127.0.0.1:${PORTA_SITE}`
const postar = async (rota, corpo, tipo = 'application/json') => {
  const r = await fetch(base + rota, { method: 'POST', headers: { 'Content-Type': tipo }, body: JSON.stringify(corpo) })
  return { status: r.status, ...(await r.json().catch(() => ({}))) }
}
const pagina = async rota => { const r = await fetch(base + rota); return { status: r.status, texto: await r.text() } }
const creditos = async nick => Number(((await t.console(`creditos ver ${nick}`)).match(/([\d.,]+)\s*cr[eé]dito/i) || [, 'NaN'])[1].replace(/[.,]/g, ''))
const esperarEstado = async (id, quer, ms = 12000) => {
  let s = {}
  for (let i = 0; i < ms / 500; i++) {
    s = await (await fetch(`${base}/api/loja/pedido/${id}`)).json()
    if (s.estado === quer) break
    await t.espera(500)
  }
  return s.estado
}

t.rodar(async () => {
  if (!token) throw new Error('sem api.token no config do DiscordPlugin')
  await new Promise(ok => mp.listen(PORTA_MP, '127.0.0.1', ok))
  const site = spawn(process.execPath, [path.join(__dirname, '..', '..', 'site', 'src', 'servidor.js'), 'teste'], {
    env: {
      ...process.env, DISCORD_PASTA: pasta, SITE_PORTA: String(PORTA_SITE), SITE_URL: base,
      PLUGIN_API_URL: `http://127.0.0.1:${portaApi}`, PLUGIN_API_TOKEN: token,
      MP_ACCESS_TOKEN: 'TEST-falso', MP_API_URL: `http://127.0.0.1:${PORTA_MP}`, LOJA_CONFERIR_MS: '1000',
    },
    stdio: ['ignore', 'pipe', 'pipe'],
  })
  let saida = ''
  site.stdout.on('data', d => { saida += d })
  site.stderr.on('data', d => { saida += d })
  let bot
  try {
    for (let i = 0; i < 20 && !/em http/.test(saida); i++) await t.espera(250)
    t.verificar(/em http/.test(saida), 'o site liga com a loja aberta', saida.slice(0, 200))

    console.log('\n== A página da loja ==')
    const loja = await pagina('/loja')
    t.verificar(loja.status === 200 && (loja.texto.match(/data-pacote="/g) || []).length === 4 && /R\$\s50/.test(loja.texto), 'a loja mostra os 4 pacotes', `${loja.status}`)

    const nome = t.nomeNovo('Lsi')
    bot = await t.criarBot(nome)
    await t.espera(1500)
    const antes = await creditos(nome)

    console.log('\n== O código do jogo ==')
    let chat = []
    bot.on('messagestr', m => chat.push(m))
    let r = await postar('/api/loja/codigo', { nick: nome })
    await t.espera(800)
    const codigo = (chat.join(' ').match(/Seu código: ([A-Z0-9]{6})/) || [])[1]
    t.verificar(r.ok && codigo, 'o código chega no chat do jogo', `${JSON.stringify(r)} | ${chat.join(' | ').slice(0, 160)}`)
    r = await postar('/api/loja/codigo', { nick: nome })
    t.verificar(!r.ok && /agora há pouco/.test(r.erro), 'não manda outro código em seguida', r.erro)
    r = await postar('/api/loja/codigo', { nick: 'NinguemOnline123' })
    t.verificar(!r.ok && /precisa estar online/.test(r.erro), 'quem está offline não recebe código', r.erro)
    r = await postar('/api/loja/codigo', { nick: '<script>' })
    t.verificar(!r.ok && r.status === 400, 'nick inválido é recusado', r.erro)
    const semJson = await fetch(`${base}/api/loja/codigo`, { method: 'POST', headers: { 'Content-Type': 'text/plain' }, body: `{"nick":"${nome}"}` })
    t.verificar(semJson.status === 415, 'POST sem Content-Type JSON é recusado (formulário de outro site não passa)', `${semJson.status}`)

    console.log('\n== O pedido ==')
    const errado = codigo === 'AAAAAA' ? 'BBBBBB' : 'AAAAAA'
    r = await postar('/api/loja/pedido', { nick: nome, codigo: errado, pacote: 'p550' })
    t.verificar(!r.ok && /Código errado/.test(r.erro), 'código errado é recusado', r.erro)
    r = await postar('/api/loja/pedido', { nick: nome, codigo, pacote: 'p550' })
    t.verificar(r.ok && r.pedido && r.link.startsWith(`http://127.0.0.1:${PORTA_MP}/pagar/site-teste-`), 'o código certo cria o pedido e o link do Mercado Pago', JSON.stringify(r).slice(0, 140))
    const pedido = r.pedido
    const ref = r.link.split('/pagar/')[1]
    const pref = preferencias[ref]
    t.verificar(pref && pref.items[0].unit_price === 50 && pref.items[0].title.includes(nome) && pref.back_urls.success === `${base}/loja/pedido/${pedido}`,
      'a preferência vai com o preço, o nick e a volta pra página do pedido', JSON.stringify(pref?.items?.[0]))
    r = await postar('/api/loja/pedido', { nick: nome, codigo, pacote: 'p550' })
    t.verificar(!r.ok, 'o mesmo código não serve duas vezes', r.erro)
    let p = await pagina(`/loja/pedido/${pedido}`)
    t.verificar(p.status === 200 && /Aguardando o pagamento/.test(p.texto) && p.texto.includes(nome), 'a página do pedido mostra "aguardando"', `${p.status}`)
    t.verificar((await pagina('/loja/pedido/naoexiste')).status === 404, 'pedido que não existe dá 404')

    console.log('\n== Pagamento e entrega ==')
    aprovar(ref, 50)
    const estado = await esperarEstado(pedido, 'entregue')
    const depois = await creditos(nome)
    t.verificar(estado === 'entregue' && depois - antes === 550, 'pagamento aprovado entrega os 550 créditos', `${estado}, ${antes} → ${depois}`)
    await t.espera(3000) // mais umas voltas de conferência
    t.verificar(await creditos(nome) === depois, 'não entrega duas vezes', `${await creditos(nome)}`)
    p = await pagina(`/loja/pedido/${pedido}`)
    t.verificar(/Créditos entregues/.test(p.texto), 'a página do pedido mostra "entregues"')

    console.log('\n== Valor diferente ==')
    chat = []
    await t.espera(61000) // a espera de 60 s pro próximo código
    await postar('/api/loja/codigo', { nick: nome })
    await t.espera(800)
    const codigo2 = (chat.join(' ').match(/Seu código: ([A-Z0-9]{6})/) || [])[1]
    r = await postar('/api/loja/pedido', { nick: nome, codigo: codigo2, pacote: 'p1150' })
    const ref2 = r.link?.split('/pagar/')[1]
    aprovar(ref2, 1)
    const estado2 = await esperarEstado(r.pedido, 'problema')
    t.verificar(estado2 === 'problema' && await creditos(nome) === depois, 'pago com valor menor: nada entregue e o pedido avisa', `${estado2}`)

    console.log('\n== Regras, novidades, votar e prévia ==')
    const regras = await pagina('/regras')
    t.verificar(regras.status === 200 && regras.texto.includes('Jogo limpo') && /class="regra-num">4</.test(regras.texto), 'as regras vêm do arquivo do Discord', `${regras.status}`)
    const nov = await pagina('/novidades')
    t.verificar(nov.status === 200 && nov.texto.includes('Mais recente') && nov.texto.includes('Praça das Caixas'), 'as novidades abrem, da mais nova pra mais antiga', `${nov.status}`)
    await t.console(`testvote username=${nome} serviceName=SiteTeste`)
    await t.console('discord site')
    await t.espera(4000)
    const votar = await pagina(`/votar?nick=${nome}`)
    t.verificar(votar.status === 200 && votar.texto.includes('Festa do Voto') && /Dia 7/.test(votar.texto), 'a página de votar mostra a festa e os 7 dias da sequência', `${votar.status}`)
    t.verificar(votar.texto.includes(`<strong>${nome}</strong>`), 'e procura o nick pra mostrar a sequência', '')
    const previa = await pagina('/previa')
    t.verificar(previa.status === 200 && previa.texto.includes('previa-arte'), 'a arte da prévia abre')
    const inicio = await pagina('/')
    t.verificar(/og:image" content="http:\/\/127\.0\.0\.1:8097\/publico\/previa\.png"/.test(inicio.texto) && fs.existsSync(path.join(__dirname, '..', '..', 'site', 'publico', 'previa.png')),
      'as páginas levam a imagem de prévia (og:image), e o PNG existe', '')
  } finally {
    site.kill()
    mp.close()
    if (/Error|TypeError/.test(saida)) console.log('saída do site:\n' + saida.slice(-1500))
  }
  await t.terminar(bot)
})
