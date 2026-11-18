// Página inicial: o IP pra copiar, o status ao vivo (atualizado pelo site.js), o Discord, o modpack,
// os cards de cada parte do servidor (cada um leva pra página dele), a prévia dos rankings e os números.
const config = require('../config');
const { RECURSOS } = require('../conteudo');
const rankings = require('../rankings');
const { layout } = require('./layout');
const { esc, numero, reais, reaisCurto, cabeca, corMc, linkJogador } = require('./util');

function inicio({ status, foto }) {
  const ip = config.ip;
  const r = rankings.calcular(foto);
  const total = foto?.jogadores?.length || 0;
  const clas = foto?.clas?.length || 0;
  const votoSites = foto?.voto?.sites || [];
  const online = status.online ? status.quantos : 0;

  const corpo = `
<section class="heroi">
  <canvas class="faiscas" data-faiscas aria-hidden="true"></canvas>
  <div class="largura heroi-grade">
    <div class="heroi-texto">
      <p class="sobrelinha surgir">Servidor de Minecraft · Java 26.2</p>
      <h1 class="surgir">Acenda a <span class="brasa">brasa</span>.</h1>
      <p class="chamada surgir">Rankup brasileiro com minas, bosses, fazendas, clãs e eventos toda semana. Entre de graça, com conta original ou não.</p>
      <div class="ip-caixa surgir">
        ${ip ? `<button class="ip" type="button" data-copiar="${esc(ip)}" aria-label="Copiar o IP ${esc(ip)}">
          <span class="ip-rotulo">IP</span><code>${esc(ip)}</code><span class="ip-acao" data-copiar-texto>Copiar</span>
        </button>` : '<div class="ip ip-vazio"><span class="ip-rotulo">IP</span><code>em breve</code></div>'}
      </div>
      <div class="acoes surgir">
        ${config.discord ? `<a class="botao botao-discord" href="${esc(config.discord)}" target="_blank" rel="noopener">Entrar no Discord</a>` : ''}
        <a class="botao botao-secundario" href="${esc(config.modpack)}" target="_blank" rel="noopener">Baixar o modpack</a>
        <a class="botao botao-fantasma" href="/rankings">Ver os rankings →</a>
      </div>
    </div>
    <aside class="status surgir" data-status aria-live="polite">${conteudoStatus(status)}</aside>
  </div>
</section>

<section class="largura numeros" aria-label="A comunidade">
  <div class="numero revelar"><strong data-contar="${total}">${numero(total)}</strong><span>jogadores já entraram</span></div>
  <div class="numero revelar"><strong data-contar="${clas}">${numero(clas)}</strong><span>clãs fundados</span></div>
  <div class="numero revelar"><strong data-status-online data-contar="${online}">${numero(online)}</strong><span>jogando agora</span></div>
</section>

<section class="largura bloco">
  <div class="bloco-topo revelar">
    <h2>O que tem no servidor</h2>
    <p class="fraco">Clique num card pra saber mais.</p>
  </div>
  <div class="cartoes">
    ${RECURSOS.map((d, i) => `<a class="cartao cartao-link revelar" href="/servidor/${d.id}" data-brilho data-atraso="${i % 4}">
      <div class="cartao-icone" aria-hidden="true">${d.icone}</div>
      <h3>${d.titulo}</h3>
      <p>${d.resumo}</p>
      <span class="cartao-mais" aria-hidden="true">Saiba mais →</span>
    </a>`).join('')}
  </div>
</section>

${r ? previaRankings(r) : ''}

<section class="largura bloco">
  <h2 class="revelar">Como entrar</h2>
  <ol class="passos">
    <li class="revelar"><span class="passo-num">1</span><div><strong>Abra o Minecraft Java</strong> na versão 26.2. Pode ser pelo launcher oficial, pelo SKLauncher ou pelo TLauncher.</div></li>
    <li class="revelar"><span class="passo-num">2</span><div><strong>Adicione o servidor</strong> em Multijogador com o IP ${ip ? `<code>${esc(ip)}</code>` : 'que vai aparecer aqui'} e aceite o pacote de texturas.</div></li>
    <li class="revelar"><span class="passo-num">3</span><div><strong>Crie sua senha</strong> com <code>/register</code> e pegue o kit de iniciante com <code>/kit iniciante</code>.</div></li>
  </ol>
  <p class="fraco revelar">Quer mais FPS? O <a href="${esc(config.modpack)}" target="_blank" rel="noopener">modpack oficial</a> já vem com Sodium, shader e o servidor na lista.</p>
</section>

${votoSites.length ? `<section class="largura bloco revelar">
  <h2>Vote e ganhe chaves</h2>
  <p>Cada voto dá uma <strong>Chave de Voto</strong>. A cada ${numero(foto.voto.festaMeta)} votos, todo mundo online ganha uma; faltam <strong>${numero(Math.max(0, foto.voto.festaMeta - foto.voto.festa))}</strong>.</p>
  <div class="acoes">${votoSites.map((s) => `<a class="botao botao-secundario" href="${esc(s.url)}" target="_blank" rel="noopener">${esc(s.nome)}</a>`).join('')}</div>
</section>` : ''}
`;
  return layout({ ativo: '/', corpo, classe: 'pagina-inicio' });
}

