// Páginas de texto: /regras (o mesmo arquivo do canal de regras do Discord), /novidades e /votar.
const fs = require('fs');
const path = require('path');
const config = require('../config');
const { NOVIDADES } = require('../conteudo');
const rankings = require('../rankings');
const { layout } = require('./layout');
const { esc, numero, reais, cabeca, linkJogador } = require('./util');

// ---------------------------------------------------------------- regras

const REGRAS = path.join(__dirname, '..', '..', '..', 'discord-bot', 'conteudo', 'regras.md');

/** O pouco de markdown do Discord que as regras usam: **negrito**, *itálico*, `código` e {#canal}. */
function markdown(linha) {
  return esc(linha)
    .replace(/\{#([a-z0-9-]+)\}/g, (_, canal) => (config.discord ? `<a href="${esc(config.discord)}" target="_blank" rel="noopener">#${canal}</a> no Discord` : `#${canal} no Discord`))
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.+?)\*/g, '<em>$1</em>')
    .replace(/`(.+?)`/g, '<code>$1</code>');
}

function paginaRegras() {
  const linhas = fs.existsSync(REGRAS) ? fs.readFileSync(REGRAS, 'utf8').split(/\r?\n/) : ['# Regras', 'As regras ainda não foram publicadas.'];
  const titulo = (linhas.find((l) => l.startsWith('# ')) || '# Regras').slice(2).replace(/^📜\s*/, '');
  const blocos = [];
  let atual = null;
  for (const l of linhas) {
    if (l.startsWith('# ') || !l.trim()) continue;
    const secao = /^\*\*([^*]+)\*\*$/.exec(l.trim());
    if (secao) {
      atual = { titulo: secao[1], linhas: [] };
      blocos.push(atual);
    } else {
      if (!atual) { atual = { titulo: null, linhas: [] }; blocos.push(atual); }
      atual.linhas.push(l);
    }
  }
  const corpo = `
<section class="cabecalho-pagina">
  <div class="largura">
    <h1 class="surgir">📜 ${esc(titulo)}</h1>
    <p class="chamada surgir">As mesmas do canal de regras do Discord.</p>
  </div>
</section>
<section class="largura bloco regras">
  ${blocos.map((b) => (b.titulo
    ? `<article class="regra-bloco revelar" data-brilho><h2>${esc(b.titulo)}</h2>${b.linhas.map((l) => {
      const n = /^\*\*(\d+)\.\*\*\s*(.*)$/.exec(l.trim());
      return n ? `<p class="regra"><span class="regra-num">${n[1]}</span><span>${markdown(n[2])}</span></p>` : `<p>${markdown(l)}</p>`;
    }).join('')}</article>`
    : `<div class="regras-intro revelar">${b.linhas.map((l) => `<p>${markdown(l)}</p>`).join('')}</div>`)).join('')}
</section>`;
  return layout({ titulo: 'Regras', descricao: 'As regras do BrasaMC, no servidor e no Discord.', ativo: '/regras', corpo });
}

// ---------------------------------------------------------------- novidades

const dataLonga = (iso) => new Date(`${iso}T12:00:00`).toLocaleDateString('pt-BR', { day: 'numeric', month: 'long', year: 'numeric' });

function paginaNovidades() {
  const corpo = `
<section class="cabecalho-pagina">
  <div class="largura">
    <h1 class="surgir">Novidades</h1>
    <p class="chamada surgir">O que entrou no servidor, da mais nova pra mais antiga.</p>
  </div>
</section>
<section class="largura bloco">
  <ol class="linha-tempo">
    ${NOVIDADES.map((n, i) => `<li class="novidade revelar${i === 0 ? ' novidade-nova' : ''}">
      <time datetime="${n.data}">${dataLonga(n.data)}</time>
      <article class="novidade-cartao" data-brilho>
        ${i === 0 ? '<span class="selo selo-novo">Mais recente</span>' : ''}
        <h2>${esc(n.titulo)}</h2>
        <ul>${n.itens.map(([icone, texto, link]) => `<li><span aria-hidden="true">${icone}</span><span>${esc(texto)}${link ? ` <a href="/servidor/${link}">Saiba mais</a>` : ''}</span></li>`).join('')}</ul>
      </article>
    </li>`).join('')}
  </ol>
</section>`;
  return layout({ titulo: 'Novidades', descricao: 'As últimas atualizações do BrasaMC.', ativo: '/novidades', corpo });
}

// ---------------------------------------------------------------- votar

