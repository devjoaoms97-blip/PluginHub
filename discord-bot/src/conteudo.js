// Textos fixos do Discord, escritos em discord-bot/conteudo/*.md: boas-vindas (com os botões dos
// cargos de aviso 🔔), regras, os downloads do modpack e um post por tutorial no fórum #tutoriais. Na primeira linha vai o
// título ("# ..."); no texto, {ip} vira o IP do servidor e {#chave} vira o link do canal.
// Cada mensagem é publicada uma vez e editada quando o arquivo muda (nada de duplicar).
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { ActionRowBuilder, ButtonBuilder, ButtonStyle, ChannelType, EmbedBuilder, MessageFlags } = require('discord.js');
const config = require('./config');

const PASTA = path.join(__dirname, '..', 'conteudo');
const COR = 0xf26b1d;

// Cargos de aviso que o membro liga e desliga nos botões das boas-vindas.
const AVISOS = [
  { cargo: 'ping-boss', rotulo: 'Boss', emoji: '🐉' },
  { cargo: 'ping-gladiador', rotulo: 'Gladiador', emoji: '⚔️' },
  { cargo: 'ping-leiloes', rotulo: 'Leilões', emoji: '⚖️' },
  { cargo: 'ping-novidades', rotulo: 'Novidades', emoji: '📢' },
];

