// Cargos que o bot controla: 🔗 Vinculado e os 4 VIPs. Eles seguem o servidor do Minecraft, então
// dar ou tirar à mão não adianta (a próxima sincronia desfaz). O apelido vira o nick do jogo no
// vínculo e quando o nick muda; depois disso o membro pode trocar se quiser.
const config = require('./config');
const plugin = require('./plugin');

const SINCRONIA_MS = 60_000;

/** Deixa um membro com os cargos e o apelido do vínculo dele (ou sem eles, se não tiver vínculo). */
async function aplicar(membro, vinculo) {
  if (membro.user.bot) return;
  const vinculado = config.cargo('vinculado');
  const queroVip = vinculo && vinculo.vip ? config.cargo(config.cargosVip[vinculo.vip]) : null;
  const tirar = [];
  const dar = [];
  if (vinculado) (vinculo ? dar : tirar).push(vinculado);
  for (const chave of Object.values(config.cargosVip)) {
    const id = config.cargo(chave);
    if (id && id !== queroVip) tirar.push(id);
  }
  if (queroVip) dar.push(queroVip);

  const roles = membro.roles.cache;
  const paraTirar = tirar.filter((id) => roles.has(id));
  const paraDar = dar.filter((id) => !roles.has(id));
  if (paraTirar.length) await membro.roles.remove(paraTirar, 'Sincronia com o servidor').catch((e) => avisar(membro, e));
  if (paraDar.length) await membro.roles.add(paraDar, 'Sincronia com o servidor').catch((e) => avisar(membro, e));

  if (vinculo && config.memoria.nicks[membro.id] !== vinculo.nick) {
    if (membro.manageable) await membro.setNickname(vinculo.nick, 'Nick do Minecraft').catch((e) => avisar(membro, e));
    config.memoria.nicks[membro.id] = vinculo.nick; // mesmo sem poder (dono do servidor), não tenta de novo
    config.salvarMemoria();
  } else if (!vinculo && config.memoria.nicks[membro.id]) {
    delete config.memoria.nicks[membro.id];
    config.salvarMemoria();
  }
}

function avisar(membro, erro) {
  console.warn(`[cargos] ${membro.user.tag}: ${erro.message}`);
}

/** Passa por todos os membros. Com o servidor do Minecraft fora do ar, não mexe em nada. */
async function sincronizar(guild) {
  let lista;
  try {
    lista = await plugin.vinculos();
  } catch (e) {
    if (!e.offline) console.warn('[sincronia]', e.message);
    return;
  }
  const porDiscord = new Map(lista.map((v) => [v.discordId, v]));
  for (const membro of guild.members.cache.values()) {
    await aplicar(membro, porDiscord.get(membro.id));
  }
}

function iniciar(guild) {
  const rodar = () => sincronizar(guild).catch((e) => console.warn('[sincronia]', e.message));
  rodar();
  setInterval(rodar, SINCRONIA_MS);
}

module.exports = { aplicar, sincronizar, iniciar };