function paginaVotar({ foto, nick }) {
  const v = foto?.voto || {};
  const r = rankings.calcular(foto);
  const sites = v.sites || [];
  const meta = v.festaMeta || 50;
  const festa = Math.min(meta, v.festa || 0);
  const premios = v.premiosMes || [3, 2, 1];
  const seq = v.sequencia || [];
  let eu = null;
  if (nick) eu = r?.porNick.get(String(nick).toLowerCase()) || false;

  const corpo = `
<section class="cabecalho-pagina">
  <div class="largura">
    <h1 class="surgir">🗳️ Vote e ganhe chaves</h1>
    <p class="chamada surgir">Cada voto ajuda o servidor a crescer e te dá uma <strong>Chave de Voto</strong> pra abrir na Praça das Caixas.</p>
  </div>
</section>
<section class="largura votar-grade">
  <div>
    <h2 class="revelar">Onde votar</h2>
    ${sites.length ? `<div class="sites">${sites.map((s, i) => `<a class="site-voto revelar" href="${esc(s.url)}" target="_blank" rel="noopener" data-brilho>
      <span class="site-num">${i + 1}</span><strong>${esc(s.nome)}</strong><span class="site-ir" aria-hidden="true">Votar →</span></a>`).join('')}</div>
      <p class="fraco pequeno">Ao votar, use o seu nick do jogo. Dá pra votar em todos os sites, todo dia.</p>`
    : '<div class="aviso revelar"><p>Os sites de votação entram aqui em breve. Enquanto isso, no jogo, o <code>/votar</code> mostra tudo.</p></div>'}

    <h2 class="revelar">O que você ganha</h2>
    <div class="cartoes cartoes-pequenos">
      <div class="cartao revelar"><div class="cartao-icone" aria-hidden="true">🔑</div><h3>Chave por voto</h3><p>Cada voto, em cada site, dá uma Chave de Voto.</p></div>
      <div class="cartao revelar"><div class="cartao-icone" aria-hidden="true">🔥</div><h3>Sequência</h3><p>O primeiro voto do dia paga um bônus em coins, que cresce até o ${seq.length || 7}º dia seguido, e no último vem ${v.chaveExtra || 1} chave extra.</p></div>
      <div class="cartao revelar"><div class="cartao-icone" aria-hidden="true">🎉</div><h3>Festa do Voto</h3><p>A cada ${numero(meta)} votos do servidor, todo mundo online ganha ${v.festaChaves || 1} chave.</p></div>
      <div class="cartao revelar"><div class="cartao-icone" aria-hidden="true">🏆</div><h3>Ranking do mês</h3><p>Os 3 que mais votam no mês levam ${premios.join(', ')} Chaves do Boss.</p></div>
    </div>
    ${seq.length ? `<div class="sequencia revelar" aria-label="Bônus de cada dia da sequência">
      ${seq.map((p, i) => `<div class="seq-dia${i === seq.length - 1 ? ' seq-ultimo' : ''}"><span>Dia ${i + 1}</span><strong>${Number(p).toLocaleString('pt-BR')}%</strong>${i === seq.length - 1 ? '<em>+ chave</em>' : ''}</div>`).join('')}
    </div>
    <p class="fraco pequeno">% do preço do seu próximo rank (mínimo ${reais(v.sequenciaMinimo || 200)}). Faltou um dia, volta pro dia 1.</p>` : ''}
  </div>

  <aside class="votar-lado">
    <div class="festa revelar" data-brilho>
      <h3>🎉 Festa do Voto</h3>
      <progress max="${meta}" value="${festa}" aria-label="${festa} de ${meta} votos"></progress>
      <p><strong>${numero(festa)}</strong> de ${numero(meta)} votos · faltam <strong>${numero(meta - festa)}</strong></p>
    </div>

    <form class="busca busca-voto revelar" action="/votar" method="get" role="search">
      <label class="sr" for="voto-nick">Ver minha sequência</label>
      <input id="voto-nick" name="nick" placeholder="Seu nick pra ver a sequência" autocomplete="off" maxlength="16" value="${esc(nick || '')}" pattern="[A-Za-z0-9_]{2,16}" required>
      <button type="submit" aria-label="Ver">🔍</button>
    </form>
    ${eu === false ? `<p class="mensagem erro">Ninguém chamado “${esc(nick)}” no servidor.</p>` : ''}
    ${eu ? `<div class="minha-seq revelar">
      ${cabeca(eu.nick, 40)}
      <div><strong>${esc(eu.nick)}</strong>
        <p>${eu.votos ? `${eu.votos.votouHoje ? '✅ Já votou hoje' : '⏳ Ainda não votou hoje'} · 🔥 ${numero(eu.votos.sequencia)} dia${eu.votos.sequencia === 1 ? '' : 's'} seguido${eu.votos.sequencia === 1 ? '' : 's'}<br>${numero(eu.votos.mes)} votos este mês · ${numero(eu.votos.total)} no total` : 'Ainda não votou nenhuma vez.'}</p>
      </div>
    </div>` : ''}

    <div class="top-votos revelar">
      <h3>Top do mês</h3>
      ${r && r.votos.top.length ? `<ol>${r.votos.top.slice(0, 10).map((x, i) => `<li><a href="${linkJogador(x.j.nick)}"><span class="mini-pos${i < 3 ? ` pos-${i + 1}` : ''}">${i + 1}</span>${cabeca(x.j.nick, 22)}<span class="mini-nome">${esc(x.j.nick)}</span><span class="mini-valor">${numero(x.valor.mes)}</span></a></li>`).join('')}</ol>
      <a class="pequeno" href="/rankings?aba=votos">Ver o ranking inteiro →</a>` : '<p class="fraco">Ninguém votou este mês ainda. Seja o primeiro!</p>'}
    </div>
  </aside>
</section>`;
  return layout({ titulo: 'Votar', descricao: 'Vote no BrasaMC e ganhe Chaves de Voto, bônus de sequência e prêmios no ranking do mês.', ativo: '/votar', corpo });
}

module.exports = { paginaRegras, paginaNovidades, paginaVotar };
