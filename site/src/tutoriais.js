// Os tutoriais do site são os mesmos arquivos do fórum #tutoriais do Discord
// (discord-bot/conteudo/tutoriais/NN-nome.md): mudou lá, muda aqui. Este módulo lê os arquivos, separa
// os grupos e transforma o markdown do Discord em HTML.
const fs = require('fs');
const path = require('path');
const config = require('./config');
const { esc } = require('./paginas/util');

const PASTA = path.join(__dirname, '..', '..', 'discord-bot', 'conteudo', 'tutoriais');

// Grupos da página /tutoriais, na ordem. Cada tutorial cai no primeiro grupo cujo teste bate no nome.
const GRUPOS = [
  { id: 'skills', titulo: 'Skills', icone: '📈', teste: /skills/ },
  { id: 'comecando', titulo: 'Começando', icone: '🚀', teste: /comece|discord|modpack|celular/ },
  { id: 'progresso', titulo: 'Rankup, dinheiro e produção', icone: '⛏️', teste: /rankup|ranks|dinheiro|terrenos|fazendas|caixas|vip/ },
  { id: 'pvp', titulo: 'PvP e eventos', icone: '⚔️', teste: /clas|eventos|gladiador|x1|dragao/ },
  { id: 'mundos', titulo: 'Mundos, pesca e tesouros', icone: '🗺️', teste: /tesouros|mundos|pesca/ },
];

/** Todos os tutoriais: { slug, numero, titulo, icone, arquivo, grupo }, na ordem do número. */
function lista() {
  if (!fs.existsSync(PASTA)) return [];
  return fs.readdirSync(PASTA).filter((f) => /^\d+-.+\.md$/.test(f)).sort().map((f) => {
    const [, numero, slug] = /^(\d+)-(.+)\.md$/.exec(f);
    const primeira = fs.readFileSync(path.join(PASTA, f), 'utf8').split(/\r?\n/)[0].replace(/^#\s*/, '');
    const m = /^(\p{Extended_Pictographic}️?)\s*(.*)$/u.exec(primeira);
    const grupo = (GRUPOS.find((g) => g.teste.test(slug)) || GRUPOS[GRUPOS.length - 1]).id;
    return { slug, numero: Number(numero), titulo: m ? m[2] : primeira, icone: m ? m[1] : '📘', arquivo: path.join(PASTA, f), grupo };
  });
}

/** O markdown de uma linha: **negrito**, *itálico*, `código`, links <https://...>, {#canal} e {ip}. */
function linha(texto) {
  const ip = config.ip ? `\`${config.ip}\`` : 'do canal de status do Discord';
  const discord = (canal) => (config.discord ? `<a href="${esc(config.discord)}" target="_blank" rel="noopener">#${canal}</a> no Discord` : `#${canal} no Discord`);
  return esc(texto.replace(/`\{ip\}`/g, ip).replace(/\{ip\}/g, config.ip || 'do Discord'))
    .replace(/\{#([a-z0-9-]+)\}/g, (_, canal) => discord(canal))
    .replace(/&lt;(https:\/\/[^\s&]+)&gt;/g, (_, u) => `<a href="${u}" target="_blank" rel="noopener">${u.replace(/^https:\/\/(github\.com\/)?/, '')}</a>`)
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/(^|[^*])\*([^*\s][^*]*?)\*(?!\*)/g, '$1<em>$2</em>')
    .replace(/`(.+?)`/g, '<code>$1</code>');
}

/**
 * O corpo do tutorial em blocos (cada seção vira um card): uma seção começa numa linha só de negrito
 * ("**Título**") ou num "## Título". Dentro: parágrafos, listas (•, - e 1.), passos ("**1.**") e os
 * blocos de código (as tabelas de nível).
 */
function blocos(texto) {
  const linhas = texto.split(/\r?\n/);
  const saida = [];
  let atual = { titulo: null, html: '' };
  saida.push(atual);
  let lista = null; // 'ul' | 'ol'
  let codigo = null;
  const fechar = () => { if (lista) { atual.html += `</${lista}>`; lista = null; } };
  for (const l of linhas.slice(1)) {
    if (codigo !== null) {
      if (l.trim().startsWith('```')) { atual.html += `<pre class="tabela-texto">${esc(codigo.replace(/\n$/, ''))}</pre>`; codigo = null; } else codigo += `${l}\n`;
      continue;
    }
    if (l.trim().startsWith('```')) { fechar(); codigo = ''; continue; }
    if (!l.trim()) { fechar(); continue; }
    const secao = /^\*\*([^*]+)\*\*$/.exec(l.trim()) || /^##+\s+(.*)$/.exec(l.trim());
    if (secao) { fechar(); saida.push((atual = { titulo: secao[1], html: '' })); continue; }
    const item = /^(?:•|-)\s+(.*)$/.exec(l.trim());
    const numerado = /^(\d+)\.\s+(.*)$/.exec(l.trim());
    const passo = /^\*\*(\d+)\.\*\*\s*(.*)$/.exec(l.trim());
    if (item || numerado) {
      const tipo = item ? 'ul' : 'ol';
      if (lista !== tipo) { fechar(); atual.html += tipo === 'ul' ? '<ul class="destaques">' : '<ol class="numerada">'; lista = tipo; }
      atual.html += `<li>${linha(item ? item[1] : numerado[2])}</li>`;
      continue;
    }
    fechar();
    if (passo) atual.html += `<p class="regra"><span class="regra-num">${passo[1]}</span><span>${linha(passo[2])}</span></p>`;
    else atual.html += `<p>${linha(l)}</p>`;
  }
  fechar();
  if (codigo !== null) atual.html += `<pre class="tabela-texto">${esc(codigo)}</pre>`;
  return saida.filter((b) => b.titulo || b.html);
}

function ler(slug) {
  const t = lista().find((x) => x.slug === slug);
  if (!t) return null;
  return { ...t, blocos: blocos(fs.readFileSync(t.arquivo, 'utf8')) };
}

module.exports = { lista, ler, GRUPOS };
