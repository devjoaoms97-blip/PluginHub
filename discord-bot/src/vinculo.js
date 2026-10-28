// Vínculo de contas: o jogador gera o código no jogo (/vincular) e confirma aqui, pelo comando
// /vincular <código> ou pelo botão do painel em #vincular-conta. A recompensa só sai pra conta do
// Discord com pelo menos IDADE_MINIMA_DIAS (contra conta criada só pra farmar o presente).
const {
  ActionRowBuilder, ButtonBuilder, ButtonStyle, EmbedBuilder, MessageFlags, ModalBuilder,
  PermissionFlagsBits, SlashCommandBuilder, TextInputBuilder, TextInputStyle,
} = require('discord.js');
const config = require('./config');
const plugin = require('./plugin');
const cargos = require('./cargos');

const COR = 0xf26b1d;
const NOMES_VIP = { BRONZE: '🥉 Bronze', PRATA: '🥈 Prata', OURO: '🥇 Ouro', DIAMANTE: '💎 Diamante' };

const comandos = [
  new SlashCommandBuilder()
    .setName('vincular')
    .setDescription('Vincula sua conta do Minecraft (gere o código no jogo com /vincular).')
    .addStringOption((o) => o.setName('codigo').setDescription('Código de 6 letras do /vincular no jogo')
      .setRequired(true).setMinLength(6).setMaxLength(7)),
  new SlashCommandBuilder()
    .setName('conta')
    .setDescription('Mostra a conta do Minecraft vinculada.')
    .addUserOption((o) => o.setName('membro').setDescription('De quem (padrão: você)')),
  new SlashCommandBuilder()
    .setName('desvincular')
    .setDescription('Desfaz o vínculo de um membro (staff).')
    .setDefaultMemberPermissions(PermissionFlagsBits.ManageRoles)
    .addUserOption((o) => o.setName('membro').setDescription('Membro do Discord').setRequired(true)),
];

/** Garante a mensagem com o botão em #vincular-conta (edita a mesma, não duplica). */
async function painel(guild) {
  const canal = await guild.channels.fetch(config.canal('vincular-conta')).catch(() => null);
  if (!canal) return console.warn('[vinculo] canal #vincular-conta não encontrado');
  const embed = new EmbedBuilder()
    .setColor(COR)
    .setTitle('🔗 Vincule sua conta do Minecraft')
    .setDescription([
      '**1.** Entre no servidor e digite **`/vincular`** no chat.',
      '**2.** Copie o código de 6 letras que aparecer (vale por 10 minutos).',
      '**3.** Clique no botão abaixo e cole o código. Também dá pra usar **`/vincular <código>`** aqui.',
      '',
      '**O que você ganha:**',
      '🎟 **5 bilhetes grátis da Mine-sena** (na primeira vez)',
      '🔗 O cargo **Vinculado** e seu apelido com o nick do jogo',
      '💎 O cargo do seu **VIP**, sempre em dia com o servidor',
    ].join('\n'))
    .setFooter({ text: 'Uma conta do Discord por conta do Minecraft. Pra trocar, abra um ticket.' });
  const botoes = new ActionRowBuilder().addComponents(
    new ButtonBuilder().setCustomId('vincular:abrir').setLabel('Vincular minha conta').setEmoji('🔗').setStyle(ButtonStyle.Primary),
  );
  const conteudo = { embeds: [embed], components: [botoes] };
  const salva = config.memoria.painelVincular && await canal.messages.fetch(config.memoria.painelVincular).catch(() => null);
  if (salva) await salva.edit(conteudo);
  else {
    const nova = await canal.send(conteudo);
    config.memoria.painelVincular = nova.id;
    config.salvarMemoria();
  }
}

async function registrar(guild, titulo, linhas, cor = COR) {
  const canal = await guild.channels.fetch(config.canal('log-vinculos')).catch(() => null);
  if (!canal) return;
  await canal.send({ embeds: [new EmbedBuilder().setColor(cor).setTitle(titulo).setDescription(linhas.join('\n')).setTimestamp()] })
    .catch((e) => console.warn('[vinculo] log:', e.message));
}

