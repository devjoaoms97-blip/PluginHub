// Site, etapa 1 (2026-11-18): a foto do DiscordPlugin (GET /site) traz o jogador com dinheiro, rank,
// prestígio, skills, clã e votos, e a lista dos clãs; o site (pasta site/) liga, mostra o status ao vivo com
// o jogador online, o próximo boss e o IP, e não deixa ler arquivo fora da pasta publico/.
// Uso: node teste-site.js (com o servidor de teste ligado)
const t = require('./lib')
const fs = require('fs')
const path = require('path')
const { spawn } = require('child_process')

const MC = process.env.MC_TESTE || 'C:\\mc-teste'
const PORTA_SITE = 8099
const cfg = fs.readFileSync(path.join(MC, 'plugins', 'DiscordPlugin', 'config.yml'), 'utf8')
const porta = (cfg.match(/^\s+porta: (\d+)/m) || [, '8155'])[1]
const token = (cfg.match(/^\s+token: "?([0-9a-f]+)"?/m) || [])[1]

const api = async rota => (await fetch(`http://127.0.0.1:${porta}${rota}`, { headers: { Authorization: `Bearer ${token}` } })).json()
const pagina = async rota => {
  const r = await fetch(`http://127.0.0.1:${PORTA_SITE}${rota}`)
  return { status: r.status, texto: await r.text(), csp: r.headers.get('content-security-policy') }
}

