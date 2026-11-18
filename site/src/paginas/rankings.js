// /rankings: abas (mais ricos, rank, skills, clãs e votos do mês), com pódio pros 3 primeiros e a lista
// até o 50º. Todas as abas vêm no HTML; o site.js troca entre elas sem recarregar (e sem JS, cada aba é
// um link com ?aba=). Na aba das skills, um seletor escolhe o total ou uma skill.
const rankings = require('../rankings');
const { layout } = require('./layout');
const { esc, numero, reais, reaisCurto, cabeca, corMc, linkJogador, tempoAtras } = require('./util');

const ABAS = [
  { id: 'dinheiro', nome: 'Mais ricos', icone: '💰' },
  { id: 'rank', nome: 'Rank', icone: '⛏️' },
  { id: 'skills', nome: 'Skills', icone: '📈' },
  { id: 'clas', nome: 'Clãs', icone: '🛡️' },
  { id: 'votos', nome: 'Votos do mês', icone: '🗳️' },
];

const textoRank = (v) => `<span class="${corMc(v.cor)}">${v.prestigio ? `<span class="prestigio">✦${v.prestigio}</span> ` : ''}${esc(v.nome)}</span>`;
const textoSkill = (v) => `${v.prestigio ? `<span class="prestigio">✦${v.prestigio}</span> ` : ''}Nível ${numero(v.nivel)}`;

/** Pódio (3 primeiros) e a lista do 4º em diante. `valor` e `detalhe` montam o que aparece de cada um. */
function lista(top, valor, detalhe, vazio) {
  if (!top.length) return `<p class="vazio-lista">${vazio}</p>`;
  const podio = top.slice(0, 3);
  const ordemPodio = [podio[1], podio[0], podio[2]].filter(Boolean); // 2º, 1º, 3º
  return `<div class="podio">
    ${ordemPodio.map((x) => {
      const pos = top.indexOf(x) + 1;
      return `<a class="podio-item podio-${pos} revelar" href="${linkJogador(x.j.nick)}" data-brilho>
        <span class="podio-coroa" aria-hidden="true">${pos === 1 ? '👑' : pos === 2 ? '🥈' : '🥉'}</span>
        <img class="podio-corpo" src="https://mc-heads.net/body/${encodeURIComponent(x.j.nick)}/${pos === 1 ? 160 : 130}" alt="" loading="lazy" height="${pos === 1 ? 150 : 120}">
        <span class="podio-pos">${pos}º</span>
        <strong class="podio-nome">${esc(x.j.nick)}</strong>
        <span class="podio-valor">${valor(x)}</span>
      </a>`;
    }).join('')}
  </div>
  ${top.length > 3 ? `<ol class="tabela" start="4">
    ${top.slice(3).map((x, i) => `<li><a href="${linkJogador(x.j.nick)}">
      <span class="tabela-pos">${i + 4}</span>${cabeca(x.j.nick, 28)}<span class="tabela-nome">${esc(x.j.nick)}${x.j.online ? '<span class="ponto-online" title="Online agora"></span>' : ''}</span>
      <span class="tabela-detalhe">${detalhe(x)}</span><span class="tabela-valor">${valor(x)}</span>
    </a></li>`).join('')}
  </ol>` : ''}`;
}

function painelClas(clas) {
  if (!clas.length) return '<p class="vazio-lista">Nenhum clã ainda. Funde o primeiro com <code>/cla criar</code>.</p>';
  return `<ol class="clas">
    ${clas.slice(0, 50).map((c) => `<li class="cla revelar" data-brilho>
      <span class="cla-pos pos-${c.posicao}">${c.posicao}</span>
      <div class="cla-info">
        <div class="cla-nome"><span class="tag-cla">${esc(c.tag)}</span><strong>${esc(c.nome)}</strong></div>
        <div class="cla-membros">${(c.nomes || []).slice(0, 8).map((n) => `<a href="${linkJogador(n)}" title="${esc(n)}">${cabeca(n, 22)}</a>`).join('')}${c.membros > 8 ? `<span class="fraco">+${c.membros - 8}</span>` : ''}</div>
      </div>
      <dl class="cla-numeros">
        <div><dt>Pontos</dt><dd>${numero(c.pontos)}</dd></div>
        <div><dt>Abates</dt><dd>${numero(c.abates)}</dd></div>
        <div><dt>Bosses</dt><dd>${numero(c.bosses)}</dd></div>
        <div><dt>Membros</dt><dd>${numero(c.membros)}</dd></div>
      </dl>
    </li>`).join('')}
  </ol>`;
}

