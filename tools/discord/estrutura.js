// Etapa 3: deixa o servidor Discord como o estrutura-brasamc.js descreve (cargos, categorias,
// canais, permissões, modo Comunidade e webhooks do DiscordPlugin).
//
//   node tools/discord/estrutura.js teste                 # só mostra o que faria (não muda nada)
//   node tools/discord/estrutura.js teste --aplicar       # aplica
//   node tools/discord/estrutura.js teste --aplicar --plugin C:\mc-teste\plugins\DiscordPlugin\config.yml
//
// Pode rodar quantas vezes quiser: o que já está certo não é tocado. Os IDs de cada item ficam em
// estado-<servidor>.json, e as URLs dos webhooks em webhooks-<servidor>.json, os dois na pasta do
// .env (fora do repositório: URL de webhook é segredo, quem tem posta no canal).
const fs = require('fs');
const path = require('path');
const { api, idDoServidor, PASTA } = require('./lib');
const E = require('./estrutura-brasamc');

const args = process.argv.slice(2);
const alvo = args.find((a) => !a.startsWith('--')) || 'teste';
const APLICAR = args.includes('--aplicar');
const iPlugin = args.indexOf('--plugin');
const CONFIG_PLUGIN = iPlugin >= 0 ? args[iPlugin + 1] : null;
const ICONE = path.join(__dirname, '..', 'marca', 'brasamc-512.png');

const P = {
  KICK_MEMBERS: 1n << 1n, BAN_MEMBERS: 1n << 2n, ADMINISTRATOR: 1n << 3n, MANAGE_CHANNELS: 1n << 4n,
  ADD_REACTIONS: 1n << 6n, VIEW_AUDIT_LOG: 1n << 7n, VIEW_CHANNEL: 1n << 10n, SEND_MESSAGES: 1n << 11n,
  MANAGE_MESSAGES: 1n << 13n, MENTION_EVERYONE: 1n << 17n, CONNECT: 1n << 20n, MUTE_MEMBERS: 1n << 22n,
  DEAFEN_MEMBERS: 1n << 23n, MOVE_MEMBERS: 1n << 24n, MANAGE_NICKNAMES: 1n << 27n, MANAGE_THREADS: 1n << 34n,
  CREATE_PUBLIC_THREADS: 1n << 35n, CREATE_PRIVATE_THREADS: 1n << 36n, SEND_MESSAGES_IN_THREADS: 1n << 38n,
  MODERATE_MEMBERS: 1n << 40n,
};
const ESCREVER = P.SEND_MESSAGES | P.CREATE_PUBLIC_THREADS | P.CREATE_PRIVATE_THREADS | P.SEND_MESSAGES_IN_THREADS;
const TIPO = { texto: 0, voz: 2, categoria: 4, anuncios: 5, forum: 15 };
const PRECISA_COMUNIDADE = new Set([TIPO.anuncios, TIPO.forum]);

const bits = (lista) => (lista || []).reduce((acc, n) => acc | P[n], 0n);
const cor = (hex) => (hex ? parseInt(hex.slice(1), 16) : 0);

let id; // do servidor
let estado;
const arquivoEstado = () => path.join(PASTA, `estado-${alvo}.json`);
const salvarEstado = () => fs.writeFileSync(arquivoEstado(), JSON.stringify(estado, null, 2));

let mudancas = 0;
async function fazer(descricao, acao) {
  mudancas++;
  console.log(`  ${descricao}`);
  if (APLICAR) {
    const r = await acao();
    salvarEstado();
    return r;
  }
  return null;
}

// ---------- cargos ----------