t.rodar(async () => {
  if (!token) throw new Error('sem api.token no config do DiscordPlugin')
  const nome = t.nomeNovo('Sit')
  const tag = 'S' + nome.slice(-3).toUpperCase()
  const bot = await t.criarBot(nome)
  await t.espera(1500)
  await t.console(`eco set ${nome} 1000000`)
  await t.console(`ranks admin set ${nome} fazendeiro`)
  await t.console(`skills admin setlevel ${nome} minerador 17`)
  await t.capturar(bot, () => bot.chat(`/cla criar ${tag} Clã do Site`), 1500)
  // o banco do clã desempata o ranking (entre 68 clãs de teste com 0 pontos, só o top 50 aparece)
  await t.capturar(bot, () => bot.chat('/cla depositar 500000'), 1200)
  await t.console(`eco set ${nome} 123456`) // depois do clã e do depósito
  await t.console(`testvote username=${nome} serviceName=SiteTeste`)
  await t.espera(1000)

  console.log('\n== A foto do DiscordPlugin ==')
  const antes = (await api('/site')).geradoEm || 0
  const resposta = await t.console('discord site')
  t.verificar(/Refazendo a foto/.test(resposta), '/discord site manda refazer a foto (fora da thread do servidor)', resposta)
  let foto = {}
  const inicio = Date.now()
  for (let i = 0; i < 60 && !(foto.geradoEm > antes); i++) { await t.espera(500); foto = await api('/site') }
  t.verificar(foto.geradoEm > antes, 'a foto nova fica pronta', `${((Date.now() - inicio) / 1000).toFixed(1)} s, ${foto.jogadores?.length} jogadores`)
  t.verificar(foto.ok && Array.isArray(foto.jogadores), 'GET /site devolve a foto', JSON.stringify(foto).slice(0, 120))
  const eu = foto.jogadores.find(j => j.nick === nome) || {}
  // R$ 123.456 e o bônus do primeiro voto do dia (sequência, mínimo R$ 200)
  t.verificar(eu.online === true && eu.dinheiro >= 123456 && eu.dinheiro <= 125000, 'o jogador está online e com o dinheiro certo', `${eu.online} ${eu.dinheiro}`)
  t.verificar(eu.rank?.id === 'fazendeiro' && eu.rank?.nome && eu.rank?.posicao > 1 && eu.rank?.total >= eu.rank?.posicao,
    'traz o rank (id, nome sem cor, posição e total)', JSON.stringify(eu.rank))
  t.verificar(eu.skills?.minerador?.nivel === 17 && eu.skills?.minerador?.profissao === true && eu.skills?.espadas?.nivel >= 1,
    'traz as skills (Minerador 17, de profissão)', JSON.stringify(eu.skills?.minerador))
  t.verificar(eu.cla?.tag === tag && eu.cla?.cargo === 'LIDER', 'traz o clã e o cargo', JSON.stringify(eu.cla))
  t.verificar(eu.votos?.mes >= 1 && eu.votos?.votouHoje === true, 'traz os votos do mês', JSON.stringify(eu.votos))
  const cla = (foto.clas || []).find(c => c.tag === tag)
  t.verificar(cla && cla.membros === 1 && cla.nomes.includes(nome) && cla.nome === 'Clã do Site', 'a lista dos clãs tem o clã novo', JSON.stringify(cla))
  t.verificar(typeof foto.voto?.festaMeta === 'number', 'traz o resumo do voto', JSON.stringify(foto.voto))

  console.log('\n== O site ==')
  const site = spawn(process.execPath, [path.join(__dirname, '..', '..', 'site', 'src', 'servidor.js'), 'teste'], {
    env: { ...process.env, SITE_PORTA: String(PORTA_SITE), SERVIDOR_IP: 'jogar.teste.local' },
    stdio: ['ignore', 'pipe', 'pipe'],
  })
  let saida = ''
  site.stdout.on('data', d => { saida += d })
  site.stderr.on('data', d => { saida += d })
  try {
    for (let i = 0; i < 20 && !/em http/.test(saida); i++) await t.espera(250)
    t.verificar(/em http/.test(saida), 'o site liga', saida.slice(0, 200))
    const inicio = await pagina('/')
    t.verificar(inicio.status === 200 && inicio.texto.includes('jogar.teste.local'), 'a página inicial abre com o IP', `${inicio.status}`)
    t.verificar(inicio.texto.includes(`<span>${nome}</span>`) && /<strong>Online<\/strong>/.test(inicio.texto), 'o status mostra o servidor online e o jogador', '')
    t.verificar(/Próximo boss/.test(inicio.texto), 'mostra o próximo boss', '')
    t.verificar(/script-src 'self'/.test(inicio.csp || ''), 'a página vai com a Content-Security-Policy', inicio.csp)
    const status = JSON.parse((await pagina('/api/status')).texto)
    t.verificar(status.online === true && status.quantos >= 1 && status.html.includes(nome), '/api/status atualiza o cartão', JSON.stringify(status).slice(0, 120))
    t.verificar(inicio.texto.includes('href="/servidor/rankup"') && inicio.texto.includes('Quem está no topo'), 'os cards levam pra página de cada parte, e a prévia dos rankings aparece', '')
    const rec = await pagina('/servidor/bosses')
    t.verificar(rec.status === 200 && rec.texto.includes('/warp boss') && (await pagina('/servidor/nada')).status === 404, 'a página de uma parte do servidor abre (e uma que não existe dá 404)', `${rec.status}`)
    const rk = await pagina('/rankings?aba=clas')
    t.verificar(rk.status === 200 && /id="painel-clas" [^>]*data-painel="clas">/.test(rk.texto) && rk.texto.includes(`>${tag}<`), 'os rankings abrem na aba dos clãs, com o clã novo', '')
    t.verificar(/data-painel="dinheiro" hidden/.test(rk.texto) && rk.texto.includes('data-sub-painel="minerador"'), 'as outras abas vêm escondidas, e a das skills tem uma lista por skill', '')
    const perfil = await pagina(`/jogador/${nome.toLowerCase()}`)
    t.verificar(perfil.status === 200 && perfil.texto.includes(`<h1 class="surgir">${nome}`) && perfil.texto.includes('Fazendeiro') && perfil.texto.includes(tag) && /value="17"/.test(perfil.texto),
      'o perfil abre pelo nick (sem diferença de maiúsculas), com rank, clã e o Minerador 17', `${perfil.status}`)
    const busca = await fetch(`http://127.0.0.1:${PORTA_SITE}/jogador?nick=${nome}`, { redirect: 'manual' })
    t.verificar(busca.status === 302 && busca.headers.get('location') === `/jogador/${nome}`, 'a busca leva pro perfil', `${busca.status} ${busca.headers.get('location')}`)
    const ninguem = await pagina('/jogador/NinguemComEsseNick')
    const quebrado = await pagina('/jogador/%E0%A4%A')
    t.verificar(ninguem.status === 404 && quebrado.status === 404, 'nick que não existe (ou endereço quebrado) dá 404', `${ninguem.status} ${quebrado.status}`)
    const css = await pagina('/publico/estilo.css')
    t.verificar(css.status === 200, 'o CSS carrega', `${css.status}`)
    const fora = await pagina('/publico/..%2Fsrc%2Fconfig.js')
    t.verificar(fora.status === 404, 'não lê arquivo fora de publico/', `${fora.status}`)
  } finally {
    site.kill()
  }

  // limpeza: desfaz o clã
  await t.capturar(bot, () => bot.chat('/cla desfazer'), 800)
  await t.capturar(bot, () => bot.chat('/cla desfazer confirmar'), 800)
  await t.terminar(bot)
})
