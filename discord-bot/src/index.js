// Bot do Discord da BrasaMC. Fala com o servidor do Minecraft pela API local do DiscordPlugin.
//   npm start            (lê a config de C:\mc-teste\discord\.env; BOT_SERVIDOR escolhe teste/oficial)
const { ActivityType, Client, Events, GatewayIntentBits, MessageFlags } = require('discord.js');
const config = require('./config');
const plugin = require('./plugin');
const cargos = require('./cargos');
const vinculo = require('./vinculo');

const cliente = new Client({ intents: [GatewayIntentBits.Guilds, GatewayIntentBits.GuildMembers] });

cliente.once(Events.ClientReady, async () => {
  const guild = await cliente.guilds.fetch(config.guildId);
  console.log(`Bot ${cliente.user.tag} no servidor "${guild.name}" (${config.servidor}).`);
  await guild.commands.set(vinculo.comandos.map((c) => c.toJSON()));
  await guild.members.fetch(); // cache de membros pra sincronia (as mudanças chegam por evento)
  await vinculo.painel(guild).catch((e) => console.warn('[painel]', e.message));
  cargos.iniciar(guild);
  atualizarAtividade();
  setInterval(atualizarAtividade, 60_000);
});

/** "Jogando BrasaMC · 12 online" (ou "servidor desligado"). */
async function atualizarAtividade() {
  let texto;
  try {
    const s = await plugin.saude();
    texto = `BrasaMC · ${s.online} online`;
  } catch {
    texto = 'BrasaMC · servidor desligado';
  }
  cliente.user.setActivity(texto, { type: ActivityType.Playing });
}

// Quem entra no Discord já vinculado (saiu e voltou) recebe os cargos na hora.
cliente.on(Events.GuildMemberAdd, async (membro) => {
  if (membro.guild.id !== config.guildId) return;
  try {
    const v = (await plugin.vinculos()).find((x) => x.discordId === membro.id);
    if (v) await cargos.aplicar(membro, v);
  } catch {
    // servidor desligado: a sincronia de cada minuto resolve depois
  }
});

cliente.on(Events.InteractionCreate, async (interacao) => {
  try {
    await vinculo.tratar(interacao);
  } catch (e) {
    console.error('[interação]', e);
    const resposta = { content: '⚠️ Algo deu errado. Tente de novo em instantes.', flags: MessageFlags.Ephemeral };
    if (interacao.deferred || interacao.replied) await interacao.editReply(resposta).catch(() => {});
    else if (interacao.isRepliable()) await interacao.reply(resposta).catch(() => {});
  }
});

process.on('unhandledRejection', (e) => console.error('[erro]', e));

cliente.login(config.token);