async function cargos(servidor, lista, cargoBot) {
  console.log('\nCargos');
  const porId = new Map(lista.map((c) => [c.id, c]));
  const acima = (c) => c.position >= cargoBot.position;
  const bloqueados = [];

  const everyone = porId.get(id);
  if (E.everyoneSemMencaoGeral && BigInt(everyone.permissions) & P.MENTION_EVERYONE) {
    const novo = BigInt(everyone.permissions) & ~P.MENTION_EVERYONE;
    await fazer('~ @everyone: tirar "Mencionar @everyone"', () =>
      api('PATCH', `/guilds/${id}/roles/${id}`, { permissions: novo.toString() }));
  }

  for (const d of E.cargos) {
    let atual = porId.get(estado.cargos[d.chave]);
    const substituirPendente = d.substituir && !estado.substituidos.includes(d.chave);
    if (!atual && !substituirPendente) {
      atual = lista.find((c) => c.name === d.nome) || lista.find((c) => (d.adotar || []).includes(c.name));
    }

    if (substituirPendente) {
      // O antigo pode ter o mesmo nome do novo: tudo com esses nomes sai, menos o já recriado.
      const velhos = lista.filter((c) => (d.substituir.includes(c.name) || c.name === d.nome) && c !== atual);
      for (const velho of velhos) {
        if (acima(velho)) { bloqueados.push(velho.name); continue; }
        await fazer(`- apagar o cargo antigo "${velho.name}" (sai de quem tinha; ${d.nome} é recriado)`, () =>
          api('DELETE', `/guilds/${id}/roles/${velho.id}`));
        if (APLICAR) { lista.splice(lista.indexOf(velho), 1); porId.delete(velho.id); }
      }
      if (APLICAR && !bloqueados.length) estado.substituidos.push(d.chave);
    }

    if (d.gerenciado) {
      if (atual) estado.cargos[d.chave] = atual.id;
      continue;
    }
    const desejado = { name: d.nome, color: cor(d.cor), hoist: !!d.separado, mentionable: false };
    if (!atual) {
      const criado = await fazer(`+ criar cargo "${d.nome}"`, () =>
        api('POST', `/guilds/${id}/roles`, { ...desejado, permissions: bits(d.permissoes).toString() }));
      if (criado) { estado.cargos[d.chave] = criado.id; lista.push(criado); porId.set(criado.id, criado); }
      continue;
    }
    estado.cargos[d.chave] = atual.id;

    const mudar = {};
    if (atual.name !== d.nome) mudar.name = d.nome;
    if (!d.manterPermissoes && !d.adotar) {
      if (atual.color !== desejado.color) mudar.color = desejado.color;
      if (atual.hoist !== desejado.hoist) mudar.hoist = desejado.hoist;
      if (BigInt(atual.permissions) !== bits(d.permissoes)) mudar.permissions = bits(d.permissoes).toString();
    }
    if (d.tirarMencaoGeral && BigInt(atual.permissions) & P.MENTION_EVERYONE) {
      mudar.permissions = (BigInt(atual.permissions) & ~P.MENTION_EVERYONE).toString();
    }
    if (!Object.keys(mudar).length) continue;
    if (acima(atual)) { bloqueados.push(atual.name); continue; }
    await fazer(`~ ajustar "${atual.name}": ${Object.keys(mudar).join(', ')}`, () =>
      api('PATCH', `/guilds/${id}/roles/${atual.id}`, mudar));
  }

  // Ordem: só dá pra mexer nos cargos abaixo do cargo do bot.
  // Criar cargos empurra o do bot pra cima, então as posições vêm frescas da API.
  const atuais = APLICAR ? await api('GET', `/guilds/${id}/roles`) : lista;
  const pos = new Map(atuais.map((c) => [c.id, c.position]));
  const topo = pos.get(cargoBot.id);
  const abaixo = (c) => c.id !== id && pos.has(c.id) && pos.get(c.id) < topo;
  const ordem = E.cargos.map((d) => atuais.find((c) => c.id === estado.cargos[d.chave])).filter((c) => c && abaixo(c));
  const outros = atuais.filter((c) => abaixo(c) && !ordem.includes(c)).sort((a, b) => b.position - a.position);
  const final = [...ordem, ...outros];
  const posicoes = final.map((c, i) => ({ id: c.id, position: topo - 1 - i }));
  if (posicoes.some((p) => pos.get(p.id) !== p.position)) {
    await fazer(`~ ordenar cargos abaixo do bot: ${final.map((c) => c.name).join(' > ')}`, () =>
      api('PATCH', `/guilds/${id}/roles`, posicoes));
  }
  return bloqueados;
}

// ---------- canais ----------

function sobrescritas(acesso) {
  const papel = (chaves, allow) => chaves.map((k) => estado.cargos[k]).filter(Boolean)
    .map((r) => ({ id: r, type: 0, allow: allow.toString(), deny: '0' }));
  switch (acesso) {
    case 'publico': return [];
    case 'leitura': return [
      { id, type: 0, allow: '0', deny: ESCREVER.toString() },
      ...papel(E.STAFF, ESCREVER),
    ];
    case 'staff': return [
      { id, type: 0, allow: '0', deny: P.VIEW_CHANNEL.toString() },
      ...papel(E.STAFF, P.VIEW_CHANNEL | P.CONNECT | ESCREVER),
    ];
    case 'direcao': return [
      { id, type: 0, allow: '0', deny: P.VIEW_CHANNEL.toString() },
      ...papel(E.DIRECAO, P.VIEW_CHANNEL | P.CONNECT | ESCREVER),
    ];
    default: return null; // "manter"
  }
}

