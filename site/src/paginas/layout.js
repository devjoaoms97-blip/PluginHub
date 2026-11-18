// A moldura de todas as páginas: cabeçalho com o menu, o conteúdo e o rodapé.
const config = require('../config');
const { esc } = require('./util');

const MENU = [
  { href: '/', nome: 'Início' },
  { href: '/rankings', nome: 'Rankings' },
  { href: '/loja', nome: 'Loja', emBreve: true },
];

function layout({ titulo, descricao, ativo, corpo, classe = '' }) {
  const menu = MENU.filter((m) => !m.emBreve)
    .map((m) => `<a href="${m.href}"${m.href === ativo ? ' aria-current="page"' : ''}>${m.nome}</a>`).join('');
  return `<!doctype html>
<html lang="pt-BR">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${esc(titulo ? `${titulo} · BrasaMC` : 'BrasaMC · Servidor de Minecraft rankup')}</title>
<meta name="description" content="${esc(descricao || 'Servidor brasileiro de Minecraft rankup: minas, boss, fazendas, clãs, eventos e muito mais.')}">
<meta name="theme-color" content="#14100e">
<link rel="icon" href="/publico/brasa.svg" type="image/svg+xml">
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Pixelify+Sans:wght@500;700&family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet">
<link rel="stylesheet" href="/publico/estilo.css">
</head>
<body class="${esc(classe)}">
<a class="pular" href="#conteudo">Pular pro conteúdo</a>
<header class="topo">
  <div class="largura topo-linha">
    <a class="marca" href="/"><img src="/publico/brasa.svg" alt="" width="28" height="28"><span>BrasaMC</span></a>
    <nav class="menu" aria-label="Principal">${menu}${config.discord ? `<a class="menu-discord" href="${esc(config.discord)}" target="_blank" rel="noopener">Discord</a>` : ''}</nav>
  </div>
</header>
<main id="conteudo">
${corpo}
</main>
<footer class="rodape">
  <div class="largura rodape-grade">
    <div>
      <a class="marca" href="/"><img src="/publico/brasa.svg" alt="" width="22" height="22"><span>BrasaMC</span></a>
      <p class="fraco">Servidor de Minecraft rankup brasileiro.</p>
    </div>
    <nav class="rodape-links" aria-label="Rodapé">
      <a href="/">Início</a><a href="/rankings">Rankings</a>${config.discord ? `<a href="${esc(config.discord)}" target="_blank" rel="noopener">Discord</a>` : ''}<a href="${esc(config.modpack)}" target="_blank" rel="noopener">Modpack</a>
    </nav>
  </div>
  <p class="largura fraco rodape-aviso">Não é um produto oficial do Minecraft. Não aprovado nem associado à Mojang ou à Microsoft.</p>
</footer>
<script src="/publico/site.js" defer></script>
</body>
</html>`;
}

module.exports = { layout, esc };
