// Configuração do site. Usa o mesmo .env do bot do Discord, na pasta DISCORD_PASTA (padrão
// C:\mc-teste\discord), porque fala com a mesma API do DiscordPlugin:
//   PLUGIN_API_URL, PLUGIN_API_TOKEN   a API local do DiscordPlugin (com sufixo _TESTE ou _OFICIAL, vale só naquele)
//   SERVIDOR_IP                        o endereço pra entrar, mostrado na página inicial (vazio: "em breve")
//   SITE_PORTA                         a porta do site (padrão 8080; com sufixo, por servidor)
//   SITE_ENDERECO                      onde escutar (padrão 127.0.0.1; na VPS, atrás do Caddy, continua assim)
//   DISCORD_CONVITE                    o convite do Discord (ex: https://discord.gg/...); vazio: sem botão
//   MODPACK_URL                        a página de download do modpack
//   MP_ACCESS_TOKEN                    o Mercado Pago (o mesmo da loja do Discord; vazio: loja do site fechada)
//   SITE_URL                           o endereço público do site (ex: https://brasamc.com.br): volta do
//                                      Mercado Pago e imagem de prévia dos links. Vazio: http://127.0.0.1:<porta>
// O servidor (teste ou oficial) vem do primeiro argumento, ou de SITE_SERVIDOR, ou de BOT_SERVIDOR.
const fs = require('fs');
const path = require('path');

const PASTA = process.env.DISCORD_PASTA || 'C:\\mc-teste\\discord';

function lerEnv() {
  const arquivo = path.join(PASTA, '.env');
  const env = {};
  if (fs.existsSync(arquivo)) {
    for (const linha of fs.readFileSync(arquivo, 'utf8').split(/\r?\n/)) {
      const m = linha.match(/^\s*([A-Z_]+)\s*=\s*(.*?)\s*$/);
      if (m) env[m[1]] = m[2];
    }
  }
  return { ...env, ...process.env };
}

const env = lerEnv();
const servidor = (process.argv[2] || env.SITE_SERVIDOR || env.BOT_SERVIDOR || 'teste').toLowerCase();
const doServidor = (chave) => env[`${chave}_${servidor.toUpperCase()}`] ?? env[chave] ?? '';
if (!doServidor('PLUGIN_API_TOKEN')) {
  throw new Error(`PLUGIN_API_TOKEN (ou PLUGIN_API_TOKEN_${servidor.toUpperCase()}) vazio em ${path.join(PASTA, '.env')}.`);
}

module.exports = {
  servidor,
  pasta: PASTA,
  porta: Number(doServidor('SITE_PORTA') || 8080),
  endereco: doServidor('SITE_ENDERECO') || '127.0.0.1',
  api: {
    url: (doServidor('PLUGIN_API_URL') || 'http://127.0.0.1:8155').replace(/\/$/, ''),
    token: doServidor('PLUGIN_API_TOKEN'),
  },
  ip: doServidor('SERVIDOR_IP'),
  discord: doServidor('DISCORD_CONVITE'),
  modpack: doServidor('MODPACK_URL') || 'https://github.com/devjoaoms97-blip/PluginHub/releases/tag/modpack-1.0.0',
  mercadoPago: {
    token: doServidor('MP_ACCESS_TOKEN'),
    // só o teste muda (um Mercado Pago falso)
    api: (env.MP_API_URL || 'https://api.mercadopago.com').replace(/\/$/, ''),
  },
};
module.exports.url = (doServidor('SITE_URL') || `http://127.0.0.1:${module.exports.porta}`).replace(/\/$/, '');