const chaveSobrescritas = (lista) => (lista || [])
  .map((o) => `${o.id}:${o.type}:${o.allow}:${o.deny}`).sort().join('|');

async function garantirCanal(d, lista, pai, acessoPai, comunidade, adiados) {
  const tipo = TIPO[d.tipo || (pai === undefined ? 'categoria' : 'texto')];
  // Categorias têm chave própria (cat:...), pra não colidir com um canal de mesmo nome.
  const chave = (tipo === TIPO.categoria ? 'cat:' : '') + d.chave;
  const porId = new Map(lista.map((c) => [c.id, c]));
  let atual = porId.get(estado.canais[chave]);
  if (!atual) {
    const mesmaEspecie = lista.filter((c) => (c.type === TIPO.categoria) === (tipo === TIPO.categoria));
    atual = mesmaEspecie.find((c) => c.name === d.nome) || mesmaEspecie.find((c) => (d.adotar || []).includes(c.name));
  }

  const acesso = d.acesso || acessoPai;
  const ow = sobrescritas(acesso);
  const nomeLegivel = tipo === TIPO.categoria ? `categoria ${d.nome}` : `${d.nome}`;

  if (!atual) {
    if (PRECISA_COMUNIDADE.has(tipo) && !comunidade) { adiados.push(() => garantirCanal(d, lista, pai, acessoPai, true, [])); return; }
    const corpo = { name: d.nome, type: tipo };
    if (pai) corpo.parent_id = pai;
    if (d.topico && tipo !== TIPO.voz) corpo.topic = d.topico;
    if (ow) corpo.permission_overwrites = ow;
    const criado = await fazer(`+ criar ${nomeLegivel}${acesso && acesso !== 'publico' ? ` (${acesso})` : ''}`, () =>
      api('POST', `/guilds/${id}/channels`, corpo));
    if (criado) { estado.canais[chave] = criado.id; lista.push(criado); }
    return criado;
  }
  estado.canais[chave] = atual.id;

  const mudar = {};
  if (atual.name !== d.nome) mudar.name = d.nome;
  if (pai !== undefined && (atual.parent_id || null) !== (pai || null)) mudar.parent_id = pai;
  if (d.topico && tipo !== TIPO.voz && (atual.topic || '') !== d.topico) mudar.topic = d.topico;
  if (ow && chaveSobrescritas(atual.permission_overwrites) !== chaveSobrescritas(ow)) mudar.permission_overwrites = ow;
  if (atual.type !== tipo && tipo === TIPO.anuncios && atual.type === TIPO.texto) {
    if (comunidade) mudar.type = tipo;
    else adiados.push(() => garantirCanal(d, lista, pai, acessoPai, true, []));
  }
  if (Object.keys(mudar).length) {
    await fazer(`~ ${atual.name !== d.nome ? `"${atual.name}" → ` : ''}${nomeLegivel}: ${Object.keys(mudar).join(', ')}`, async () => {
      const r = await api('PATCH', `/channels/${atual.id}`, mudar);
      Object.assign(atual, r);
    });
  }
  return atual;
}

async function canais(servidor, lista) {
  console.log('\nCanais');
  let comunidade = servidor.features.includes('COMMUNITY');
  const adiados = [];
  for (const cat of E.categorias) {
    const c = await garantirCanal(cat, lista, undefined, cat.acesso, comunidade, adiados);
    const idCat = c ? c.id : `(nova:${cat.chave})`;
    for (const canal of cat.canais) await garantirCanal(canal, lista, idCat, cat.acesso, comunidade, adiados);
  }
  for (const chave of E.apagarCanais || []) {
    const atual = lista.find((c) => c.id === estado.canais[chave]);
    if (atual) {
      await fazer(`- apagar #${atual.name} (com as mensagens)`, async () => {
        await api('DELETE', `/channels/${atual.id}`);
        lista.splice(lista.indexOf(atual), 1);
      });
    }
    if (APLICAR || !atual) delete estado.canais[chave];
  }
  return adiados;
}

