// Os rankings, calculados em cima da foto do DiscordPlugin. Refeitos só quando chega uma foto nova
// (a cada ~5 min), e guardados com a posição de cada jogador em cada um, pro perfil.
const TOP = 50;

/** As skills, na ordem do jogo (as de combate e depois as de profissão), pela foto. */
function skillsDaFoto(jogadores) {
  for (const j of jogadores) if (j.skills) return Object.entries(j.skills).map(([id, s]) => ({ id, nome: s.nome, profissao: s.profissao }));
  return [];
}

const somaSkills = (j) => {
  let nivel = 0, prestigio = 0;
  for (const s of Object.values(j.skills || {})) { nivel += s.nivel; prestigio += s.prestigio; }
  return { nivel, prestigio };
};

/** Ordena, corta no TOP e anota a posição de todo mundo (não só do top). */
function ordenar(lista, comparar, valor) {
  const ordenada = lista.slice().sort(comparar);
  const posicoes = new Map(ordenada.map((j, i) => [j.nick.toLowerCase(), i + 1]));
  return { top: ordenada.slice(0, TOP).map((j) => ({ j, ...valor(j) })), posicoes, total: ordenada.length };
}

let calculado = null;

function calcular(foto) {
  if (!foto) return null;
  if (calculado && calculado.geradoEm === foto.geradoEm) return calculado;
  const jogadores = foto.jogadores || [];
  const porNick = new Map(jogadores.map((j) => [j.nick.toLowerCase(), j]));
  const skills = skillsDaFoto(jogadores);

  const dinheiro = ordenar(jogadores.filter((j) => j.dinheiro > 0), (a, b) => b.dinheiro - a.dinheiro, (j) => ({ valor: j.dinheiro }));

  const rank = ordenar(jogadores.filter((j) => j.rank),
    (a, b) => (b.rank.prestigio - a.rank.prestigio) || (b.rank.posicao - a.rank.posicao) || ((b.dinheiro || 0) - (a.dinheiro || 0)),
    (j) => ({ valor: j.rank }));

  const comSkills = jogadores.filter((j) => j.skills);
  const skillTotal = ordenar(comSkills.filter((j) => somaSkills(j).nivel > skills.length || somaSkills(j).prestigio > 0),
    (a, b) => { const x = somaSkills(a), y = somaSkills(b); return (y.prestigio - x.prestigio) || (y.nivel - x.nivel); },
    (j) => ({ valor: somaSkills(j) }));
  const porSkill = {};
  for (const s of skills) {
    porSkill[s.id] = ordenar(comSkills.filter((j) => j.skills[s.id] && (j.skills[s.id].nivel > 1 || j.skills[s.id].xp > 0 || j.skills[s.id].prestigio > 0)),
      (a, b) => { const x = a.skills[s.id], y = b.skills[s.id]; return (y.prestigio - x.prestigio) || (y.nivel - x.nivel) || (y.xp - x.xp); },
      (j) => ({ valor: j.skills[s.id] }));
  }

  const votos = ordenar(jogadores.filter((j) => j.votos?.mes > 0),
    (a, b) => (b.votos.mes - a.votos.mes) || (b.votos.total - a.votos.total), (j) => ({ valor: j.votos }));

  const clas = (foto.clas || []).map((c, i) => ({ ...c, posicao: i + 1 }));

  calculado = { geradoEm: foto.geradoEm, porNick, skills, dinheiro, rank, skillTotal, porSkill, votos, clas, totalJogadores: jogadores.length };
  return calculado;
}

module.exports = { calcular, somaSkills, TOP };
