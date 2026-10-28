// Tickets de suporte. O painel em #abrir-ticket tem um menu com os tipos; o membro escolhe, preenche
// um formulário e ganha um canal privado em "📂 TICKETS ABERTOS", que só ele e a staff veem (os de
// pagamento, só a direção). A staff assume pelo botão; o dono ou a staff fecha, e a conversa vai
// em .txt pro #log-tickets (só a staff vê); o dono recebe na DM só o aviso com a resolução. O canal some 5 s depois.
const {
  ActionRowBuilder, AttachmentBuilder, ButtonBuilder, ButtonStyle, ChannelType, EmbedBuilder, MessageFlags,
  ModalBuilder, PermissionFlagsBits, StringSelectMenuBuilder, TextInputBuilder, TextInputStyle,
} = require('discord.js');
const config = require('./config');
const cargos = require('./cargos');

const COR = 0xf26b1d;
const MAXIMO_ABERTOS = 2;
const STAFF = ['diretor', 'subdiretor', 'moderador', 'ajudante'];
const DIRECAO = ['diretor', 'subdiretor'];

const TIPOS = {
  duvida: { nome: 'Dúvida', emoji: '❓', descricao: 'Como funciona alguma coisa do servidor', pergunta: 'Qual é a sua dúvida?' },
  bug: { nome: 'Bug', emoji: '🐛', descricao: 'Algo quebrado ou estranho no jogo', pergunta: 'O que aconteceu? Onde e quando? Como repetir?' },
  denuncia: { nome: 'Denúncia', emoji: '🚩', descricao: 'Hack, golpe, ofensa ou outra regra quebrada', pergunta: 'Quem, o que fez, onde e quando? (mande prints aqui depois)' },
  pagamento: { nome: 'Pagamento', emoji: '💳', descricao: 'Compra de créditos, VIP ou problema com pagamento', pergunta: 'O que comprou, quando e o que aconteceu?', soDirecao: true },
  apelacao: { nome: 'Apelação', emoji: '⚖️', descricao: 'Pedir revisão de ban, mute ou punição', pergunta: 'Qual punição, quando, e por que deveria ser revista?' },
  outro: { nome: 'Outro', emoji: '💬', descricao: 'Qualquer outro assunto', pergunta: 'Como podemos ajudar?' },
};

const tickets = () => (config.memoria.tickets ||= {});

/** Painel com o menu de tipos em #abrir-ticket (edita a mesma mensagem, não duplica). */
async function painel(guild) {
  const canal = await guild.channels.fetch(config.canal('abrir-ticket')).catch(() => null);
  if (!canal) return console.warn('[tickets] canal #abrir-ticket não encontrado');
  const embed = new EmbedBuilder()
    .setColor(COR)
    .setTitle('🎫 Suporte da BrasaMC')
    .setDescription([
      'Precisa de ajuda? Escolha o tipo no menu abaixo e conte o que houve.',
      'Abre um canal **privado**, só seu e da staff.',
      '',
      ...Object.values(TIPOS).map((t) => `${t.emoji} **${t.nome}**: ${t.descricao}`),
      '',
      `Até ${MAXIMO_ABERTOS} tickets abertos por pessoa. Dúvidas rápidas: veja antes os tutoriais em <#${config.canal('tutoriais')}>.`,
    ].join('\n'));
  const menu = new ActionRowBuilder().addComponents(
    new StringSelectMenuBuilder().setCustomId('ticket:tipo').setPlaceholder('Escolha o tipo de atendimento')
      .addOptions(Object.entries(TIPOS).map(([valor, t]) => ({ label: t.nome, value: valor, description: t.descricao, emoji: t.emoji }))),
  );
  const conteudo = { embeds: [embed], components: [menu] };
  const salva = config.memoria.painelTickets && await canal.messages.fetch(config.memoria.painelTickets).catch(() => null);
  if (salva) await salva.edit(conteudo);
  else {
    config.memoria.painelTickets = (await canal.send(conteudo)).id;
    config.salvarMemoria();
  }
}

