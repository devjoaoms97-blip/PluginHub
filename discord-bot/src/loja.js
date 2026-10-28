// Loja de créditos em #loja-vip. O membro escolhe o pacote, recebe um link do Mercado Pago (Pix ou
// cartão, vale 1 hora) e o bot confere os pagamentos do pedido a cada 20 s. Pagamento aprovado vira
// entrega na conta vinculada pela API do DiscordPlugin (POST /entregar), que anota o ID do pagamento
// e nunca entrega o mesmo duas vezes. Com o servidor desligado, a entrega espera e tenta de novo.
// Cada venda vai pro #log-vendas e o comprador recebe uma DM.
const { ActionRowBuilder, ButtonBuilder, ButtonStyle, EmbedBuilder, MessageFlags } = require('discord.js');
const config = require('./config');
const plugin = require('./plugin');
const cargos = require('./cargos');
const mp = require('./mercadopago');

const COR = 0xf26b1d;
const CONFERIR_MS = 20_000;
const LINK_MS = 60 * 60_000;
const ESPERA_PENDENTE_MS = 48 * 60 * 60_000; // Pix ou boleto em análise: continua conferindo até 48 h
const GUARDAR_MS = 30 * 24 * 60 * 60_000;
const MAX_ABERTOS = 3;

// R$ 1 = 10 créditos (CreditosPlugin); os pacotes maiores têm bônus.
const PACOTES = [
  { id: 'p100', nome: 'Pequeno', preco: 10, creditos: 100, emoji: '🪙' },
  { id: 'p260', nome: 'Médio', preco: 25, creditos: 260, bonus: 4, emoji: '💰' },
  { id: 'p550', nome: 'Grande', preco: 50, creditos: 550, bonus: 10, emoji: '💎' },
  { id: 'p1150', nome: 'Enorme', preco: 100, creditos: 1150, bonus: 15, emoji: '👑' },
];

const METODOS = { account_money: 'Saldo MP', credit_card: 'Cartão de crédito', debit_card: 'Cartão de débito', bank_transfer: 'Pix', ticket: 'Boleto' };
const reais = (v) => v.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
const numero = (v) => v.toLocaleString('pt-BR');
const mem = () => (config.memoria.loja ||= { contador: 0, pedidos: {} });

async function painel(guild) {
  const canal = await guild.channels.fetch(config.canal('loja-vip')).catch(() => null);
  if (!canal) return console.warn('[loja] canal #loja-vip não encontrado');
  const embed = new EmbedBuilder()
    .setColor(COR)
    .setTitle('🛒 Loja de créditos da BrasaMC')
    .setDescription([
      'Créditos compram **VIP** e **cosméticos** na loja do jogo (`/creditos`).',
      '',
      ...PACOTES.map((p) => `${p.emoji} **${p.nome}** · ${numero(p.creditos)} créditos por **${reais(p.preco)}**${p.bonus ? ` *(+${p.bonus}% de bônus)*` : ''}`),
      '',
      '**Como comprar**',
      `**1.** Vincule sua conta em <#${config.canal('vincular-conta')}> (os créditos caem nela).`,
      '**2.** Clique no pacote e pague pelo link do Mercado Pago, com **Pix** ou **cartão**.',
      '**3.** Pronto: os créditos chegam sozinhos em até 1 minuto, e você recebe uma DM.',
    ].join('\n'))
    .setFooter({ text: 'Problema com um pagamento? Abra um ticket de Pagamento em #abrir-ticket.' });
  const botoes = new ActionRowBuilder().addComponents(PACOTES.map((p) =>
    new ButtonBuilder().setCustomId(`loja:${p.id}`).setLabel(`${numero(p.creditos)} · ${reais(p.preco)}`).setEmoji(p.emoji)
      .setStyle(ButtonStyle.Success)));
  const conteudo = { embeds: [embed], components: [botoes] };
  const salva = config.memoria.painelLoja && await canal.messages.fetch(config.memoria.painelLoja).catch(() => null);
  if (salva) await salva.edit(conteudo);
  else {
    config.memoria.painelLoja = (await canal.send(conteudo)).id;
    config.salvarMemoria();
  }
}

/** Vínculo do membro: o da última sincronia ou, se não tiver, pergunta ao servidor agora. */
async function vinculoDe(discordId) {
  const v = cargos.vinculoDe(discordId);
  if (v) return v;
  return (await plugin.vinculos()).find((x) => x.discordId === discordId) || null;
}

// Quem está com um pedido sendo criado agora (dois cliques seguidos não abrem dois pedidos).
const criando = new Set();

