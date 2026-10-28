// Configuração do bot. Tudo o que é segredo ou muda por servidor fica fora do repositório, na pasta
// DISCORD_PASTA (padrão C:\mc-teste\discord):
//   .env                      DISCORD_TOKEN, GUILD_TESTE, GUILD_OFICIAL, BOT_SERVIDOR,
//                             PLUGIN_API_URL, PLUGIN_API_TOKEN, IDADE_MINIMA_DIAS, SERVIDOR_IP,
//                             MP_ACCESS_TOKEN (Mercado Pago; vazio: loja desligada)
//   estado-<servidor>.json    IDs dos cargos e canais, gravado pelo tools/discord/estrutura.js
//   bot-<servidor>.json       o que o próprio bot guarda (mensagem do painel, nicks aplicados)
const fs = require('fs');
const path = require('path');

const PASTA = process.env.DISCORD_PASTA || 'C:\\mc-teste\\discord';

function lerEnv() {
  const arquivo = path.join(PASTA, '.env');
  if (!fs.existsSync(arquivo)) throw new Error(`Não achei ${arquivo}.`);
  const env = {};
  for (const linha of fs.readFileSync(arquivo, 'utf8').split(/\r?\n/)) {
    const m = linha.match(/^\s*([A-Z_]+)\s*=\s*(.*?)\s*$/);
    if (m) env[m[1]] = m[2];
  }
  return { ...env, ...process.env };
}

const env = lerEnv();
const servidor = (env.BOT_SERVIDOR || 'teste').toLowerCase();
const guildId = env[`GUILD_${servidor.toUpperCase()}`];
if (!env.DISCORD_TOKEN) throw new Error('DISCORD_TOKEN vazio no .env.');
if (!guildId) throw new Error(`GUILD_${servidor.toUpperCase()} vazio no .env.`);
if (!env.PLUGIN_API_TOKEN) throw new Error('PLUGIN_API_TOKEN vazio no .env (é o api.token do config do DiscordPlugin).');

const arquivoEstado = path.join(PASTA, `estado-${servidor}.json`);
if (!fs.existsSync(arquivoEstado)) throw new Error(`Não achei ${arquivoEstado}: rode o tools/discord/estrutura.js antes.`);
const estrutura = JSON.parse(fs.readFileSync(arquivoEstado, 'utf8'));

const arquivoBot = path.join(PASTA, `bot-${servidor}.json`);
const memoria = fs.existsSync(arquivoBot) ? JSON.parse(fs.readFileSync(arquivoBot, 'utf8')) : {};
memoria.nicks ||= {};

module.exports = {
  token: env.DISCORD_TOKEN,
  servidor,
  guildId,
  api: {
    url: (env.PLUGIN_API_URL || 'http://127.0.0.1:8155').replace(/\/$/, ''),
    token: env.PLUGIN_API_TOKEN,
  },
  idadeMinimaDias: Number(env.IDADE_MINIMA_DIAS || 30),
  // Endereço pra entrar no servidor, mostrado no #status (vazio: não mostra).
  ipDoServidor: env.SERVIDOR_IP || '',
  mercadoPago: { token: env.MP_ACCESS_TOKEN || '' },
  cargo: (chave) => estrutura.cargos[chave],
  canal: (chave) => estrutura.canais[chave],
  // Tier do VipPlugin → chave do cargo no estrutura-brasamc.js.
  cargosVip: { BRONZE: 'vip-bronze', PRATA: 'vip-prata', OURO: 'vip-ouro', DIAMANTE: 'vip-diamante' },
  memoria,
  salvarMemoria: () => fs.writeFileSync(arquivoBot, JSON.stringify(memoria, null, 2)),
};
