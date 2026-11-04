// Saguão dos eventos (EventosPlugin, /warp eventos): o salão com um portal por evento, na cor dele; os
// textos em cima dos portais e o painel da agenda; o portal com o evento fechado diz quando abre; o anúncio
// da abertura tem o botão [ENTRAR]; quem se inscreve pelo comando, de fora, vem pro saguão, e pelo portal
// fica nele; a barra do alto mostra quanto falta e quantos inscritos; sair do saguão cancela a inscrição;
// o /<evento> sair e o cancelamento devolvem pra onde estava; com a luta rolando, o portal leva pra
// arquibancada (só durante o evento: sem nada rolando, o /<evento> assistir não leva), e no fim ninguém
// fica na arquibancada; e o portal da frente volta pro spawn.
// Uso: node teste-saguao.js
const { Vec3 } = require('vec3')
const t = require('./lib')

const CX = 0, CZ = 300, CHAO = 64
const Y = CHAO + 1
const PLANO = CZ - 11 // a fileira dos portais
const VOLTA = CZ + 11
const SPAWN = new Vec3(0.5, 65, 43.5)
// a ordem dos portais é a dos eventos no plugin
const PORTAIS = { vietna: 0, spleef: 1, correoucai: 2, sobrevivencia: 3, corrida: 4, labirinto: 5 }
const xPortal = id => CX - 17 + 6 * PORTAIS[id] + 2.5