function paginaRankings({ foto, aba, skill }) {
  const r = rankings.calcular(foto);
  if (!ABAS.some((a) => a.id === aba)) aba = 'dinheiro';
  if (!r) {
    return layout({ titulo: 'Rankings', ativo: '/rankings', corpo: `<section class="largura bloco vazio"><h1>Rankings</h1><p>Os rankings aparecem assim que o servidor ligar.</p></section>` });
  }
  if (skill && !r.porSkill[skill]) skill = '';

  const abas = ABAS.map((a) => `<a class="aba" role="tab" href="/rankings?aba=${a.id}" id="aba-${a.id}" data-aba="${a.id}"
      aria-controls="painel-${a.id}" aria-selected="${a.id === aba}"${a.id === aba ? '' : ' tabindex="-1"'}><span aria-hidden="true">${a.icone}</span> ${a.nome}</a>`).join('');

  const seletorSkills = `<div class="chips" role="tablist" aria-label="Qual skill">
    <a class="chip" href="/rankings?aba=skills" data-skill="" aria-selected="${!skill}">Total</a>
    ${r.skills.map((s) => `<a class="chip${s.profissao ? ' chip-prof' : ''}" href="/rankings?aba=skills&amp;skill=${s.id}" data-skill="${s.id}" aria-selected="${skill === s.id}">${esc(s.nome)}</a>`).join('')}
  </div>`;
  const listasSkills = [
    `<div class="sub-painel" data-sub-painel=""${skill ? ' hidden' : ''}>${lista(r.skillTotal.top, (x) => `${numero(x.valor.nivel)} níveis`,
      (x) => (x.valor.prestigio ? `<span class="prestigio">✦${x.valor.prestigio}</span>` : ''), 'Ninguém subiu skill ainda.')}</div>`,
    ...r.skills.map((s) => `<div class="sub-painel" data-sub-painel="${s.id}"${skill === s.id ? '' : ' hidden'}>${lista(r.porSkill[s.id].top.slice(0, 20),
      (x) => textoSkill(x.valor), (x) => `${numero(x.valor.xp)} XP`, `Ninguém treinou ${esc(s.nome)} ainda.`)}</div>`),
  ].join('');

  const paineis = {
    dinheiro: lista(r.dinheiro.top, (x) => reais(x.valor), (x) => (x.j.rank ? textoRank(x.j.rank) : ''), 'Ninguém tem dinheiro ainda.'),
    rank: lista(r.rank.top, (x) => textoRank(x.valor), (x) => reaisCurto(x.j.dinheiro), 'Ninguém subiu de rank ainda.'),
    skills: seletorSkills + listasSkills,
    clas: painelClas(r.clas),
    votos: lista(r.votos.top, (x) => `${numero(x.valor.mes)} voto${x.valor.mes === 1 ? '' : 's'}`,
      (x) => (x.valor.sequencia > 1 ? `🔥 ${numero(x.valor.sequencia)} dias seguidos` : ''), 'Ninguém votou este mês ainda. Use <code>/votar</code> no jogo.'),
  };
  const notas = {
    votos: 'No fim do mês, o 1º ganha 3 Chaves do Boss, o 2º ganha 2 e o 3º ganha 1.',
    clas: 'Pontos = abates no PvP + pontos de dano nos bosses.',
    rank: 'O prestígio vem antes do rank.',
    skills: 'O total soma os níveis de todas as skills; o prestígio vem antes.',
  };

  const corpo = `
<section class="cabecalho-pagina">
  <div class="largura cabecalho-linha">
    <div>
      <h1 class="surgir">Rankings</h1>
      <p class="fraco surgir">${numero(r.totalJogadores)} jogadores · atualizado ${tempoAtras(r.geradoEm)}</p>
    </div>
    <form class="busca surgir" action="/jogador" method="get" role="search">
      <label class="sr" for="busca-nick">Procurar jogador</label>
      <input id="busca-nick" name="nick" placeholder="Procurar um nick..." autocomplete="off" maxlength="16" required pattern="[A-Za-z0-9_]{2,16}">
      <button type="submit" aria-label="Procurar">🔍</button>
    </form>
  </div>
</section>
<section class="largura">
  <div class="abas" role="tablist" aria-label="Rankings" data-abas>${abas}</div>
  ${ABAS.map((a) => `<div class="painel" role="tabpanel" id="painel-${a.id}" aria-labelledby="aba-${a.id}" data-painel="${a.id}"${a.id === aba ? '' : ' hidden'}>
    ${notas[a.id] ? `<p class="nota">${notas[a.id]}</p>` : ''}
    ${paineis[a.id]}
  </div>`).join('')}
</section>`;
  return layout({ titulo: 'Rankings', descricao: 'Os mais ricos, os maiores ranks, as skills, os clãs e os votos do mês na BrasaMC.', ativo: '/rankings', corpo });
}

module.exports = { paginaRankings };