async function configurarServidor(servidor, lista) {
  console.log('\nServidor');
  const canal = (k) => estado.canais[k] || null;
  const mudar = {};
  const S = E.servidor;
  const nome = alvo === 'teste' ? S.nome + ' Teste' : S.nome;
  if (servidor.name !== nome) mudar.name = nome;
  if (servidor.default_message_notifications !== S.notificacoesPadrao) mudar.default_message_notifications = S.notificacoesPadrao;
  if (servidor.verification_level < S.verificacao) mudar.verification_level = S.verificacao;
  if (servidor.explicit_content_filter !== S.filtroConteudo) mudar.explicit_content_filter = S.filtroConteudo;
  if (canal(S.canalSistema) && servidor.system_channel_id !== canal(S.canalSistema)) mudar.system_channel_id = canal(S.canalSistema);
  if (canal(S.canalAfk) && servidor.afk_channel_id !== canal(S.canalAfk)) mudar.afk_channel_id = canal(S.canalAfk);
  if (!servidor.icon && fs.existsSync(ICONE)) mudar.icon = 'data:image/png;base64,' + fs.readFileSync(ICONE).toString('base64');
  const ligarComunidade = S.comunidade && !servidor.features.includes('COMMUNITY');
  if (ligarComunidade) {
    mudar.features = [...servidor.features, 'COMMUNITY'];
    mudar.verification_level = Math.max(S.verificacao, servidor.verification_level);
    mudar.explicit_content_filter = 2;
  }
  if (S.comunidade) {
    if (servidor.rules_channel_id !== canal(S.canalRegras)) mudar.rules_channel_id = canal(S.canalRegras);
    if (servidor.public_updates_channel_id !== canal(S.canalAvisosDiscord)) mudar.public_updates_channel_id = canal(S.canalAvisosDiscord);
  }
  if (!Object.keys(mudar).length) return false;
  const nomes = Object.keys(mudar).map((k) => (k === 'features' ? 'ligar modo Comunidade' : k));
  await fazer(`~ servidor: ${nomes.join(', ')}`, async () => Object.assign(servidor, await api('PATCH', `/guilds/${id}`, mudar)));
  return ligarComunidade;
}

async function ordenarCanais(lista) {
  const posicoes = [];
  const porId = new Map(lista.map((c) => [c.id, c]));
  const declaradas = E.categorias.map((c) => porId.get(estado.canais['cat:' + c.chave])).filter(Boolean);
  const outras = lista.filter((c) => c.type === TIPO.categoria && !declaradas.includes(c)).sort((a, b) => a.position - b.position);
  // As categorias antigas que não estão na estrutura ficam logo antes de "outros jogos".
  const iOutros = declaradas.findIndex((c) => c.id === estado.canais['cat:outros-jogos']);
  const cats = iOutros >= 0 ? [...declaradas.slice(0, iOutros), ...outras, ...declaradas.slice(iOutros)] : [...declaradas, ...outras];
  cats.forEach((c, i) => posicoes.push({ id: c.id, position: i }));
  for (const cat of E.categorias) {
    const idCat = estado.canais['cat:' + cat.chave];
    const filhos = cat.canais.map((c) => porId.get(estado.canais[c.chave])).filter(Boolean);
    const resto = lista.filter((c) => c.parent_id === idCat && !filhos.includes(c)).sort((a, b) => a.position - b.position);
    [...filhos, ...resto].forEach((c, i) => posicoes.push({ id: c.id, position: i }));
  }
  const fora = posicoes.filter((p) => porId.get(p.id) && porId.get(p.id).position !== p.position);
  if (fora.length) {
    await fazer(`~ reordenar ${fora.length} canais/categorias`, () => api('PATCH', `/guilds/${id}/channels`, posicoes));
  }
}

// ---------- webhooks ----------

async function webhooks() {
  console.log('\nWebhooks do DiscordPlugin');
  const urls = {};
  let existentes = [];
  if (Object.keys(estado.canais).length) existentes = await api('GET', `/guilds/${id}/webhooks`);
  const avatar = fs.existsSync(ICONE) ? 'data:image/png;base64,' + fs.readFileSync(ICONE).toString('base64') : undefined;
  for (const [categoria, chaveCanal] of Object.entries(E.webhooks)) {
    const canal = estado.canais[chaveCanal];
    const nome = `BrasaMC ${categoria}`;
    let w = existentes.find((x) => x.id === estado.webhooks[categoria]) || existentes.find((x) => x.name === nome);
    if (!w) {
      w = await fazer(`+ criar webhook "${categoria}" em ${chaveCanal}`, () =>
        api('POST', `/channels/${canal}/webhooks`, { name: nome, avatar }));
    } else if (w.channel_id !== canal) {
      w = await fazer(`~ mover webhook "${categoria}" para ${chaveCanal}`, () =>
        api('PATCH', `/webhooks/${w.id}`, { channel_id: canal }));
    }
    if (w) {
      estado.webhooks[categoria] = w.id;
      if (w.token) urls[categoria] = `https://discord.com/api/webhooks/${w.id}/${w.token}`;
    }
  }
  if (APLICAR) {
    salvarEstado();
    const arquivo = path.join(PASTA, `webhooks-${alvo}.json`);
    fs.writeFileSync(arquivo, JSON.stringify(urls, null, 2));
    console.log(`  URLs gravadas em ${arquivo}`);
    if (CONFIG_PLUGIN) gravarNoPlugin(urls);
  }
}

