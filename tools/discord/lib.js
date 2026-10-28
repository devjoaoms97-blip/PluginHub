// Base dos scripts do Discord: lê o .env de fora do repositório e fala com a API REST do
// Discord (sem dependências, com o fetch do Node 18+). O token nunca é impresso.
const fs = require('fs');
const path = require('path');

const PASTA = process.env.DISCORD_PASTA || 'C:\\mc-teste\\discord';
const API = 'https://discord.com/api/v10';

function lerEnv() {
  const arquivo = path.join(PASTA, '.env');
  if (!fs.existsSync(arquivo)) throw new Error(`Não achei ${arquivo}. Crie com DISCORD_TOKEN=...`);
  const env = {};
  for (const linha of fs.readFileSync(arquivo, 'utf8').split(/\r?\n/)) {
    const m = linha.match(/^\s*([A-Z_]+)\s*=\s*(.*?)\s*$/);
    if (m) env[m[1]] = m[2];
  }
  if (!env.DISCORD_TOKEN || env.DISCORD_TOKEN === 'cole_o_token_aqui') {
    throw new Error(`DISCORD_TOKEN vazio em ${arquivo}.`);
  }
  return env;
}

const env = lerEnv();

/** Chamada à API, esperando o que o Discord pedir em caso de limite (HTTP 429). */
async function api(metodo, rota, corpo) {
  for (let tentativa = 0; tentativa < 5; tentativa++) {
    const resposta = await fetch(API + rota, {
      method: metodo,
      headers: {
        Authorization: `Bot ${env.DISCORD_TOKEN}`,
        'Content-Type': 'application/json',
        'User-Agent': 'DiscordBot (BrasaMC, 1.0)',
      },
      body: corpo === undefined ? undefined : JSON.stringify(corpo),
    });
    if (resposta.status === 429) {
      const dados = await resposta.json().catch(() => ({}));
      await new Promise((r) => setTimeout(r, Math.ceil((dados.retry_after || 1) * 1000)));
      continue;
    }
    if (resposta.status === 204) return null;
    const dados = await resposta.json().catch(() => null);
    if (!resposta.ok) {
      const erro = new Error(`${metodo} ${rota}: HTTP ${resposta.status} ${JSON.stringify(dados)}`);
      erro.status = resposta.status;
      throw erro;
    }
    return dados;
  }
  throw new Error(`${metodo} ${rota}: limite do Discord excedido`);
}

/** "teste", "oficial" ou um ID direto. */
function idDoServidor(nome) {
  if (/^\d+$/.test(nome)) return nome;
  const id = env[`GUILD_${String(nome).toUpperCase()}`];
  if (!id) throw new Error(`Servidor "${nome}" não está no .env (GUILD_${String(nome).toUpperCase()}).`);
  return id;
}

module.exports = { api, env, idDoServidor, PASTA };
