// /previa: a arte da imagem que aparece quando alguém manda o link do site no Discord ou no WhatsApp
// (og:image). É uma página de 1200x630; o tools/gerar-previa.js tira uma foto dela com o Edge (ou o
// Chrome) e salva em publico/previa.png. Mudou a arte? Rode de novo e suba o PNG.
const config = require('../config');
const { esc } = require('./util');

/** Faíscas em posições sorteadas sempre do mesmo jeito (a imagem sai igual a cada vez). */
function faiscas() {
  let semente = 7;
  const sorteio = () => ((semente = (semente * 16807) % 2147483647) / 2147483647);
  const cores = ['#f26b1d', '#ffb347', '#ffd166'];
  return Array.from({ length: 70 }, () => {
    const t = 2 + sorteio() * 5;
    const y = sorteio() * 630;
    return `<rect x="${(sorteio() * 1200).toFixed(0)}" y="${y.toFixed(0)}" width="${t.toFixed(1)}" height="${t.toFixed(1)}" fill="${cores[Math.floor(sorteio() * 3)]}" opacity="${(0.25 + (y / 630) * 0.7).toFixed(2)}"/>`;
  }).join('');
}

function paginaPrevia() {
  return `<!doctype html>
<html lang="pt-BR">
<head>
<meta charset="utf-8">
<title>Prévia BrasaMC</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Pixelify+Sans:wght@700&family=Inter:wght@500;700;800&display=swap" rel="stylesheet">
<link rel="stylesheet" href="/publico/estilo.css">
</head>
<body class="previa">
  <div class="previa-arte">
    <svg class="previa-faiscas" viewBox="0 0 1200 630" aria-hidden="true">${faiscas()}</svg>
    <img class="previa-chama" src="/publico/brasa.svg" alt="" width="190" height="190">
    <div class="previa-texto">
      <p class="previa-sobre">Servidor de Minecraft · Rankup</p>
      <h1>Brasa<span>MC</span></h1>
      <p class="previa-lista">⛏️ 12 minas · 🐉 Bosses · 🌾 Fazendas · 🛡️ Clãs · ⚔️ Eventos</p>
      ${config.ip ? `<p class="previa-ip">${esc(config.ip)}</p>` : '<p class="previa-ip">Entre no Discord e jogue com a gente</p>'}
    </div>
  </div>
</body>
</html>`;
}

module.exports = { paginaPrevia };
