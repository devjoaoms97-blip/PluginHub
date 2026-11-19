// A página de cada parte do servidor (/servidor/<id>), aberta pelos cards da página inicial.
const { RECURSOS } = require('../conteudo');
const { layout } = require('./layout');
const { esc } = require('./util');
const tutoriais = require('../tutoriais');

const ABAS = { rank: 'o ranking de rank', clas: 'o ranking dos clãs', skills: 'o ranking das skills', votos: 'o ranking de votos', dinheiro: 'os mais ricos' };

/** Os tutoriais ligados a esta parte do servidor (os nomes vêm dos próprios arquivos). */
function guias(d) {
  const todos = tutoriais.lista();
  const lista = (d.tutoriais || []).map((s) => todos.find((t) => t.slug === s)).filter(Boolean);
  if (!lista.length) return '';
  return `<h2 class="guias-titulo">Tutoriais</h2><ul class="lista-tutoriais">${lista.map((t) => `<li><a href="/tutoriais/${t.slug}"><span aria-hidden="true">${t.icone}</span>${esc(t.titulo)}</a></li>`).join('')}</ul>`;
}

function recurso(id) {
  const i = RECURSOS.findIndex((r) => r.id === id);
  if (i < 0) return null;
  const d = RECURSOS[i];
  const outros = RECURSOS.filter((r) => r.id !== id);
  const corpo = `
<section class="cabecalho-pagina">
  <div class="largura">
    <a class="voltar" href="/">← Início</a>
    <div class="titulo-recurso surgir"><span class="titulo-icone" aria-hidden="true">${d.icone}</span><h1>${esc(d.titulo)}</h1></div>
    <p class="chamada surgir">${esc(d.resumo)}</p>
  </div>
</section>
<section class="largura recurso-grade">
  <article class="recurso-texto">
    ${d.paragrafos.map((p) => `<p class="revelar">${esc(p)}</p>`).join('')}
    <ul class="destaques">${d.lista.map((l) => `<li class="revelar">${esc(l)}</li>`).join('')}</ul>
    ${d.ranking ? `<a class="botao botao-primario revelar" href="/rankings?aba=${d.ranking}">Ver ${ABAS[d.ranking]} →</a>` : ''}
  </article>
  <aside class="comandos revelar" data-brilho>
    <h2>Comandos</h2>
    <dl>${d.comandos.map(([c, o]) => `<div><dt><button type="button" class="comando" data-copiar="${esc(c)}" title="Copiar"><code>${esc(c)}</code></button></dt><dd>${esc(o)}</dd></div>`).join('')}</dl>
    <p class="fraco">Clique num comando pra copiar.</p>
    ${guias(d)}
  </aside>
</section>
<section class="largura bloco">
  <h2 class="revelar">Mais no servidor</h2>
  <div class="cartoes cartoes-pequenos">
    ${outros.map((o) => `<a class="cartao cartao-link revelar" href="/servidor/${o.id}" data-brilho>
      <div class="cartao-icone" aria-hidden="true">${o.icone}</div><h3>${esc(o.titulo)}</h3>
    </a>`).join('')}
  </div>
</section>`;
  return layout({ titulo: d.titulo, descricao: d.resumo, corpo });
}

module.exports = { recurso };