/** Três mini-rankings (mais ricos, rank e clãs), cada um levando pra aba dele. */
function previaRankings(r) {
  const mini = (titulo, aba, itens) => `<a class="mini-ranking revelar" href="/rankings?aba=${aba}" data-brilho>
    <h3>${titulo}</h3>
    ${itens.length ? `<ol>${itens.join('')}</ol>` : '<p class="fraco">Ninguém ainda.</p>'}
    <span class="cartao-mais">Ver tudo →</span>
  </a>`;
  const linha = (pos, icone, nome, valor) => `<li><span class="mini-pos pos-${pos}">${pos}</span>${icone}<span class="mini-nome">${nome}</span><span class="mini-valor">${valor}</span></li>`;
  return `<section class="largura bloco">
  <div class="bloco-topo revelar">
    <h2>Quem está no topo</h2>
    <p class="fraco">Atualizado a cada 5 minutos.</p>
  </div>
  <div class="minis">
    ${mini('💰 Mais ricos', 'dinheiro', r.dinheiro.top.slice(0, 3).map((x, i) => linha(i + 1, cabeca(x.j.nick, 24), esc(x.j.nick), reaisCurto(x.valor))))}
    ${mini('⛏️ Rank', 'rank', r.rank.top.slice(0, 3).map((x, i) => linha(i + 1, cabeca(x.j.nick, 24), esc(x.j.nick),
      `<span class="${corMc(x.valor.cor)}">${x.valor.prestigio ? `✦${x.valor.prestigio} ` : ''}${esc(x.valor.nome)}</span>`)))}
    ${mini('🛡️ Clãs', 'clas', r.clas.slice(0, 3).map((c, i) => linha(i + 1, `<span class="tag-cla">${esc(c.tag)}</span>`, esc(c.nome), `${numero(c.pontos)} pts`)))}
  </div>
</section>`;
}

function conteudoStatus(s) {
  if (!s.online) {
    return `<div class="status-topo"><span class="luz luz-off" aria-hidden="true"></span><strong>Servidor desligado</strong></div>
<p class="fraco">Volte daqui a pouco. Os avisos de quando ele volta saem no Discord.</p>`;
  }
  const nomes = s.jogadores || [];
  return `<div class="status-topo"><span class="luz" aria-hidden="true"></span><strong>Online</strong><span class="status-contagem">${numero(nomes.length)}/${numero(s.maximo)}</span></div>
${nomes.length ? `<ul class="cabecas">${nomes.slice(0, 24).map((n) => `<li><a href="${linkJogador(n)}">${cabeca(n, 28)}<span>${esc(n)}</span></a></li>`).join('')}</ul>${nomes.length > 24 ? `<p class="fraco">e mais ${numero(nomes.length - 24)}</p>` : ''}` : '<p class="fraco">Ninguém online agora. Que tal ser o primeiro?</p>'}
<dl class="status-info">
  ${s.proximoBoss ? `<div><dt>Próximo boss</dt><dd>${esc(s.proximoBoss)}</dd></div>` : ''}
  ${s.poteMineSena != null ? `<div><dt>Pote da Mine-sena</dt><dd>${reais(s.poteMineSena)}${s.horarioMineSena ? ` · sorteio às ${esc(s.horarioMineSena)}` : ''}</dd></div>` : ''}
</dl>`;
}

module.exports = { inicio, conteudoStatus };
