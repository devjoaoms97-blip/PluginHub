// Evento Spleef (EventosPlugin): inscrição só com o inventário vazio, um evento aberto por vez, os 3 no
// piso de cima, a pá na luta, quebrar a neve (sem drop) faz cair pro andar de baixo sem sair, só a
// neve quebra, comandos barrados, cair na água (do último piso) elimina, desconectar elimina, o último
// de pé leva o cheque e a arena volta. Num segundo evento, com o tempo encurtado no config (e o
// original de volta no fim), o piso derrete sozinho.
// Uso: node teste-spleef.js
const fs = require('fs')
const path = require('path')
const t = require('./lib')
const { Vec3 } = require('vec3')

const CONFIG = path.join(process.env.MC_TESTE || 'C:\\mc-teste', 'plugins', 'EventosPlugin', 'config.yml')
const AGUA = 64
const PISOS = [AGUA + 6, AGUA + 12, AGUA + 18] // 70, 76, 82
const CENTRO = [0, 900]
const RAIO = 15

const quantos = async (nome, item) => Number(((await t.console(`minecraft:clear ${nome} ${item} 0`)).match(/Found (\d+)/) || [, 0])[1])
const naArquibancada = b => b.entity.position.z < CENTRO[1] - RAIO - 2 && b.entity.position.y > PISOS[2] + 2
const andar = b => PISOS.findLastIndex(y => b.entity.position.y >= y + 0.5)

/** Neve que o bot D enxerga nos 3 pisos (pra ver o derretimento). */
function neve(bot) {
  let n = 0
  for (const y of PISOS)
    for (let x = CENTRO[0] - RAIO; x <= CENTRO[0] + RAIO; x++)
      for (let z = CENTRO[1] - RAIO; z <= CENTRO[1] + RAIO; z++) if (bot.blockAt(new Vec3(x, y, z))?.name === 'snow_block') n++
  return n
}

/**
 * Quebra a neve debaixo dos pés (com a pá na mão). O Mineflayer não lê o encantamento no formato novo
 * ao calcular o tempo de quebra ("enchantments is not iterable"): tempo fixo, como no teste-coleta.
 */
async function cavarPe(bot) {
  if (!bot.digTimeOriginal) {
    bot.digTimeOriginal = bot.digTime
    bot.digTime = bl => { try { return bot.digTimeOriginal(bl) } catch (e) { return 50 } }
  }
  const b = bot.blockAt(bot.entity.position.offset(0, -0.5, 0).floored())
  if (!b || b.name !== 'snow_block') return false
  try {
    await bot.dig(b, true)
  } catch (e) {
    console.log(`  [${bot.username} não cavou] ${e.message}`)
    return false
  }
  await t.espera(1500)
  return true
}

