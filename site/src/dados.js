// O que o site sabe do servidor, pela API local do DiscordPlugin:
//   status()  GET /saude: online, jogadores, TPS, próximo boss e pote da Mine-sena (guardado 15 s)
//   foto()    GET /site: todos os jogadores (dinheiro, rank, skills, clã, VIP, votos) e os clãs.
//             O plugin refaz a foto a cada 5 min; aqui fica guardada 1 min.
// Com o servidor do Minecraft desligado, status() diz online: false e foto() devolve a última que deu
// certo (o site continua mostrando os rankings).
const config = require('./config');

async function chamar(rota) {
  const resposta = await fetch(config.api.url + rota, {
    headers: { Authorization: `Bearer ${config.api.token}` },
    signal: AbortSignal.timeout(6000),
  });
  if (resposta.status === 401) throw new Error('token da API recusado (PLUGIN_API_TOKEN)');
  return resposta.json();
}

function guardado(rota, validadeMs, manterUltimo) {
  let valor = null;
  let quando = 0;
  let pedindo = null;
  return async () => {
    if (valor && Date.now() - quando < validadeMs) return valor;
    pedindo ||= chamar(rota)
      .then((v) => {
        if (v && v.ok) {
          valor = v;
          quando = Date.now();
        }
        return valor;
      })
      .catch((e) => {
        if (!/fetch failed|aborted|timeout/i.test(e.message)) console.warn(`[site] ${rota}:`, e.message);
        return manterUltimo ? valor : null;
      })
      .finally(() => { pedindo = null; });
    return pedindo;
  };
}

const saude = guardado('/saude', 15_000, false);
const site = guardado('/site', 60_000, true);

/** Status ao vivo: { online: true, quantos, maximo, jogadores, tps, proximoBoss... } ou { online: false }. */
async function status() {
  const s = await saude();
  if (!s) return { online: false };
  // o /saude chama de "online" a quantidade de jogadores; aqui online é ligado/desligado
  return { ...s, online: true, quantos: s.online };
}

/**
 * POST na API do DiscordPlugin (loja: código, confirmar e entregar). Servidor desligado: lança um erro
 * com offline = true.
 */
async function postar(rota, corpo) {
  let resposta;
  try {
    resposta = await fetch(config.api.url + rota, {
      method: 'POST',
      headers: { Authorization: `Bearer ${config.api.token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify(corpo),
      signal: AbortSignal.timeout(8000),
    });
  } catch {
    const erro = new Error('O servidor do Minecraft está desligado agora. Tente de novo daqui a pouco.');
    erro.offline = true;
    throw erro;
  }
  if (resposta.status === 401) throw new Error('token da API recusado (PLUGIN_API_TOKEN)');
  return resposta.json().catch(() => ({ ok: false, erro: 'resposta inválida do servidor' }));
}

/** A última foto dos jogadores, ou null se nunca deu pra pegar. */
async function foto() {
  return site();
}

module.exports = { status, foto, postar };