async function confirmar(interacao, codigo) {
  await interacao.deferReply({ flags: MessageFlags.Ephemeral });
  const usuario = interacao.user;
  const idadeDias = (Date.now() - usuario.createdTimestamp) / 86_400_000;
  const elegivel = idadeDias >= config.idadeMinimaDias;
  let r;
  try {
    r = await plugin.vincular({ codigo: codigo.trim(), discordId: usuario.id, discordNome: usuario.username, recompensa: elegivel });
  } catch (e) {
    return interacao.editReply(`⚠️ ${e.message}`);
  }
  if (!r.ok) return interacao.editReply(`❌ ${r.erro}`);

  const membro = await interacao.guild.members.fetch(usuario.id);
  await cargos.aplicar(membro, { nick: r.nick, vip: r.vip, discordId: usuario.id });
  const presente = r.recompensado
    ? '\n🎟 Você ganhou **5 bilhetes grátis da Mine-sena**! Escolha os números no **Bancário**.'
    : elegivel ? '' : `\nO presente do vínculo é só pra contas do Discord com ${config.idadeMinimaDias} dias ou mais.`;
  await interacao.editReply(`✅ Conta vinculada a **${r.nick}**!${r.vip ? ` VIP ${NOMES_VIP[r.vip] || r.vip} reconhecido.` : ''}${presente}`);
  await registrar(interacao.guild, '🔗 Conta vinculada', [
    `${usuario} → **${r.nick}**`,
    `Conta do Discord com ${Math.floor(idadeDias)} dias${r.recompensado ? ' · recompensa entregue' : elegivel ? ' · já tinha recebido a recompensa' : ' · sem recompensa (conta nova)'}`,
  ], 0x2ecc71);
}

async function conta(interacao) {
  const alvo = interacao.options.getUser('membro') || interacao.user;
  let lista;
  try {
    lista = await plugin.vinculos();
  } catch (e) {
    return interacao.reply({ content: `⚠️ ${e.message}`, flags: MessageFlags.Ephemeral });
  }
  const v = lista.find((x) => x.discordId === alvo.id);
  const texto = v
    ? `🔗 ${alvo} está vinculado a **${v.nick}**${v.vip ? ` · VIP ${NOMES_VIP[v.vip] || v.vip}` : ''}.`
    : `${alvo} ainda não vinculou a conta. Veja como em <#${config.canal('vincular-conta')}>.`;
  return interacao.reply({ content: texto, flags: MessageFlags.Ephemeral, allowedMentions: { parse: [] } });
}

async function desvincular(interacao) {
  const alvo = interacao.options.getUser('membro');
  await interacao.deferReply({ flags: MessageFlags.Ephemeral });
  let r;
  try {
    r = await plugin.desvincular({ discordId: alvo.id });
  } catch (e) {
    return interacao.editReply(`⚠️ ${e.message}`);
  }
  if (!r.ok) return interacao.editReply(`❌ ${r.erro}`);
  const membro = await interacao.guild.members.fetch(alvo.id).catch(() => null);
  if (membro) await cargos.aplicar(membro, null);
  await interacao.editReply(`✅ Vínculo de ${alvo} com **${r.nick}** desfeito.`);
  await registrar(interacao.guild, '✂️ Vínculo desfeito', [`${alvo} ✕ **${r.nick}**`, `Pela staff: ${interacao.user}`], 0xe74c3c);
}

/** Trata as interações deste módulo; retorna false se não for daqui. */
async function tratar(interacao) {
  if (interacao.isChatInputCommand()) {
    const acoes = {
      vincular: () => confirmar(interacao, interacao.options.getString('codigo')),
      conta: () => conta(interacao),
      desvincular: () => desvincular(interacao),
    };
    const acao = acoes[interacao.commandName];
    if (!acao) return false;
    await acao();
    return true;
  }
  if (interacao.isButton() && interacao.customId === 'vincular:abrir') {
    const modal = new ModalBuilder().setCustomId('vincular:modal').setTitle('Vincular conta do Minecraft').addComponents(
      new ActionRowBuilder().addComponents(
        new TextInputBuilder().setCustomId('codigo').setLabel('Código do /vincular no jogo')
          .setStyle(TextInputStyle.Short).setMinLength(6).setMaxLength(7).setPlaceholder('Ex.: K7QX2M').setRequired(true),
      ),
    );
    await interacao.showModal(modal);
    return true;
  }
  if (interacao.isModalSubmit() && interacao.customId === 'vincular:modal') {
    await confirmar(interacao, interacao.fields.getTextInputValue('codigo'));
    return true;
  }
  return false;
}

module.exports = { comandos, painel, tratar };