async function comprar(interacao, pacote) {
  await interacao.deferReply({ flags: MessageFlags.Ephemeral });
  if (criando.has(interacao.user.id)) return interacao.editReply('⏳ Já estou gerando seu link, um instante.');
  criando.add(interacao.user.id);
  try {
    return await criarPedido(interacao, pacote);
  } finally {
    criando.delete(interacao.user.id);
  }
}

async function criarPedido(interacao, pacote) {
  const usuario = interacao.user;
  let v;
  try {
    v = await vinculoDe(usuario.id);
  } catch (e) {
    return interacao.editReply(`⚠️ ${e.message}`);
  }
  if (!v || !v.uuid) {
    return interacao.editReply(`🔗 Antes de comprar, vincule sua conta do Minecraft em <#${config.canal('vincular-conta')}>: é nela que os créditos caem.`);
  }
  const abertos = Object.entries(mem().pedidos).filter(([, p]) => p.discordId === usuario.id && p.estado === 'aguardando' && p.expira > Date.now());
  const igual = abertos.find(([, p]) => p.pacote === pacote.id && p.uuid === v.uuid);
  let id;
  let pedido;
  if (igual) [id, pedido] = igual;
  else {
    if (abertos.length >= MAX_ABERTOS) {
      return interacao.editReply('⏳ Você já tem 3 links de pagamento abertos. Pague um deles ou espere vencer (1 hora).');
    }
    const numeroPedido = ++mem().contador;
    id = `${config.servidor}-${numeroPedido}`;
    const expira = Date.now() + LINK_MS;
    const { link } = await mp.criarLink({
      referencia: id,
      titulo: `BrasaMC · ${numero(pacote.creditos)} créditos (${v.nick})`,
      preco: pacote.preco,
      expiraEm: expira,
      voltarPara: `https://discord.com/channels/${config.guildId}/${config.canal('loja-vip')}`,
    });
    pedido = {
      numero: numeroPedido, discordId: usuario.id, uuid: v.uuid, nick: v.nick, pacote: pacote.id,
      preco: pacote.preco, creditos: pacote.creditos, link, criado: Date.now(), expira, estado: 'aguardando', pagamentos: {},
    };
    mem().pedidos[id] = pedido;
    config.salvarMemoria();
    console.log(`[loja] pedido ${id}: ${usuario.tag} → ${v.nick}, ${pacote.creditos} créditos, ${reais(pacote.preco)}`);
  }
  const embed = new EmbedBuilder()
    .setColor(COR)
    .setTitle(`${pacote.emoji} Pedido #${pedido.numero}: ${numero(pedido.creditos)} créditos`)
    .setDescription([
      `Para a conta **${pedido.nick}** · **${reais(pedido.preco)}**`,
      '',
      'Clique no botão para pagar com **Pix** ou **cartão** no Mercado Pago.',
      `O link vence <t:${Math.floor(pedido.expira / 1000)}:R>. Quando o pagamento for aprovado, os créditos caem sozinhos e você recebe uma DM.`,
    ].join('\n'));
  const botao = new ActionRowBuilder().addComponents(
    new ButtonBuilder().setStyle(ButtonStyle.Link).setURL(pedido.link).setLabel(`Pagar ${reais(pedido.preco)}`).setEmoji('💳'));
  return interacao.editReply({ embeds: [embed], components: [botao] });
}

async function registrar(guild, embed) {
  const canal = await guild.channels.fetch(config.canal('log-vendas')).catch(() => null);
  if (canal) await canal.send({ embeds: [embed] }).catch((e) => console.warn('[loja] log:', e.message));
}

/** Procura pagamentos novos dos pedidos abertos. */
async function conferir(id, pedido, guild) {
  const pagamentos = await mp.pagamentosDe(id);
  for (const p of pagamentos) {
    const chave = String(p.id);
    if (p.status !== 'approved' || pedido.pagamentos[chave]) continue;
    const certo = p.currency_id === 'BRL' && p.transaction_amount + 0.001 >= pedido.preco;
    pedido.pagamentos[chave] = { estado: certo ? 'pago' : 'divergente', metodo: p.payment_type_id, valor: p.transaction_amount, quando: Date.now() };
    config.salvarMemoria();
    console.log(`[loja] pedido ${id}: pagamento ${chave} aprovado (${p.payment_type_id}, ${p.transaction_amount})`);
    if (!certo) {
      await registrar(guild, new EmbedBuilder().setColor(0xe74c3c).setTitle(`⚠️ Pedido #${pedido.numero}: valor diferente`)
        .setDescription(`Pagamento \`${chave}\` aprovado com ${p.currency_id} ${p.transaction_amount}, mas o pacote custa ${reais(pedido.preco)}. **Nada foi entregue**: confira no Mercado Pago.`));
    }
  }
  const emAndamento = pagamentos.some((p) => ['pending', 'in_process', 'authorized'].includes(p.status));
  const passou = Date.now() - pedido.criado;
  if ((Date.now() > pedido.expira + 10 * 60_000 && !emAndamento) || passou > ESPERA_PENDENTE_MS) {
    pedido.estado = 'fechado';
    config.salvarMemoria();
    console.log(`[loja] pedido ${id} fechado (${Object.keys(pedido.pagamentos).length} pagamento(s))`);
  }
}