function ler(arquivo) {
  const bruto = fs.readFileSync(arquivo, 'utf8').replace(/\r\n/g, '\n');
  const [primeira, ...resto] = bruto.split('\n');
  const texto = resto.join('\n').trim()
    .replace(/\{ip\}/g, config.ipDoServidor || 'em breve')
    .replace(/\{instagram\}/g, config.instagram)
    .replace(/\{#([a-z0-9:-]+)\}/g, (_, chave) => (config.canal(chave) ? `<#${config.canal(chave)}>` : `#${chave}`));
  if (texto.length > 4096) throw new Error(`${path.basename(arquivo)} passou de 4096 caracteres`);
  return { titulo: primeira.replace(/^#\s*/, '').trim(), texto };
}

const resumo = (obj) => crypto.createHash('sha1').update(JSON.stringify(obj)).digest('hex');
const memoria = () => (config.memoria.conteudo ||= {});

/** Publica ou edita uma mensagem fixa num canal de texto. */
async function fixa(guild, chave, canalChave, mensagem) {
  const canal = await guild.channels.fetch(config.canal(canalChave)).catch(() => null);
  if (!canal) return console.warn(`[conteudo] canal ${canalChave} não encontrado`);
  const hash = resumo(mensagem);
  const salvo = memoria()[chave];
  const atual = salvo && await canal.messages.fetch(salvo.mensagem).catch(() => null);
  if (atual && salvo.hash === hash) return;
  if (atual) await atual.edit(mensagem);
  else memoria()[chave] = { mensagem: (await canal.send(mensagem)).id };
  memoria()[chave].hash = hash;
  config.salvarMemoria();
  console.log(`[conteudo] ${chave} ${atual ? 'atualizado' : 'publicado'}`);
}

function embed({ titulo, texto }) {
  return new EmbedBuilder().setColor(COR).setTitle(titulo).setDescription(texto);
}

async function boasVindas(guild) {
  // antes do lançamento, só o aviso de novidades (é por ele que sai a data) e o link do Instagram
  const avisos = config.lancado ? AVISOS : [{ ...AVISOS.find((a) => a.cargo === 'ping-novidades'), rotulo: 'Me avise do lançamento' }];
  const botoes = new ActionRowBuilder().addComponents(avisos.filter((a) => config.cargo(a.cargo)).map((a) =>
    new ButtonBuilder().setCustomId(`aviso:${a.cargo}`).setLabel(a.rotulo).setEmoji(a.emoji).setStyle(ButtonStyle.Secondary)));
  if (!config.lancado) {
    botoes.addComponents(new ButtonBuilder().setLabel('Instagram').setEmoji('📸').setStyle(ButtonStyle.Link).setURL(config.instagram));
  }
  await fixa(guild, 'boas-vindas', 'boas-vindas', {
    embeds: [embed(ler(path.join(PASTA, config.lancado ? 'boas-vindas.md' : 'boas-vindas-pre.md')))],
    components: botoes.components.length ? [botoes] : [],
  });
}

/** Um post por arquivo em conteudo/tutoriais, criados do último pro primeiro (o 01 fica no topo). */
async function tutoriais(guild) {
  const forum = await guild.channels.fetch(config.canal('tutoriais')).catch(() => null);
  if (!forum) return console.warn('[conteudo] fórum #tutoriais não encontrado');
  const arquivos = fs.readdirSync(path.join(PASTA, 'tutoriais')).filter((a) => a.endsWith('.md')).sort().reverse();
  for (const arquivo of arquivos) {
    const chave = `tutorial:${arquivo.replace(/\.md$/, '')}`;
    let conteudo;
    try { conteudo = ler(path.join(PASTA, 'tutoriais', arquivo)); } catch (e) { console.warn(`[conteudo] ${e.message}`); continue; }
    const mensagem = { embeds: [embed(conteudo)] };
    const hash = resumo({ titulo: conteudo.titulo, mensagem });
    const salvo = memoria()[chave];
    const fio = salvo && await guild.channels.fetch(salvo.fio).catch(() => null);
    if (fio && salvo.hash === hash) continue;
    if (fio) {
      if (fio.name !== conteudo.titulo) await fio.setName(conteudo.titulo.slice(0, 100));
      const inicial = await fio.fetchStarterMessage().catch(() => null);
      if (inicial) await inicial.edit(mensagem);
    } else {
      const novo = await forum.threads.create({ name: conteudo.titulo.slice(0, 100), message: mensagem });
      memoria()[chave] = { fio: novo.id };
      if (arquivo.startsWith('01')) await novo.pin().catch(() => {});
    }
    memoria()[chave].hash = hash;
    config.salvarMemoria();
    console.log(`[conteudo] ${chave} ${fio ? 'atualizado' : 'publicado'}`);
  }
}

/**
 * Uma mensagem por arquivo de conteudo/<pasta> (anuncios, changelog, spoilers), na ordem do nome
 * (AAAA-MM-DD-assunto.md: o mais antigo primeiro). Publicada uma vez, editada se o texto mudar.
 * Uma linha "anexo: caminho" (a partir da raiz do repositório) manda o arquivo junto, só na primeira vez.
 * Nos anúncios, a primeira publicação marca o cargo 🔔 Novidades.
 */
async function serie(guild, pasta, canalChave, marcar) {
  const dir = path.join(PASTA, pasta);
  if (!fs.existsSync(dir)) return;
  const canal = await guild.channels.fetch(config.canal(canalChave)).catch(() => null);
  if (!canal) return console.warn(`[conteudo] canal ${canalChave} não encontrado`);
  for (const arquivo of fs.readdirSync(dir).filter((a) => a.endsWith('.md')).sort()) {
    const chave = `${pasta}:${arquivo.replace(/\.md$/, '')}`;
    const conteudo = ler(path.join(dir, arquivo));
    const anexos = [...conteudo.texto.matchAll(/^anexo:\s*(.+)$/gm)].map((m) => path.join(PASTA, '..', '..', m[1].trim()));
    conteudo.texto = conteudo.texto.replace(/^anexo:.*$\n?/gm, '').trim();
    // com vídeo, o texto vai na própria mensagem (fica em cima do vídeo; o embed ficaria embaixo)
    const comVideo = anexos.length > 0;
    const corpo = comVideo
      ? { content: `## ${conteudo.titulo}\n${conteudo.texto}`.slice(0, 2000), embeds: [] }
      : { embeds: [embed(conteudo)] };
    const hash = resumo({ titulo: conteudo.titulo, texto: conteudo.texto, comVideo });
    const salvo = memoria()[chave];
    const atual = salvo && await canal.messages.fetch(salvo.mensagem).catch(() => null);
    if (atual && salvo.hash === hash) continue;
    if (atual) await atual.edit({ ...corpo, allowedMentions: { parse: [] } });
    else {
      const cargo = marcar && config.cargo('ping-novidades');
      const msg = await canal.send({
        ...corpo,
        content: [cargo ? `<@&${cargo}>` : '', corpo.content || ''].filter(Boolean).join('\n') || undefined,
        allowedMentions: { roles: cargo ? [cargo] : [] },
        files: anexos.filter((a) => fs.existsSync(a)),
      });
      memoria()[chave] = { mensagem: msg.id };
      // canal de anúncios: publica também pra quem segue o canal em outro servidor
      if (canal.type === ChannelType.GuildAnnouncement) await msg.crosspost().catch(() => {});
    }
    memoria()[chave].hash = hash;
    config.salvarMemoria();
    console.log(`[conteudo] ${chave} ${atual ? 'atualizado' : 'publicado'}`);
  }
}

async function publicar(guild) {
  // cada parte por si: um texto com problema não segura o resto
  const passos = [
    ['boas-vindas', () => boasVindas(guild)],
    ['regras', () => fixa(guild, 'regras', 'regras', { embeds: [embed(ler(path.join(PASTA, 'regras.md')))] })],
    ['modpack', () => fixa(guild, 'modpack', 'modpack', { embeds: [embed(ler(path.join(PASTA, 'modpack.md')))] })],
    ['faq', () => config.canal('faq') && fixa(guild, 'faq', 'faq', { embeds: [embed(ler(path.join(PASTA, 'faq.md')))] })],
    ['tutoriais', () => tutoriais(guild)],
    ['anuncios', () => serie(guild, 'anuncios', 'anuncios', true)],
    ['changelog', () => serie(guild, 'changelog', 'changelog', false)],
    ['spoilers', () => serie(guild, 'spoilers', 'spoilers', false)],
  ];
  for (const [nome, passo] of passos) await Promise.resolve().then(passo).catch((e) => console.warn(`[conteudo] ${nome}: ${e.message}`));
}

/** Botões dos cargos de aviso: clica, ganha; clica de novo, tira. */
async function tratar(interacao) {
  if (!interacao.isButton() || !interacao.customId.startsWith('aviso:')) return false;
  const aviso = AVISOS.find((a) => `aviso:${a.cargo}` === interacao.customId);
  const cargo = aviso && config.cargo(aviso.cargo);
  if (!cargo) return false;
  const membro = interacao.member;
  const tem = membro.roles.cache.has(cargo);
  if (tem) await membro.roles.remove(cargo, 'Cargo de aviso (botão)');
  else await membro.roles.add(cargo, 'Cargo de aviso (botão)');
  await interacao.reply({
    content: tem ? `🔕 Você não vai mais ser marcado em **${aviso.rotulo}**.` : `🔔 Pronto! Você vai ser marcado em **${aviso.rotulo}**.`,
    flags: MessageFlags.Ephemeral,
  });
  return true;
}

module.exports = { publicar, tratar };
