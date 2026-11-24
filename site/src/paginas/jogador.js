// /jogador/<nick>: o perfil. Rank, dinheiro, clã, VIP, votos, as posições nos rankings e uma barra
// por skill (combate e profissão separados).
const rankings = require('../rankings');
const { layout } = require('./layout');
const { esc, numero, reais, cabeca, corMc, tempoAtras, data, VIP, linkJogador } = require('./util');

const CARGOS = { LIDER: 'Líder', OFICIAL: 'Oficial', MEMBRO: 'Membro' };

function paginaJogador({ foto, nick }) {
  const r = rankings.calcular(foto);
  const j = r?.porNick.get(String(nick).toLowerCase());
  if (!j) return null;
  const chave = j.nick.toLowerCase();
  const pos = (ranking) => ranking.posicoes.get(chave);
  const posicoes = [
    ['💰', 'Mais ricos', 'dinheiro', pos(r.dinheiro), r.dinheiro.total],
    ['⛏️', 'Rank', 'rank', pos(r.rank), r.rank.total],
    ['📈', 'Skills', 'skills', pos(r.skillTotal), r.skillTotal.total],
    ['🗳️', 'Votos do mês', 'votos', pos(r.votos), r.votos.total],
  ].filter((p) => p[3]);
  const soma = rankings.somaSkills(j);
  const skills = Object.entries(j.skills || {});
  const grupo = (titulo, profissao) => {
    const lista = skills.filter(([, s]) => s.profissao === profissao);
    if (!lista.length) return '';
    return `<div class="skills-grupo"><h3>${titulo}</h3><ul class="skills">
      ${lista.map(([id, s]) => {
        const p = r.porSkill[id]?.posicoes.get(chave);
        return `<li class="skill revelar">
          <div class="skill-topo"><span class="skill-nome">${esc(s.nome)}</span>${s.prestigio ? `<span class="prestigio">✦${s.prestigio}</span>` : ''}<span class="skill-nivel">${numero(s.nivel)}<small>/50</small></span></div>
          <progress max="50" value="${Math.min(50, s.nivel)}" aria-label="${esc(s.nome)}: nível ${s.nivel} de 50"></progress>
          ${p && p <= 100 && (s.nivel > 1 || s.prestigio) ? `<a class="skill-pos" href="/rankings?aba=skills&amp;skill=${id}">#${p} no ranking</a>` : ''}
        </li>`;
      }).join('')}
    </ul></div>`;
  };

  const corpo = `
<section class="perfil-topo">
  <div class="largura perfil-grade">
    <img class="perfil-corpo surgir" src="https://mc-heads.net/body/${encodeURIComponent(j.nick)}/220" alt="Skin de ${esc(j.nick)}" height="220">
    <div class="perfil-info">
      <a class="voltar" href="/rankings">← Rankings</a>
      <h1 class="surgir">${esc(j.nick)}${j.online ? '<span class="selo selo-online"><span class="luz" aria-hidden="true"></span>Online</span>' : ''}</h1>
      <div class="selos surgir">
        ${j.rank ? `<span class="selo selo-rank"><span class="${corMc(j.rank.cor)}">${j.rank.prestigio ? `✦${j.rank.prestigio} ` : ''}${esc(j.rank.nome)}</span></span>` : ''}
        ${j.vip ? `<span class="selo selo-vip vip-${esc(j.vip.toLowerCase())}">VIP ${esc(VIP[j.vip] || j.vip)}</span>` : ''}
        ${j.apoiador ? '<span class="selo selo-apoiador">Apoiador</span>' : ''}
        ${j.cla ? `<a class="selo selo-cla" href="/rankings?aba=clas"><span class="tag-cla">${esc(j.cla.tag)}</span>${esc(j.cla.nome)} · ${CARGOS[j.cla.cargo] || ''}</a>` : ''}
      </div>
      <p class="fraco surgir">${j.online ? 'Jogando agora' : `Visto ${tempoAtras(j.ultimoLogin)}`} · entrou em ${data(j.primeiroLogin)}</p>
      <dl class="perfil-numeros">
        <div class="revelar"><dt>Dinheiro</dt><dd>${reais(j.dinheiro)}</dd></div>
        <div class="revelar"><dt>Rank</dt><dd>${j.rank ? `${numero(j.rank.posicao)}<small>/${numero(j.rank.total)}</small>` : '—'}</dd></div>
        <div class="revelar"><dt>Níveis de skill</dt><dd>${numero(soma.nivel)}</dd></div>
        <div class="revelar"><dt>Votos no mês</dt><dd>${numero(j.votos?.mes)}${j.votos?.sequencia > 1 ? `<small> · 🔥${j.votos.sequencia}</small>` : ''}</dd></div>
      </dl>
    </div>
  </div>
</section>
${posicoes.length ? `<section class="largura bloco">
  <h2 class="revelar">Nos rankings</h2>
  <div class="posicoes">${posicoes.map(([icone, nome, aba, p, total]) => `<a class="posicao revelar" href="/rankings?aba=${aba}" data-brilho>
    <span aria-hidden="true">${icone}</span><strong>#${numero(p)}</strong><span>${nome}</span><small class="fraco">de ${numero(total)}</small>
  </a>`).join('')}</div>
</section>` : ''}
${skills.length ? `<section class="largura bloco">
  <h2 class="revelar">Skills</h2>
  ${grupo('Combate', false)}
  ${grupo('Profissão', true)}
</section>` : ''}`;
  return layout({ titulo: j.nick, descricao: `Perfil de ${j.nick} no BrasaMC: rank, skills, clã e rankings.`, ativo: '/rankings', corpo });
}

/** Sem o nick exato: sugestões que começam com o que foi digitado. */
function naoAchou({ foto, nick }) {
  const r = rankings.calcular(foto);
  const busca = String(nick).toLowerCase();
  const parecidos = r && busca ? [...r.porNick.values()].filter((j) => j.nick.toLowerCase().includes(busca)).slice(0, 12) : [];
  return layout({
    titulo: 'Jogador não encontrado', ativo: '/rankings',
    corpo: `<section class="largura bloco vazio">
      <h1>Ninguém chamado “${esc(nick)}”</h1>
      ${parecidos.length ? `<p>Você quis dizer:</p><div class="sugestoes">${parecidos.map((j) => `<a class="selo" href="${linkJogador(j.nick)}">${cabeca(j.nick, 20)}${esc(j.nick)}</a>`).join('')}</div>`
        : '<p>Esse nick nunca entrou no servidor (ou entrou nos últimos minutos: o site atualiza a cada 5).</p>'}
      <p><a href="/rankings">Ver os rankings</a></p>
    </section>`,
  });
}

module.exports = { paginaJogador, naoAchou };