t.rodar(async () => {
  const original = fs.readFileSync(CONFIG, 'utf8')
  await t.console('spleef cancelar')
  await t.console('vietna cancelar')
  const a = await t.criarBot(t.nomeNovo('Spa'))
  const b = await t.criarBot(t.nomeNovo('Spb'))
  const c = await t.criarBot(t.nomeNovo('Spc'))
  const d = await t.criarBot(t.nomeNovo('Spd'))
  const todos = [a, b, c, d]
  try {
    for (const bot of todos) {
      await t.console(`minecraft:clear ${bot.username}`)
      await t.console(`gamemode survival ${bot.username}`)
      await t.console(`lp user ${bot.username} permission set eventos.ip.ignorar true`)
      await t.console(`execute in minecraft:spawn run minecraft:tp ${bot.username} 0.5 65 43.5`)
    }
    await t.espera(1500)

    console.log('\n== Inscrição ==')
    await t.console('spleef abrir')
    await t.espera(500)
    const outro = await t.console('vietna abrir')
    t.verificar(/Já há um evento aberto: Spleef/.test(outro), 'com o Spleef aberto, o Vietnã não abre', outro)
    await t.console(`minecraft:give ${a.username} snowball 1`)
    let msgs = await t.capturar(a, () => a.chat('/spleef entrar'))
    t.verificar(msgs.some(m => m.includes('totalmente vazio')), 'com um item, não entra', msgs.find(m => m.includes('vazio')))
    await t.console(`minecraft:clear ${a.username}`)
    for (const bot of [a, b, c]) await t.capturar(bot, () => bot.chat('/spleef entrar'), 600)
    const info = await t.capturar(d, () => d.chat('/spleef'))
    t.verificar(info.some(m => m.includes('3 inscrito')), '/spleef mostra os 3 inscritos', info.find(m => m.includes('Agora')))

    console.log('\n== Piso de cima ==')
    await t.console('spleef iniciar')
    await t.espera(2500)
    t.verificar([a, b, c].every(x => andar(x) === 2), 'os 3 no piso de cima', [a, b, c].map(x => x.entity.position.floored()).join(' | '))
    t.verificar(await quantos(a.username, 'diamond_shovel') === 0, 'sem a pá na preparação')
    await t.espera(10000)
    t.verificar(await quantos(a.username, 'diamond_shovel') === 1, 'na luta, a pá')
    msgs = await t.capturar(a, () => a.chat('/spawn'))
    t.verificar(msgs.some(m => m.includes('só o /spleef')), 'lutando, /spawn não funciona', msgs.find(m => m.includes('Spleef')))
    await t.capturar(d, () => d.chat('/spleef assistir'), 2000)
    t.verificar(naArquibancada(d), '/spleef assistir leva pra arquibancada', `${d.entity.position.floored()}`)
    msgs = await t.capturar(d, () => d.chat('/sethome casa'))
    t.verificar(msgs.some(m => m.includes('só /spleef e /spawn')), 'na arquibancada, /sethome não funciona', msgs.find(m => m.includes('Spleef')))

    console.log('\n== Quebrar a neve ==')
    const pa = a.inventory.items().find(i => i.name === 'diamond_shovel')
    if (pa) await a.equip(pa, 'hand')
    let cavou = false
    const msgsCavar = await t.capturar(a, async () => { cavou = await cavarPe(a) }, 500)
    t.verificar(cavou, 'o A quebra a neve debaixo dele com a pá')
    t.verificar(andar(a) === 1 && !(await quantos(a.username, 'snow_block')) && !(await quantos(a.username, 'snowball')),
      'e cai pro andar do meio, sem ganhar neve nem bola de neve', `andar ${andar(a)}, ${a.entity.position.floored()} ${msgsCavar.join(' | ')}`)
    t.verificar(t.itensNoChao(a, 6).length === 0, 'nada cai no chão', JSON.stringify(t.itensNoChao(a, 6)))

    console.log('\n== Eliminação ==')
    const pb = b.inventory.items().find(i => i.name === 'diamond_shovel')
    if (pb) await b.equip(pb, 'hand')
    const msgsB = await t.capturar(b, async () => {
      for (let i = 0; i < 4 && andar(b) >= 0; i++) await cavarPe(b)
      await t.espera(1500)
    }, 1500)
    t.verificar(msgsB.some(m => m.includes('caiu na água')), 'quem cai do último piso na água é eliminado', msgsB.find(m => m.includes('Spleef]')))
    t.verificar(naArquibancada(b), 'e vai pra arquibancada', `${b.entity.position.floored()}`)

    const msgsA = await t.capturar(a, async () => { c.quit() }, 3000)
    t.verificar(msgsA.some(m => m.includes('desconectou')), 'quem desconecta é eliminado', msgsA.find(m => m.includes('desconectou')))
    t.verificar(msgsA.some(m => m.includes(a.username) && m.includes('venceu')), 'o último de pé vence', msgsA.find(m => m.includes('venceu')))
    await t.espera(1000)
    t.verificar(await quantos(a.username, 'diamond_shovel') === 0 && await quantos(a.username, 'paper') === 1,
      'o vencedor perde a pá e ganha o cheque', msgsA.find(m => m.includes('cheque')))
    t.verificar(a.entity.position.distanceTo(new Vec3(0.5, 65, 43.5)) < 3, 'e volta pra onde estava', `${a.entity.position.floored()}`)
    await t.espera(2500)
    const cheia = neve(d)
    t.verificar(cheia > 3 * 600, 'a arena volta inteira depois', `${cheia} blocos de neve`)

    // o piso derrete sozinho: outro evento, com o tempo encurtado (começa com 4 s, a cada 2 s)
    console.log('\n== O piso derrete ==')
    fs.writeFileSync(CONFIG, original.replace(/(spleef:[\s\S]*?derreter:\s*\n\s*comecar-segundos:) \d+/, '$1 4'))
    await t.console('spleef reload')
    const c2 = await t.criarBot(c.username)
    todos.push(c2)
    for (const bot of [a, b, c2]) {
      await t.console(`minecraft:clear ${bot.username}`)
      await t.console(`execute in minecraft:spawn run minecraft:tp ${bot.username} 0.5 65 43.5`)
    }
    await t.espera(1500)
    await t.console('spleef abrir')
    for (const bot of [a, b, c2]) await t.capturar(bot, () => bot.chat('/spleef entrar'), 600)
    await t.console('spleef iniciar')
    await t.espera(10000 + 8000)
    const depois = neve(d)
    t.verificar(depois < cheia && depois > 0, 'com o tempo, a neve derrete', `${cheia} → ${depois}`)
  } finally {
    fs.writeFileSync(CONFIG, original)
    await t.console('spleef cancelar')
    await t.console('spleef reload')
    for (const bot of todos) {
      await t.console(`minecraft:clear ${bot.username}`)
      await t.console(`lp user ${bot.username} permission unset eventos.ip.ignorar`)
    }
  }
  await t.terminar(...todos.filter(x => x !== c))
})
