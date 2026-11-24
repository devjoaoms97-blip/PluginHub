// Site do BrasaMC: um servidor HTTP do próprio Node, sem dependências. Escuta em 127.0.0.1 (na VPS, o
// Caddy fica na frente com o domínio e o HTTPS) e fala com o servidor do Minecraft pela API local do
// DiscordPlugin (src/dados.js).
//
//   GET /                  página inicial
//   GET /servidor/<id>     a página de cada parte do servidor (os cards da inicial)
//   GET /rankings          rankings em abas (?aba=dinheiro|rank|skills|clas|votos, &skill=<id>)
//   GET /jogador?nick=     a busca: leva pro perfil
//   GET /jogador/<nick>    o perfil do jogador
//   GET /loja              a loja de créditos;  GET /loja/pedido/<id>  a página do pedido
//   POST /api/loja/codigo  manda o código pro jogo;  POST /api/loja/pedido  cria o pedido e o link
//   GET /api/loja/pedido/<id>  o miolo da página do pedido (o site.js atualiza a cada 5 s)
//   GET /tutoriais, /tutoriais/<nome>  os tutoriais do Discord (o /celular leva pro de celular)
//   GET /regras, /novidades, /votar (?nick= mostra a sequência)
//   GET /previa            a arte da imagem de prévia dos links (o gerar-previa.js tira a foto dela)
//   GET /api/status        o status ao vivo em HTML (o site.js troca o cartão a cada 30 s)
//   GET /publico/...       CSS, JS e imagens da pasta publico/
const http = require('http');
const fs = require('fs');
const path = require('path');
const config = require('./config');
const dados = require('./dados');
const { inicio, conteudoStatus } = require('./paginas/inicio');
const { layout } = require('./paginas/layout');
const { recurso } = require('./paginas/recurso');
const { paginaRankings } = require('./paginas/rankings');
const { paginaJogador, naoAchou: jogadorNaoAchado } = require('./paginas/jogador');
const { paginaLoja, paginaPedido, conteudoPedido } = require('./paginas/loja');
const { paginaRegras, paginaNovidades, paginaVotar } = require('./paginas/extras');
const { paginaTutoriais, paginaTutorial } = require('./paginas/tutoriais');
const { paginaPrevia } = require('./paginas/previa');
const loja = require('./loja');

const PUBLICO = path.join(__dirname, '..', 'publico');
const TIPOS = { '.css': 'text/css; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.svg': 'image/svg+xml', '.png': 'image/png', '.ico': 'image/x-icon', '.webp': 'image/webp' };

const SEGURANCA = {
  'X-Content-Type-Options': 'nosniff',
  'Referrer-Policy': 'strict-origin-when-cross-origin',
  'X-Frame-Options': 'DENY',
  'Content-Security-Policy': "default-src 'self'; img-src 'self' https://mc-heads.net data:; style-src 'self' https://fonts.googleapis.com; font-src https://fonts.gstatic.com; script-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'",
};

function enviar(res, status, corpo, tipo = 'text/html; charset=utf-8', extra = {}) {
  res.writeHead(status, { 'Content-Type': tipo, ...SEGURANCA, ...extra });
  res.end(corpo);
}

function arquivoPublico(res, rota) {
  const nome = path.normalize(decodificar(rota.slice('/publico/'.length)));
  const arquivo = path.join(PUBLICO, nome);
  if (!arquivo.startsWith(PUBLICO + path.sep) || !fs.existsSync(arquivo) || !fs.statSync(arquivo).isFile()) return naoAchou(res);
  enviar(res, 200, fs.readFileSync(arquivo), TIPOS[path.extname(arquivo)] || 'application/octet-stream', { 'Cache-Control': 'public, max-age=300' });
}

/** decodeURIComponent que não quebra com % solto (vira texto vazio, e a página dá 404). */
function decodificar(texto) {
  try {
    return decodeURIComponent(texto);
  } catch {
    return '';
  }
}

function redirecionar(res, destino) {
  res.writeHead(302, { Location: destino, ...SEGURANCA });
  res.end();
}

function naoAchou(res) {
  enviar(res, 404, layout({
    titulo: 'Página não encontrada',
    corpo: '<section class="largura bloco vazio"><h1>Nada por aqui</h1><p>Essa página não existe. <a href="/">Voltar pro início</a>.</p></section>',
  }));
}

/** O IP de quem pediu. Atrás do Caddy (conexão de 127.0.0.1), vale o X-Forwarded-For. */
function ipDe(req) {
  const direto = req.socket.remoteAddress || '';
  const repassado = req.headers['x-forwarded-for'];
  if (repassado && /^(::ffff:)?127\.0\.0\.1$|^::1$/.test(direto)) return String(repassado).split(',')[0].trim();
  return direto;
}

/** Corpo JSON de até 4 KB. Só aceita Content-Type JSON (um formulário de outro site não consegue mandar). */
function lerJson(req) {
  return new Promise((ok, falha) => {
    if (!/^application\/json/i.test(req.headers['content-type'] || '')) return falha(Object.assign(new Error('JSON esperado'), { status: 415 }));
    let tamanho = 0;
    const partes = [];
    req.on('data', (p) => {
      tamanho += p.length;
      if (tamanho > 4096) { falha(Object.assign(new Error('grande demais'), { status: 413 })); req.destroy(); return; }
      partes.push(p);
    });
    req.on('end', () => {
      try { ok(JSON.parse(Buffer.concat(partes).toString('utf8') || '{}')); } catch { falha(Object.assign(new Error('JSON inválido'), { status: 400 })); }
    });
    req.on('error', falha);
  });
}