const pos = bot => bot.entity.position
const noSaguao = b => Math.abs(pos(b).x - CX) <= 20 && Math.abs(pos(b).z - CZ) <= 13 && pos(b).y >= CHAO && pos(b).y < CHAO + 9
const noSpawn = b => pos(b).distanceTo(SPAWN) < 3
const bloco = async (x, y, z, tipo) => /passed/i.test(await t.console(`execute in minecraft:arenas if block ${x} ${y} ${z} minecraft:${tipo}`))
const tp = (bot, x, y, z, yaw = 180) => t.console(`execute in minecraft:arenas run minecraft:tp ${bot.username} ${x} ${y} ${z} ${yaw} 0`)
const paraSpawn = bot => t.console(`execute in minecraft:spawn run minecraft:tp ${bot.username} 0.5 65 43.5`)
const inscritos = async (bot, id) => Number(((await t.capturar(bot, () => bot.chat(`/${id}`), 800)).find(m => m.includes('inscrito')) || '').match(/\((\d+) inscrito/)?.[1] || 0)

/** Anda até o portal do evento (de 3 blocos antes dele, pro norte). */
async function entrarNoPortal(bot, id) {
  await tp(bot, xPortal(id), Y, PLANO + 3.5)
  await t.espera(600)
  return andar(bot, xPortal(id), PLANO - 4)
}

async function andar(bot, x, z) {
  await bot.lookAt(new Vec3(x, pos(bot).y + 1.6, z), true)
  return t.capturar(bot, async () => {
    bot.setControlState('forward', true)
    await t.espera(1600)
    bot.setControlState('forward', false)
  }, 800)
}

t.rodar(async () => {
  for (const e of Object.keys(PORTAIS)) await t.console(`${e} cancelar`)
  console.log(await t.console('eventos construir'))
  const a = await t.criarBot(t.nomeNovo('Sga'))
  const b = await t.criarBot(t.nomeNovo('Sgb'))
  const c = await t.criarBot(t.nomeNovo('Sgc'))
  const d = await t.criarBot(t.nomeNovo('Sgd'))
  const todos = [a, b, c, d]
  try {
    for (const bot of todos) {
      await t.console(`minecraft:clear ${bot.username}`)
      await t.console(`gamemode survival ${bot.username}`)
      await t.console(`lp user ${bot.username} permission set eventos.ip.ignorar true`)
      await paraSpawn(bot)
    }
    await t.espera(1500)

    console.log('\n== O salão ==')
    await tp(a, CX + 0.5, Y, CZ + 6.5)
    await t.espera(2500)
    t.verificar(await bloco(CX - 17, Y, PLANO, 'lime_concrete') && await bloco(CX + 13, Y, PLANO, 'green_concrete')
      && await bloco(CX - 16, Y, PLANO, 'air'), 'um portal por evento, na cor dele (o do Vietnã verde-limão, o do Labirinto verde)')
    t.verificar(await bloco(CX - 2, Y, VOLTA, 'quartz_block'), 'e o portal de volta pro spawn')
    const textos = Number(((await t.console(`execute in minecraft:arenas if entity @e[type=minecraft:text_display,tag=eventos-saguao]`)).match(/count: (\d+)/i) || [, 0])[1])
    t.verificar(textos === 8, 'os textos: um em cima de cada portal, o da volta e o painel', `${textos}`)
    const painel = await t.console(`execute in minecraft:arenas positioned ${CX + 18.5} ${CHAO + 3.2} ${CZ + 0.5} run data get entity @e[type=minecraft:text_display,tag=eventos-saguao,limit=1,distance=..1] text`)
    // o console resume o texto (só o começo aparece); a agenda é a mesma do /eventos, que o teste-labirinto confere
    t.verificar(painel.includes('EVENTOS DE HOJE'), 'o painel da agenda de hoje está lá', painel.slice(0, 200))

    console.log('\n== Portal com o evento fechado ==')
    let msgs = await entrarNoPortal(a, 'corrida')
    t.verificar(msgs.some(m => m.includes('abre') && /(hoje|amanhã) \d\d:\d\d/.test(m)), 'diz quando abre', msgs.find(m => m.includes('abre')))

    console.log('\n== Assistir, sem nada rolando ==')
    msgs = await t.capturar(b, () => b.chat('/corrida assistir'), 1200)
    t.verificar(msgs.some(m => m.includes('não está rolando')) && noSpawn(b), 'sem o evento rolando, /corrida assistir não leva', msgs.find(m => m.includes('rolando')))

    console.log('\n== Inscrição ==')
    msgs = await t.capturar(b, () => t.console('corrida abrir'), 800)
    t.verificar(msgs.some(m => m.includes('[ENTRAR]')), 'o anúncio da abertura tem o botão [ENTRAR]', msgs.find(m => m.includes('ENTRAR')))
    msgs = await t.capturar(b, () => b.chat('/corrida entrar'), 1500)
    t.verificar(msgs.some(m => m.includes('Inscrito!')) && noSaguao(b), 'quem se inscreve de fora vem pro saguão', `${pos(b).floored()}`)
    const barras = []
    a.on('bossBarCreated', bb => barras.push(bb))
    a.on('bossBarUpdated', bb => barras.push(bb))
    msgs = await entrarNoPortal(a, 'corrida')
    t.verificar(msgs.some(m => m.includes('Inscrito!')) && noSaguao(a), 'andar no portal inscreve (e fica no saguão)', msgs.find(m => m.includes('Inscrito') || m.includes('§c')))
    t.verificar(await inscritos(d, 'corrida') === 2, '2 inscritos')
    await t.espera(1500)
    const barra = barras.map(bb => t.semCor(String(bb.title))).reverse().find(s => s.includes('começa em')) || ''
    t.verificar(barra.includes('Corrida Maluca') && barra.includes('2 inscrito'), 'a barra do alto: quanto falta e quantos inscritos', barra)

    console.log('\n== Sair ==')
    msgs = await t.capturar(c, () => c.chat('/corrida entrar'), 1200)
    t.verificar(noSaguao(c), 'o C se inscreve de fora e vem pro saguão', `${pos(c).floored()}`)
    msgs = await t.capturar(c, () => c.chat('/corrida sair'), 1200)
    t.verificar(noSpawn(c), '/corrida sair devolve pra onde estava', `${pos(c).floored()}`)
    msgs = await t.capturar(b, () => paraSpawn(b), 2500)
    t.verificar(msgs.some(m => m.includes('inscrição foi cancelada')), 'sair do saguão cancela a inscrição', msgs.find(m => m.includes('saguão')))
    t.verificar(await inscritos(d, 'corrida') === 1, 'sobra 1 inscrito')
    await t.capturar(b, () => b.chat('/corrida entrar'), 1200)
    await t.console('corrida cancelar')
    await t.espera(1000)
    t.verificar(noSpawn(b) && noSaguao(a), 'cancelado: quem veio de fora volta, quem entrou pelo portal fica no saguão',
      `${pos(b).floored()} | ${pos(a).floored()}`)

    console.log('\n== Com a luta rolando ==')
    await t.console('correoucai abrir')
    msgs = await entrarNoPortal(a, 'correoucai')
    t.verificar(msgs.some(m => m.includes('Inscrito!')), 'o A entra no Corre ou Cai pelo portal')
    await t.capturar(d, () => d.chat('/correoucai entrar'), 1200)
    await t.console('correoucai iniciar')
    await t.espera(2000)
    t.verificar(!noSaguao(a) && !noSaguao(d), 'os dois vão pra arena', `${pos(a).floored()} | ${pos(d).floored()}`)
    await tp(c, CX + 0.5, Y, CZ + 6.5)
    await t.espera(500)
    msgs = await entrarNoPortal(c, 'correoucai')
    t.verificar(msgs.some(m => m.includes('Na arquibancada')) && !noSaguao(c), 'o portal leva pra arquibancada', `${pos(c).floored()}`)
    await paraSpawn(b)
    await t.espera(500)
    await t.capturar(b, () => b.chat('/correoucai assistir'), 1500)
    t.verificar(!noSpawn(b), 'com o evento rolando, /correoucai assistir leva', `${pos(b).floored()}`)
    await t.console('correoucai cancelar')
    await t.espera(1000)
    t.verificar(noSaguao(a) && noSpawn(d), 'no fim, cada um volta pra onde estava (o A pro saguão, o D pro spawn)', `${pos(a).floored()} | ${pos(d).floored()}`)
    t.verificar(noSaguao(c) && noSpawn(b), 'e ninguém fica na arquibancada: quem assistia volta de onde veio (o C pro saguão, o B pro spawn)',
      `${pos(c).floored()} | ${pos(b).floored()}`)

    console.log('\n== De volta pro spawn ==')
    await tp(a, CX + 0.5, Y, VOLTA - 3.5, 0)
    await t.espera(600)
    await andar(a, CX + 0.5, VOLTA + 4)
    await t.espera(800)
    t.verificar(noSpawn(a), 'o portal da frente volta pro spawn', `${pos(a).floored()}`)
  } finally {
    for (const e of Object.keys(PORTAIS)) await t.console(`${e} cancelar`)
    for (const bot of todos) {
      await t.console(`minecraft:clear ${bot.username}`)
      await t.console(`lp user ${bot.username} permission unset eventos.ip.ignorar`)
    }
  }
  await t.terminar(...todos)
})
