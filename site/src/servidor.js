// Site da BrasaMC: um servidor HTTP do próprio Node, sem dependências. Escuta em 127.0.0.1 (na VPS, o
// Caddy fica na frente com o domínio e o HTTPS) e fala com o servidor do Minecraft pela API local do
// DiscordPlugin (src/dados.js).
//
//   GET /                  página inicial
//   GET /servidor/<id>     a página de cada parte do servidor (os cards da inicial)
//   GET /rankings          rankings em abas (?aba=dinheiro|rank|skills|clas|votos, &skill=<id>)
//   GET /jogador?nick=     a busca: leva pro perfil
//   GET /jogador/<nick>    o perfil do jogador
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

async function atender(req, res) {
  const url = new URL(req.url, 'http://site');
  const rota = url.pathname.replace(/\/+$/, '') || '/';
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

servidor.listen(config.porta, config.endereco, () => {
  console.log(`[site] BrasaMC (${config.servidor}) em http://${config.endereco}:${config.porta}`);
});