const json = (res, status, obj) => enviar(res, status, JSON.stringify(obj), 'application/json; charset=utf-8', { 'Cache-Control': 'no-store' });

async function atender(req, res) {
  const url = new URL(req.url, 'http://site');
  const rota = url.pathname.replace(/\/+$/, '') || '/';
  if (req.method === 'POST' && (rota === '/api/loja/codigo' || rota === '/api/loja/pedido')) {
    let corpo;
    try {
      corpo = await lerJson(req);
    } catch (e) {
      return json(res, e.status || 400, { ok: false, erro: e.message });
    }
    const r = rota === '/api/loja/codigo' ? await loja.pedirCodigo(ipDe(req), corpo) : await loja.criarPedido(ipDe(req), corpo);
    return json(res, r.ok ? 200 : 400, r);
  }
  if (req.method !== 'GET' && req.method !== 'HEAD') return enviar(res, 405, 'Método não permitido', 'text/plain; charset=utf-8');

  if (rota.startsWith('/publico/')) return arquivoPublico(res, rota);
  if (rota === '/') {
    const [status, foto] = await Promise.all([dados.status(), dados.foto()]);
    return enviar(res, 200, inicio({ status, foto }), undefined, { 'Cache-Control': 'no-cache' });
  }
  if (rota.startsWith('/servidor/')) {
    const html = recurso(rota.slice('/servidor/'.length));
    return html ? enviar(res, 200, html) : naoAchou(res);
  }
  if (rota === '/rankings') {
    const foto = await dados.foto();
    return enviar(res, 200, paginaRankings({ foto, aba: url.searchParams.get('aba') || 'dinheiro', skill: url.searchParams.get('skill') || '' }),
      undefined, { 'Cache-Control': 'no-cache' });
  }
  if (rota === '/jogador') {
    const nick = (url.searchParams.get('nick') || '').trim();
    if (!nick) return redirecionar(res, '/rankings');
    return redirecionar(res, `/jogador/${encodeURIComponent(nick)}`);
  }
  if (rota.startsWith('/jogador/')) {
    const nick = decodificar(rota.slice('/jogador/'.length)).slice(0, 32);
    const foto = await dados.foto();
    const html = paginaJogador({ foto, nick });
    return html ? enviar(res, 200, html, undefined, { 'Cache-Control': 'no-cache' }) : enviar(res, 404, jogadorNaoAchado({ foto, nick }));
  }
  if (rota === '/loja') return enviar(res, 200, paginaLoja(), undefined, { 'Cache-Control': 'no-cache' });
  if (rota.startsWith('/loja/pedido/')) {
    const html = paginaPedido(decodificar(rota.slice('/loja/pedido/'.length)));
    return html ? enviar(res, 200, html, undefined, { 'Cache-Control': 'no-store', 'Referrer-Policy': 'no-referrer' }) : naoAchou(res);
  }
  if (rota.startsWith('/api/loja/pedido/')) {
    const s = loja.situacao(decodificar(rota.slice('/api/loja/pedido/'.length)));
    return s ? json(res, 200, { estado: s.estado, html: conteudoPedido(s) }) : json(res, 404, { erro: 'pedido não encontrado' });
  }
  if (rota === '/regras') return enviar(res, 200, paginaRegras());
  if (rota === '/novidades') return enviar(res, 200, paginaNovidades());
  if (rota === '/celular') return redirecionar(res, '/tutoriais/jogar-pelo-celular');
  if (rota === '/tutoriais') return enviar(res, 200, paginaTutoriais());
  if (rota.startsWith('/tutoriais/')) {
    const html = paginaTutorial(decodificar(rota.slice('/tutoriais/'.length)));
    return html ? enviar(res, 200, html) : naoAchou(res);
  }
  if (rota === '/votar') {
    const foto = await dados.foto();
    const nick = (url.searchParams.get('nick') || '').trim().slice(0, 16);
    return enviar(res, 200, paginaVotar({ foto, nick }), undefined, { 'Cache-Control': 'no-cache' });
  }
  if (rota === '/previa') return enviar(res, 200, paginaPrevia());
  if (rota === '/api/status') {
    const status = await dados.status();
    return enviar(res, 200, JSON.stringify({ online: status.online, quantos: status.online ? (status.jogadores || []).length : 0, html: conteudoStatus(status) }),
      'application/json; charset=utf-8', { 'Cache-Control': 'no-store' });
  }
  return naoAchou(res);
}

const servidor = http.createServer((req, res) => {
  atender(req, res).catch((e) => {
    console.error('[site]', req.url, e);
    if (!res.headersSent) enviar(res, 500, 'Erro no site. Tente de novo.', 'text/plain; charset=utf-8');
  });
});

loja.iniciar();
servidor.listen(config.porta, config.endereco, () => {
  console.log(`[site] BrasaMC (${config.servidor}) em http://${config.endereco}:${config.porta}`);
});
