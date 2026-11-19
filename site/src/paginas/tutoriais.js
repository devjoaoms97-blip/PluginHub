// /tutoriais: os tutoriais do Discord por grupo (Skills primeiro). /tutoriais/<slug>: um tutorial, com
// cada seção num card e os outros do mesmo grupo do lado.
const tutoriais = require('../tutoriais');
const { layout } = require('./layout');
const { esc } = require('./util');

function paginaTutoriais() {
  const todos = tutoriais.lista();
  const corpo = `
<section class="cabecalho-pagina">
  <div class="largura">
    <h1 class="surgir">Tutoriais</h1>
    <p class="chamada surgir">Tudo o que dá pra fazer no servidor, explicado. São os mesmos do fórum do Discord.</p>
    <nav class="chips chips-grupos surgir" aria-label="Grupos">${tutoriais.GRUPOS.filter((g) => todos.some((t) => t.grupo === g.id))
      .map((g) => `<a class="chip" href="#${g.id}">${g.icone} ${esc(g.titulo)}</a>`).join('')}</nav>
  </div>
</section>
${tutoriais.GRUPOS.map((g) => {
    const doGrupo = todos.filter((t) => t.grupo === g.id);
    if (!doGrupo.length) return '';
    return `<section class="largura bloco" id="${g.id}">
  <h2 class="revelar">${g.icone} ${esc(g.titulo)}</h2>
  <div class="cartoes cartoes-tutoriais">
    ${doGrupo.map((t, i) => `<a class="cartao cartao-link cartao-tutorial revelar" href="/tutoriais/${t.slug}" data-brilho data-atraso="${i % 4}">
      <div class="cartao-icone" aria-hidden="true">${t.icone}</div><h3>${esc(t.titulo)}</h3><span class="cartao-mais" aria-hidden="true">Ler →</span>
    </a>`).join('')}
  </div>
</section>`;
  }).join('')}`;
  return layout({ titulo: 'Tutoriais', descricao: 'Os tutoriais da BrasaMC: skills, rankup, dinheiro, clãs, eventos, tesouros e mais.', ativo: '/tutoriais', corpo });
}

function paginaTutorial(slug) {
  const t = tutoriais.ler(slug);
  if (!t) return null;
  const grupo = tutoriais.GRUPOS.find((g) => g.id === t.grupo);
  const irmaos = tutoriais.lista().filter((x) => x.grupo === t.grupo);
  const corpo = `
<section class="cabecalho-pagina">
  <div class="largura">
    <a class="voltar" href="/tutoriais#${t.grupo}">← Tutoriais${grupo ? ` · ${esc(grupo.titulo)}` : ''}</a>
    <div class="titulo-recurso surgir"><span class="titulo-icone" aria-hidden="true">${t.icone}</span><h1>${esc(t.titulo)}</h1></div>
  </div>
</section>
<section class="largura tutorial-grade">
  <article class="regras">
    ${t.blocos.map((b) => (b.titulo
    ? `<div class="regra-bloco revelar"><h2>${esc(b.titulo).replace(/\*\*/g, '')}</h2>${b.html}</div>`
    : `<div class="regras-intro revelar">${b.html}</div>`)).join('')}
  </article>
  ${irmaos.length > 1 ? `<aside class="comandos tutorial-lado">
    <h2>${grupo ? `${grupo.icone} ${esc(grupo.titulo)}` : 'Mais tutoriais'}</h2>
    <ul class="lista-tutoriais">${irmaos.map((x) => `<li><a href="/tutoriais/${x.slug}"${x.slug === t.slug ? ' aria-current="page"' : ''}><span aria-hidden="true">${x.icone}</span>${esc(x.titulo)}</a></li>`).join('')}</ul>
    <a class="pequeno" href="/tutoriais">Todos os tutoriais →</a>
  </aside>` : ''}
</section>`;
  return layout({ titulo: t.titulo, descricao: `${t.titulo}: tutorial da BrasaMC.`, ativo: '/tutoriais', corpo });
}

module.exports = { paginaTutoriais, paginaTutorial };
