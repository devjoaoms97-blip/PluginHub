// Etapa 2: só LÊ um servidor Discord e mostra como ele está (cargos, categorias, canais,
// permissões e webhooks). Não muda nada. Grava também um JSON completo na pasta do .env,
// que o script de estrutura usa depois pra aproveitar o que já existe.
//
//   node tools/discord/exportar.js oficial
//   node tools/discord/exportar.js teste
const fs = require('fs');
const path = require('path');
const { api, idDoServidor, PASTA } = require('./lib');

const TIPOS = { 0: 'texto', 2: 'voz', 4: 'categoria', 5: 'anúncios', 13: 'palco', 15: 'fórum', 16: 'mídia' };
const PERMISSOES = {
  ADMINISTRATOR: 1n << 3n,
  MANAGE_GUILD: 1n << 5n,
  MANAGE_CHANNELS: 1n << 4n,
  MANAGE_ROLES: 1n << 28n,
  KICK: 1n << 1n,
  BAN: 1n << 2n,
  MANAGE_MESSAGES: 1n << 13n,
  MENTION_EVERYONE: 1n << 17n,
  MODERATE_MEMBERS: 1n << 40n,
  VIEW_CHANNEL: 1n << 10n,
  SEND_MESSAGES: 1n << 11n,
};

function nomesDasPermissoes(valor, filtro = Object.keys(PERMISSOES)) {
  const bits = BigInt(valor || 0);
  return filtro.filter((n) => (bits & PERMISSOES[n]) === PERMISSOES[n]);
}

async function main() {
  const alvo = process.argv[2] || 'oficial';
  const id = idDoServidor(alvo);

  const servidor = await api('GET', `/guilds/${id}?with_counts=true`);
  const [canais, cargos] = await Promise.all([
    api('GET', `/guilds/${id}/channels`),
    api('GET', `/guilds/${id}/roles`),
  ]);
  let webhooks = [];
  try {
    webhooks = await api('GET', `/guilds/${id}/webhooks`);
  } catch (e) {
    console.log(`(sem acesso aos webhooks: ${e.status})`);
  }
  // A URL e o token de um webhook dão poder de postar: nunca saem deste script.
  webhooks = webhooks.map(({ token, url, ...resto }) => resto);

  const nomeCargo = Object.fromEntries(cargos.map((c) => [c.id, c.name]));
  nomeCargo[id] = '@everyone';

  console.log(`\n=== ${servidor.name} (${id}) ===`);
  console.log(`Membros: ~${servidor.approximate_member_count}, online: ~${servidor.approximate_presence_count}`);
  console.log(`Recursos: ${servidor.features.join(', ') || 'nenhum'}`);
  console.log(`Verificação: ${servidor.verification_level}, filtro de conteúdo: ${servidor.explicit_content_filter}, boost: nível ${servidor.premium_tier}`);

  console.log(`\n--- Cargos (${cargos.length}) ---`);
  for (const c of [...cargos].sort((a, b) => b.position - a.position)) {
    const perms = nomesDasPermissoes(c.permissions, ['ADMINISTRATOR', 'MANAGE_GUILD', 'MANAGE_CHANNELS', 'MANAGE_ROLES', 'BAN', 'KICK', 'MANAGE_MESSAGES', 'MODERATE_MEMBERS', 'MENTION_EVERYONE']);
    const extra = [c.managed ? 'gerenciado por bot' : '', c.hoist ? 'separado na lista' : '', c.color ? '#' + c.color.toString(16).padStart(6, '0') : '']
      .filter(Boolean).join(', ');
    console.log(`  ${c.name}${extra ? ` (${extra})` : ''}${perms.length ? ` → ${perms.join(' ')}` : ''}`);
  }

  const webhooksPorCanal = {};
  for (const w of webhooks) (webhooksPorCanal[w.channel_id] ||= []).push(w.name);

  const descrever = (canal, recuo) => {
    const privado = (canal.permission_overwrites || []).some(
      (o) => o.id === id && (BigInt(o.deny) & PERMISSOES.VIEW_CHANNEL),
    );
    const somenteLeitura = (canal.permission_overwrites || []).some(
      (o) => o.id === id && (BigInt(o.deny) & PERMISSOES.SEND_MESSAGES),
    );
    const marcas = [TIPOS[canal.type] || `tipo ${canal.type}`];
    if (privado) marcas.push('privado');
    else if (somenteLeitura) marcas.push('só leitura');
    const liberados = (canal.permission_overwrites || [])
      .filter((o) => o.id !== id && BigInt(o.allow) & PERMISSOES.VIEW_CHANNEL)
      .map((o) => (o.type === 0 ? nomeCargo[o.id] || o.id : 'membro ' + o.id));
    if (privado && liberados.length) marcas.push('vê: ' + liberados.join(', '));
    if (webhooksPorCanal[canal.id]) marcas.push('webhooks: ' + webhooksPorCanal[canal.id].join(', '));
    const prefixo = canal.type === 2 || canal.type === 13 ? '🔊 ' : '# ';
    console.log(`${recuo}${prefixo}${canal.name}  [${marcas.join(' | ')}]`);
  };

  console.log(`\n--- Canais (${canais.length}) ---`);
  const porPosicao = (a, b) => a.position - b.position;
  const categorias = canais.filter((c) => c.type === 4).sort(porPosicao);
  const soltos = canais.filter((c) => c.type !== 4 && !c.parent_id).sort(porPosicao);
  for (const c of soltos) descrever(c, '  ');
  for (const cat of categorias) {
    console.log(`\n  📁 ${cat.name.toUpperCase()}`);
    for (const c of canais.filter((x) => x.parent_id === cat.id).sort(porPosicao)) descrever(c, '     ');
  }

  console.log(`\n--- Webhooks (${webhooks.length}) ---`);
  for (const w of webhooks) {
    const canal = canais.find((c) => c.id === w.channel_id);
    console.log(`  ${w.name} → #${canal ? canal.name : w.channel_id}${w.user ? ` (criado por ${w.user.username})` : ''}`);
  }

  const arquivo = path.join(PASTA, `export-${alvo}.json`);
  fs.writeFileSync(arquivo, JSON.stringify({ servidor, cargos, canais, webhooks, exportadoEm: new Date().toISOString() }, null, 2));
  console.log(`\nJSON completo em ${arquivo}`);
}

main().catch((e) => {
  console.error('Erro:', e.message);
  process.exit(1);
});