function formulario(tipo, interacao) {
  const t = TIPOS[tipo];
  const vinculo = cargos.vinculoDe(interacao.user.id);
  const campos = [];
  if (!vinculo) {
    campos.push(new TextInputBuilder().setCustomId('nick').setLabel('Seu nick no Minecraft')
      .setStyle(TextInputStyle.Short).setMaxLength(16).setRequired(tipo !== 'duvida' && tipo !== 'outro'));
  }
  campos.push(
    new TextInputBuilder().setCustomId('assunto').setLabel('Assunto').setStyle(TextInputStyle.Short).setMaxLength(80).setRequired(true),
    new TextInputBuilder().setCustomId('descricao').setLabel(t.pergunta.slice(0, 45)).setPlaceholder(t.pergunta)
      .setStyle(TextInputStyle.Paragraph).setMaxLength(1500).setRequired(true),
  );
  return new ModalBuilder().setCustomId(`ticket:abrir:${tipo}`).setTitle(`${t.emoji} ${t.nome}`)
    .addComponents(...campos.map((c) => new ActionRowBuilder().addComponents(c)));
}

function abertosDe(usuarioId) {
  return Object.values(tickets()).filter((t) => t.dono === usuarioId);
}

async function abrir(interacao, tipo) {
  const t = TIPOS[tipo];
  await interacao.deferReply({ flags: MessageFlags.Ephemeral });
  const meus = abertosDe(interacao.user.id);
  if (meus.length >= MAXIMO_ABERTOS) {
    return interacao.editReply(`Você já tem ${meus.length} tickets abertos: ${meus.map((x) => `<#${x.canal}>`).join(', ')}. Feche um antes de abrir outro.`);
  }
  const guild = interacao.guild;
  const vinculo = cargos.vinculoDe(interacao.user.id);
  const nick = vinculo ? vinculo.nick : interacao.fields.getTextInputValue('nick')?.trim() || null;
  const assunto = interacao.fields.getTextInputValue('assunto').trim();
  const descricao = interacao.fields.getTextInputValue('descricao').trim();

  config.memoria.ticketNumero = (config.memoria.ticketNumero || 0) + 1;
  const numero = config.memoria.ticketNumero;
  const quemVe = (t.soDirecao ? DIRECAO : STAFF).map((k) => config.cargo(k)).filter(Boolean);
  const nomeCanal = `${t.emoji}・${String(numero).padStart(4, '0')}-${(nick || interacao.user.username).toLowerCase()}`;
  const canal = await guild.channels.create({
    name: nomeCanal.slice(0, 90),
    type: ChannelType.GuildText,
    parent: config.canal('cat:tickets'),
    topic: `${t.nome}: ${assunto}`.slice(0, 1000),
    permissionOverwrites: [
      { id: guild.id, deny: [PermissionFlagsBits.ViewChannel] },
      { id: interacao.user.id, allow: [PermissionFlagsBits.ViewChannel, PermissionFlagsBits.SendMessages, PermissionFlagsBits.AttachFiles, PermissionFlagsBits.EmbedLinks, PermissionFlagsBits.ReadMessageHistory] },
      ...quemVe.map((id) => ({ id, allow: [PermissionFlagsBits.ViewChannel, PermissionFlagsBits.SendMessages, PermissionFlagsBits.AttachFiles, PermissionFlagsBits.ReadMessageHistory, PermissionFlagsBits.ManageMessages] })),
      { id: interacao.client.user.id, allow: [PermissionFlagsBits.ViewChannel, PermissionFlagsBits.SendMessages, PermissionFlagsBits.ManageChannels] },
    ],
  });
  tickets()[canal.id] = { canal: canal.id, numero, tipo, dono: interacao.user.id, nick, assunto, aberto: Date.now(), assumido: null };
  config.salvarMemoria();

  const embed = new EmbedBuilder()
    .setColor(COR)
    .setTitle(`${t.emoji} Ticket #${numero}: ${assunto}`)
    .setDescription(descricao)
    .addFields(
      { name: 'Aberto por', value: `${interacao.user}`, inline: true },
      { name: 'Nick', value: nick ? `\`${nick}\`${vinculo ? ' 🔗' : ''}` : 'não informado', inline: true },
      { name: 'Tipo', value: t.nome, inline: true },
    )
    .setFooter({ text: 'Mande prints e detalhes aqui. A staff responde assim que puder.' })
    .setTimestamp();
  const botoes = new ActionRowBuilder().addComponents(
    new ButtonBuilder().setCustomId('ticket:assumir').setLabel('Assumir').setEmoji('🙋').setStyle(ButtonStyle.Primary),
    new ButtonBuilder().setCustomId('ticket:fechar').setLabel('Fechar').setEmoji('🔒').setStyle(ButtonStyle.Danger),
  );
  await canal.send({
    content: `${interacao.user} ${quemVe.map((id) => `<@&${id}>`).join(' ')}`,
    embeds: [embed],
    components: [botoes],
    allowedMentions: { users: [interacao.user.id], roles: quemVe },
  });
  await interacao.editReply(`✅ Ticket aberto: ${canal}`);
}

function ehStaff(membro, ticket) {
  const chaves = TIPOS[ticket.tipo]?.soDirecao ? DIRECAO : STAFF;
  return chaves.some((k) => membro.roles.cache.has(config.cargo(k))) || membro.permissions.has(PermissionFlagsBits.Administrator);
}

async function assumir(interacao) {
  const ticket = tickets()[interacao.channelId];
  if (!ticket) return interacao.reply({ content: 'Este canal não é um ticket aberto.', flags: MessageFlags.Ephemeral });
  if (!ehStaff(interacao.member, ticket)) return interacao.reply({ content: 'Só a staff assume tickets.', flags: MessageFlags.Ephemeral });
  ticket.assumido = interacao.user.id;
  config.salvarMemoria();
  await interacao.reply({ content: `🙋 ${interacao.user} assumiu este ticket.`, allowedMentions: { parse: [] } });
}

function pedirFechamento(interacao) {
  const ticket = tickets()[interacao.channelId];
  if (!ticket) return interacao.reply({ content: 'Este canal não é um ticket aberto.', flags: MessageFlags.Ephemeral });
  if (interacao.user.id !== ticket.dono && !ehStaff(interacao.member, ticket)) {
    return interacao.reply({ content: 'Só quem abriu ou a staff fecha o ticket.', flags: MessageFlags.Ephemeral });
  }
  return interacao.showModal(new ModalBuilder().setCustomId('ticket:fechar').setTitle('Fechar ticket').addComponents(
    new ActionRowBuilder().addComponents(new TextInputBuilder().setCustomId('motivo').setLabel('Motivo ou resolução (opcional)')
      .setStyle(TextInputStyle.Paragraph).setMaxLength(500).setRequired(false)),
  ));
}

/** Conversa inteira em texto, da mais antiga pra mais nova. */
async function transcricao(canal, ticket) {
  const mensagens = [];
  let antes;
  for (;;) {
    const lote = await canal.messages.fetch({ limit: 100, before: antes });
    if (!lote.size) break;
    mensagens.push(...lote.values());
    antes = lote.last().id;
    if (lote.size < 100) break;
  }
  mensagens.reverse();
  const data = (ms) => new Date(ms).toLocaleString('pt-BR', { timeZone: 'America/Sao_Paulo' });
  const linhas = [
    `Ticket #${ticket.numero} · ${TIPOS[ticket.tipo]?.nome} · ${ticket.assunto}`,
    `Aberto em ${data(ticket.aberto)} · nick: ${ticket.nick || '-'}`,
    ''.padEnd(60, '-'),
  ];
  for (const m of mensagens) {
    // cleanContent troca <@id> e <@&id> pelos nomes; a menção da abertura não interessa no registro
    const conteudo = m.author.id === canal.client.user.id && m.embeds.length ? '' : m.cleanContent;
    const texto = [conteudo, ...m.embeds.map((e) => [e.title, e.description].filter(Boolean).join(': '))].filter(Boolean).join(' | ');
    const anexos = [...m.attachments.values()].map((a) => ` [anexo: ${a.url}]`).join('');
    linhas.push(`[${data(m.createdTimestamp)}] ${m.author.username}: ${texto}${anexos}`);
  }
  return linhas.join('\n');
}

async function fechar(interacao) {
  const canal = interacao.channel;
  const ticket = tickets()[canal.id];
  if (!ticket) return interacao.reply({ content: 'Este canal não é um ticket aberto.', flags: MessageFlags.Ephemeral });
  const motivo = interacao.fields.getTextInputValue('motivo').trim() || 'sem motivo';
  await interacao.reply({ content: `🔒 Ticket fechado por ${interacao.user}. Este canal some em 5 segundos.`, allowedMentions: { parse: [] } });

  const texto = await transcricao(canal, ticket);
  const nomeArquivo = `ticket-${String(ticket.numero).padStart(4, '0')}.txt`;
  const minutos = Math.round((Date.now() - ticket.aberto) / 60_000);
  const resumo = new EmbedBuilder()
    .setColor(0x95a5a6)
    .setTitle(`${TIPOS[ticket.tipo]?.emoji || '🎫'} Ticket #${ticket.numero} fechado`)
    .setDescription(`**${ticket.assunto}**\nResolução: ${motivo}`)
    .addFields(
      { name: 'Aberto por', value: `<@${ticket.dono}>${ticket.nick ? ` (\`${ticket.nick}\`)` : ''}`, inline: true },
      { name: 'Assumido por', value: ticket.assumido ? `<@${ticket.assumido}>` : 'ninguém', inline: true },
      { name: 'Fechado por', value: `${interacao.user}`, inline: true },
      { name: 'Duração', value: minutos < 60 ? `${minutos} min` : `${Math.round(minutos / 60)} h`, inline: true },
    )
    .setTimestamp();
  const log = await interacao.guild.channels.fetch(config.canal('log-tickets')).catch(() => null);
  if (log) {
    await log.send({ embeds: [resumo], files: [new AttachmentBuilder(Buffer.from(texto, 'utf8'), { name: nomeArquivo })], allowedMentions: { parse: [] } })
      .catch((e) => console.warn('[tickets] log:', e.message));
  }
  const dono = await interacao.client.users.fetch(ticket.dono).catch(() => null);
  if (dono) {
    await dono.send(`Seu ticket **#${ticket.numero}** (${ticket.assunto}) na BrasaMC foi fechado.\nResolução: ${motivo}`)
      .catch(() => {}); // DM fechada: tudo bem
  }
  delete tickets()[canal.id];
  config.salvarMemoria();
  setTimeout(() => canal.delete(`Ticket #${ticket.numero} fechado`).catch(() => {}), 5000);
}

/** Canal de ticket apagado à mão: tira da lista. */
function canalApagado(canalId) {
  if (!tickets()[canalId]) return;
  delete tickets()[canalId];
  config.salvarMemoria();
}

async function tratar(interacao) {
  if (interacao.isStringSelectMenu() && interacao.customId === 'ticket:tipo') {
    const tipo = interacao.values[0];
    if (!TIPOS[tipo]) return false;
    await interacao.showModal(formulario(tipo, interacao));
    return true;
  }
  if (interacao.isModalSubmit() && interacao.customId.startsWith('ticket:abrir:')) {
    await abrir(interacao, interacao.customId.split(':')[2]);
    return true;
  }
  if (interacao.isButton() && interacao.customId === 'ticket:assumir') { await assumir(interacao); return true; }
  if (interacao.isButton() && interacao.customId === 'ticket:fechar') { await pedirFechamento(interacao); return true; }
  if (interacao.isModalSubmit() && interacao.customId === 'ticket:fechar') { await fechar(interacao); return true; }
  return false;
}

module.exports = { painel, tratar, canalApagado };
