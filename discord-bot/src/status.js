// #status: uma mensagem só, editada a cada minuto, com o servidor online ou offline (e desde
// quando), quem está jogando, o próximo boss e o pote da Mine-sena. Se outra mensagem cair no canal
// (o "servidor online/desligado" do webhook do DiscordPlugin), o painel é reenviado pra ficar por último.
const { ActivityType, EmbedBuilder } = require('discord.js');
const config = require('./config');
const plugin = require('./plugin');

const INTERVALO_MS = 60_000;
const dinheiro = (v) => v.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
const hora = (ms) => `<t:${Math.floor(ms / 1000)}:R>`;

function montar(s) {
  const mem = config.memoria.status ||= {};
  const online = !!s;
  if (mem.online !== online) {
    mem.online = online;
    mem.desde = Date.now();
    config.salvarMemoria();
  }
  const embed = new EmbedBuilder().setTitle('BrasaMC').setTimestamp().setFooter({ text: 'Atualizado a cada minuto' });
  const ip = config.ipDoServidor;
  if (!online) {
    return embed.setColor(0xe74c3c).setDescription([
      `🔴 **Servidor offline** ${hora(mem.desde)}`,
      'Já já a gente volta. Fique de olho no <#' + config.canal('anuncios') + '>.',
      ip ? `\n🌐 **IP:** \`${ip}\`` : '',
    ].join('\n'));
  }
  const nomes = s.jogadores || [];
  const lista = nomes.length ? nomes.slice(0, 30).map((n) => `\`${n}\``).join(' ') + (nomes.length > 30 ? ` e mais ${nomes.length - 30}` : '') : 'ninguém agora';
  embed.setColor(0x2ecc71).setDescription([
    `🟢 **Servidor online** ${hora(mem.desde)}`,
    ip ? `🌐 **IP:** \`${ip}\`` : '',
  ].filter(Boolean).join('\n'));
  embed.addFields({ name: `👥 Jogando (${s.online}/${s.maximo})`, value: lista.slice(0, 1024) });
  if (s.proximoBoss) embed.addFields({ name: '🐉 Próximo boss', value: s.proximoBoss, inline: true });
  if (s.poteMineSena !== undefined) {
    embed.addFields({ name: '🎟 Mine-sena', value: `${dinheiro(s.poteMineSena)} · sorteio às ${s.horarioMineSena || '18:00'}`, inline: true });
  }
  if (s.tps !== undefined && s.tps < 18) embed.addFields({ name: '⚠️ Desempenho', value: `TPS ${s.tps}`, inline: true });
  return embed;
}

async function atualizar(cliente) {
  let s = null;
  try {
    s = await plugin.saude();
  } catch {
    // servidor desligado: o painel mostra offline
  }
  cliente.user.setActivity(s ? `BrasaMC · ${s.online} online` : 'BrasaMC · servidor desligado', { type: ActivityType.Playing });

  const canal = await cliente.channels.fetch(config.canal('status')).catch(() => null);
  if (!canal) return;
  const conteudo = { embeds: [montar(s)] };
  const id = config.memoria.painelStatus;
  const atual = id && await canal.messages.fetch(id).catch(() => null);
  if (atual && canal.lastMessageId === id) {
    await atual.edit(conteudo);
    return;
  }
  if (atual) await atual.delete().catch(() => {});
  config.memoria.painelStatus = (await canal.send(conteudo)).id;
  config.salvarMemoria();
}

function iniciar(cliente) {
  const rodar = () => atualizar(cliente).catch((e) => console.warn('[status]', e.message));
  rodar();
  setInterval(rodar, INTERVALO_MS);
}

module.exports = { iniciar };