/** Entrega um pagamento aprovado. Servidor desligado: fica pra próxima volta. */
async function entregar(id, pedido, chave, guild) {
  const pagamento = pedido.pagamentos[chave];
  let r;
  try {
    r = await plugin.entregar({ pagamento: chave, pedido: `#${pedido.numero}`, uuid: pedido.uuid, creditos: pedido.creditos });
  } catch (e) {
    if (!e.offline) console.warn(`[loja] entrega ${id}:`, e.message);
    return;
  }
  if (!r.ok) {
    if (!pagamento.avisado) {
      pagamento.avisado = true;
      config.salvarMemoria();
      await registrar(guild, new EmbedBuilder().setColor(0xe74c3c).setTitle(`⚠️ Pedido #${pedido.numero}: entrega falhou`)
        .setDescription(`Pagamento \`${chave}\` de <@${pedido.discordId}> pra **${pedido.nick}**: ${r.erro}\nO bot continua tentando.`));
    }
    return;
  }
  pagamento.estado = 'entregue';
  config.salvarMemoria();
  console.log(`[loja] pedido ${id}: ${pedido.creditos} créditos entregues a ${r.nick}${r.repetida ? ' (já tinha sido)' : ''}`);
  const metodo = METODOS[pagamento.metodo] || pagamento.metodo;
  await registrar(guild, new EmbedBuilder().setColor(0x2ecc71).setTitle(`🧾 Venda #${pedido.numero}`).setTimestamp()
    .addFields(
      { name: 'Comprador', value: `<@${pedido.discordId}>`, inline: true },
      { name: 'Conta', value: r.nick || pedido.nick, inline: true },
      { name: 'Créditos', value: numero(pedido.creditos), inline: true },
      { name: 'Valor', value: reais(pagamento.valor), inline: true },
      { name: 'Forma', value: metodo, inline: true },
      { name: 'Pagamento', value: `\`${chave}\``, inline: true },
    ));
  const usuario = await guild.client.users.fetch(pedido.discordId).catch(() => null);
  await usuario?.send({ embeds: [new EmbedBuilder().setColor(0x2ecc71).setTitle('✅ Pagamento aprovado!')
    .setDescription(`**${numero(pedido.creditos)} créditos** entregues na conta **${r.nick || pedido.nick}** (pedido #${pedido.numero}).\nUse no jogo com \`/creditos\`. Obrigado pelo apoio à BrasaMC! 🔥`)] })
    .catch(() => {});
}

let rodando = false;
async function rodada(guild) {
  if (rodando) return;
  rodando = true;
  try {
    const pedidos = mem().pedidos;
    for (const [id, pedido] of Object.entries(pedidos)) {
      if (pedido.estado === 'aguardando') await conferir(id, pedido, guild).catch((e) => console.warn(`[loja] ${id}:`, e.message));
      for (const [chave, pagamento] of Object.entries(pedido.pagamentos)) {
        if (pagamento.estado === 'pago') await entregar(id, pedido, chave, guild);
      }
      const pendente = Object.values(pedido.pagamentos).some((p) => p.estado === 'pago');
      const vazio = !Object.keys(pedido.pagamentos).length;
      if (pedido.estado === 'fechado' && !pendente && (vazio || Date.now() - pedido.criado > GUARDAR_MS)) {
        delete pedidos[id];
        config.salvarMemoria();
      }
    }
  } finally {
    rodando = false;
  }
}

function iniciar(guild) {
  if (!config.mercadoPago.token) return console.warn('[loja] sem MP_ACCESS_TOKEN no .env: loja desligada');
  painel(guild).catch((e) => console.warn('[loja] painel:', e.message));
  setInterval(() => rodada(guild).catch((e) => console.warn('[loja]', e.message)), CONFERIR_MS);
}

async function tratar(interacao) {
  if (!interacao.isButton() || !interacao.customId.startsWith('loja:')) return false;
  const pacote = PACOTES.find((p) => `loja:${p.id}` === interacao.customId);
  if (!pacote) return false;
  if (!config.mercadoPago.token) {
    await interacao.reply({ content: '🛠️ A loja está fechada agora. Volte daqui a pouco.', flags: MessageFlags.Ephemeral });
    return true;
  }
  await comprar(interacao, pacote);
  return true;
}

module.exports = { iniciar, tratar, PACOTES };