/**
 * Grava no config.yml do DiscordPlugin, pra cada categoria, a webhook-url e o cargo a marcar
 * (mencionar). Categoria que não existe no arquivo entra no fim do bloco "categorias:".
 */
function gravarNoPlugin(urls) {
  const linhas = fs.readFileSync(CONFIG_PLUGIN, 'utf8').split(/\r?\n/);
  const iCats = linhas.findIndex((l) => /^categorias:\s*$/.test(l));
  if (iCats < 0) throw new Error(`Não achei "categorias:" em ${CONFIG_PLUGIN}`);
  const fimDoBloco = () => {
    let i = iCats + 1;
    while (i < linhas.length && (linhas[i] === '' || /^\s/.test(linhas[i]))) i++;
    while (i > iCats + 1 && linhas[i - 1].trim() === '') i--;
    return i;
  };
  const definir = (cat, chave, valor) => {
    let iCat = linhas.findIndex((l, i) => i > iCats && i < fimDoBloco() && l === `  ${cat}:`);
    if (iCat < 0) {
      iCat = fimDoBloco();
      linhas.splice(iCat, 0, `  ${cat}:`);
    }
    for (let i = iCat + 1; i < linhas.length && (linhas[i].startsWith('    ') || /^\s*#/.test(linhas[i])); i++) {
      if (linhas[i].startsWith(`    ${chave}:`)) { linhas[i] = `    ${chave}: "${valor}"`; return; }
    }
    linhas.splice(iCat + 1, 0, `    ${chave}: "${valor}"`);
  };
  for (const [cat, url] of Object.entries(urls)) definir(cat, 'webhook-url', url);
  for (const [cat, chaveCargo] of Object.entries(E.mencoes || {})) {
    if (estado.cargos[chaveCargo]) definir(cat, 'mencionar', estado.cargos[chaveCargo]);
  }
  fs.writeFileSync(CONFIG_PLUGIN, linhas.join('\n'));
  console.log(`  Config do DiscordPlugin atualizado: ${CONFIG_PLUGIN} (rode /discord reload no servidor)`);
}

// ---------- principal ----------

async function main() {
  id = idDoServidor(alvo);
  estado = fs.existsSync(arquivoEstado())
    ? JSON.parse(fs.readFileSync(arquivoEstado(), 'utf8'))
    : { cargos: {}, canais: {}, webhooks: {}, substituidos: [] };

  const eu = await api('GET', '/users/@me');
  const servidor = await api('GET', `/guilds/${id}`);
  const listaCargos = await api('GET', `/guilds/${id}/roles`);
  const listaCanais = await api('GET', `/guilds/${id}/channels`);
  const cargoBot = listaCargos.find((c) => c.managed && c.tags && c.tags.bot_id === eu.id);

  console.log(`${APLICAR ? 'APLICANDO' : 'SIMULAÇÃO (nada muda; use --aplicar)'} em "${servidor.name}" (${id})`);

  const bloqueados = await cargos(servidor, listaCargos, cargoBot);
  if (bloqueados.length) {
    console.log(`\n⚠ O cargo do bot ("${cargoBot.name}") está abaixo de: ${bloqueados.join(', ')}.`);
    console.log('  Em Configurações do servidor → Cargos, arraste o cargo do bot para logo abaixo do Sub-diretor e rode de novo.');
    if (APLICAR) { console.log('  Parei antes de mexer nos canais.'); process.exit(2); }
  }

  const adiados = await canais(servidor, listaCanais);
  const ligouComunidade = await configurarServidor(servidor, listaCanais);
  if (adiados.length) {
    if (ligouComunidade || !APLICAR) console.log('\nCanais que precisam do modo Comunidade');
    for (const f of adiados) await f();
    if (ligouComunidade && APLICAR) await configurarServidor(servidor, listaCanais);
  }
  if (APLICAR) await ordenarCanais(await api('GET', `/guilds/${id}/channels`));
  else console.log('\n  (ordem dos canais é ajustada no --aplicar)');
  await webhooks();

  console.log(mudancas ? `\n${mudancas} mudança(s) ${APLICAR ? 'aplicada(s)' : 'a fazer'}.` : '\nTudo já está como deveria.');
}

main().catch((e) => {
  if (estado && APLICAR) salvarEstado();
  console.error('\nErro:', e.message);
  process.exit(1);
});
