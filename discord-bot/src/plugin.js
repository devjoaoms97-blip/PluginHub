// Cliente da API local do DiscordPlugin (veja ApiLocal.java). Com o servidor do Minecraft
// desligado, as chamadas lançam um erro com offline = true.
const config = require('./config');

async function chamar(metodo, rota, corpo) {
  let resposta;
  try {
    resposta = await fetch(config.api.url + rota, {
      method: metodo,
      headers: { Authorization: `Bearer ${config.api.token}`, 'Content-Type': 'application/json' },
      body: corpo === undefined ? undefined : JSON.stringify(corpo),
      signal: AbortSignal.timeout(8000),
    });
  } catch (e) {
    const erro = new Error('O servidor do Minecraft está desligado agora. Tente de novo quando ele voltar.');
    erro.offline = true;
    throw erro;
  }
  const dados = await resposta.json().catch(() => ({}));
  if (resposta.status === 401) throw new Error('Token da API recusado: confira o PLUGIN_API_TOKEN.');
  return dados;
}

module.exports = {
  saude: () => chamar('GET', '/saude'),
  vinculos: async () => (await chamar('GET', '/vinculos')).vinculos || [],
  vincular: (dados) => chamar('POST', '/vinculo', dados),
  desvincular: (dados) => chamar('POST', '/desvincular', dados),
  entregar: (dados) => chamar('POST', '/entregar', dados),
};
