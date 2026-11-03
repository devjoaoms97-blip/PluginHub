// Corrida Maluca (EventosPlugin): /eventos às 9h e 14h; os 3 na largada, sem kit, atrás do portão, que abre
// no "valendo"; as portas (de 3, só uma abre; a falsa avisa); checkpoint, e quem cai na água volta pra ele
// (sem sair do evento); o trampolim joga por cima do vão; o tapete empurra pra trás; as plataformas somem e
// voltam; as barras giram e empurram; os canhões soltam vento; a chegada em ordem fecha o pódio e paga os
// 3 cheques; e a pista volta inteira.
// Os bots não fazem parkour: pulam de um obstáculo pro outro por teleporte (com eventos.admin, que libera
// teleporte na luta). Se a pista não existir, o teste constrói (/corrida construir).
// Uso: node teste-corrida.js
const t = require('./lib')
const { Vec3 } = require('vec3')

const Z0 = 1800, Y = 71 // o pé em cima do piso (70)
const quantos = async (nome, item) => Number(((await t.console(`minecraft:clear ${nome} ${item} 0`)).match(/Found (\d+)/) || [, 0])[1])
const bloco = async (x, y, z, tipo) => /passed/.test(await t.console(`execute in minecraft:arenas if block ${x} ${y} ${z} minecraft:${tipo}`))
const tp = (bot, x, z, y = Y) => t.console(`execute in minecraft:arenas run minecraft:tp ${bot.username} ${x} ${y} ${z} 0 0`)
const pos = bot => bot.entity.position

/** Repete a checagem até dar certo (ou acabar o tempo). */
async function ate(fn, limiteMs, passoMs = 300) {
  for (const fim = Date.now() + limiteMs; Date.now() < fim;) {
    if (await fn()) return true
    await t.espera(passoMs)
  }
  return false
}

t.rodar(async () => {
  for (const e of ['vietna', 'spleef', 'correoucai', 'sobrevivencia', 'corrida']) await t.console(`${e} cancelar`)
  if (!(await bloco(-6, 70, Z0, 'white_concrete'))) {
    console.log('  construindo a pista...')
    console.log('  ' + await t.console('corrida construir'))
  }
  const a = await t.criarBot(t.nomeNovo('Cra'), { empurroes: true })
  const b = await t.criarBot(t.nomeNovo('Crb'), { empurroes: true })
  const c = await t.criarBot(t.nomeNovo('Crc'), { empurroes: true })
  const d = await t.criarBot(t.nomeNovo('Crd'))
  const corredores = [a, b, c], todos = [a, b, c, d]
  try {
    for (const bot of todos) {
      await t.console(`minecraft:clear ${bot.username}`)
      await t.console(`gamemode survival ${bot.username}`)
      await t.console(`lp user ${bot.username} permission set eventos.ip.ignorar true`)
      await t.console(`execute in minecraft:spawn run minecraft:tp ${bot.username} 0.5 65 43.5`)
    }
    await t.espera(1500)

    console.log('\n== /eventos e inscrição ==')
    let msgs = await t.capturar(d, () => d.chat('/eventos'))
    t.verificar(msgs.some(m => m.includes('Corrida Maluca') && m.includes('09:00') && m.includes('14:00')),
      '/eventos mostra a Corrida Maluca às 9h e 14h', msgs.find(m => m.includes('Corrida')))
    msgs = await t.capturar(d, () => d.chat('/corrida'))
    t.verificar(msgs.some(m => m.includes('3 primeiros') && m.includes('5.000') && m.includes('2.000')), '/corrida explica os 3 prêmios',
      msgs.find(m => m.includes('primeiros')))
    await t.console('corrida abrir')
    for (const bot of corredores) await t.capturar(bot, () => bot.chat('/corrida entrar'), 600)

    console.log('\n== Largada ==')
    await t.console('corrida iniciar')
    await t.capturar(d, () => d.chat('/corrida assistir'), 2000)
    t.verificar(corredores.every(x => pos(x).z >= Z0 && pos(x).z < Z0 + 8 && pos(x).y >= 70.5), 'os 3 na largada',
      corredores.map(x => pos(x).floored()).join(' | '))
    t.verificar(pos(d).x >= 8 && pos(d).y >= 80, '/corrida assistir leva pra passarela', `${pos(d).floored()}`)
    t.verificar(await bloco(0, 72, Z0 + 8, 'light_blue_stained_glass'), 'o portão está fechado na preparação')
    const barras = await t.console('execute in minecraft:arenas if entity @e[type=minecraft:block_display,tag=eventos-corrida]')
    t.verificar(/count: 3/i.test(barras), 'as barras estão lá (uma e uma cruz)', barras)
    await t.espera(10000)
    t.verificar(await bloco(0, 72, Z0 + 8, 'air'), 'no "valendo", o portão abre')
    t.verificar(a.inventory.items().length === 0, 'sem kit nenhum', a.inventory.items().map(i => i.name).join(','))
    for (const bot of corredores) await t.console(`lp user ${bot.username} permission set eventos.admin true`)

    console.log('\n== Portas ==')
    const portas = [-5, -1, 3]
    msgs = await t.capturar(a, async () => {
      for (const x0 of portas) {
        await tp(a, x0 + 1.5, Z0 + 13.5)
        await t.espera(1500)
      }
    }, 500)
    const abertas = []
    for (const x0 of portas) if (await bloco(x0 + 1, 72, Z0 + 14, 'air')) abertas.push(x0)
    t.verificar(abertas.length === 1, 'de 3 portas, só uma abre ao encostar', `abertas: ${abertas}`)
    t.verificar(msgs.some(m => m.includes('Porta falsa')), 'a falsa avisa', msgs.filter(m => m.includes('[barra]')).join(' | '))

    console.log('\n== Trampolim ==')
    await tp(b, 0.5, Z0 + 26.5)
    let maisLonge = 0
    await ate(async () => { maisLonge = Math.max(maisLonge, pos(b).z - Z0); return false }, 3000, 100)
    t.verificar(maisLonge > 31 && pos(b).z - Z0 > 30 && pos(b).y >= 70.5, 'o slime joga por cima do vão', `foi até dz ${maisLonge.toFixed(1)}, parou em ${pos(b).floored()}`)

    console.log('\n== Checkpoint e queda ==')
    msgs = await t.capturar(a, () => tp(a, 0.5, Z0 + 48.5), 1200)
    t.verificar(msgs.some(m => m.includes('Checkpoint 1')), 'passar o checkpoint avisa', msgs.filter(m => m.includes('[barra]')).join(' | '))
    const queda = await t.capturar(b, async () => {
      msgs = await t.capturar(a, () => tp(a, 0.5, Z0 + 52.5, 63), 1500)
    }, 0)
    t.verificar(msgs.some(m => m.includes('Caiu')) && pos(a).z - Z0 >= 47 && pos(a).z - Z0 < 51 && pos(a).y >= 70.5,
      'quem cai na água volta pro checkpoint', `${pos(a).floored()} | ${msgs.filter(m => m.includes('[barra]')).join(' | ')}`)
    t.verificar(!queda.some(m => m.includes('caiu na água')), 'e continua na corrida (ninguém sai por cair)')

    console.log('\n== Plataformas ==')
    const sumiu = await ate(() => bloco(-4, 70, Z0 + 54, 'air'), 8000)
    const voltou = sumiu && await ate(() => bloco(-4, 70, Z0 + 54, 'lime_concrete'), 8000)
    t.verificar(sumiu && voltou, 'as plataformas somem e voltam', `sumiu ${sumiu}, voltou ${voltou}`)

    console.log('\n== Tapete ==')
    await tp(c, 0.5, Z0 + 83.5)
    await t.espera(800)
    const noTapete = pos(c).z
    await t.espera(3000)
    t.verificar(noTapete - pos(c).z > 1, 'o tapete empurra pra trás', `de ${noTapete.toFixed(1)} pra ${pos(c).z.toFixed(1)}`)

    console.log('\n== Barras e vento ==')
    // a barra pode pegar logo: vale chegar perto de onde foi posto e depois sair dali (ou cair e voltar pro checkpoint)
    const parado = new Vec3(3.5, Y, Z0 + 99.5)
    await tp(c, parado.x, parado.z)
    let chegou = false
    const empurrado = await ate(async () => {
      if (pos(c).distanceTo(parado) < 1) chegou = true
      return chegou && pos(c).distanceTo(parado) > 1.5
    }, 7000, 50)
    t.verificar(empurrado, 'a barra giratória empurra quem não pula', `${parado.floored()} -> ${pos(c).floored()}`)
    const vento = await ate(async () => /passed/.test(await t.console(
      `execute in minecraft:arenas if entity @e[type=minecraft:wind_charge,x=0,y=${Y},z=${Z0 + 127},distance=..12]`)), 5000)
    t.verificar(vento, 'os canhões soltam carga de vento na ponte')

    console.log('\n== Chegada ==')
    const fim = await t.capturar(d, async () => {
      for (const bot of corredores) {
        await tp(bot, 0.5, Z0 + 137.5)
        await t.espera(1200)
      }
    }, 2000)
    const ordem = corredores.map((bot, i) => fim.some(m => m.includes(bot.username) && m.includes(`chegada em ${i + 1}º`)))
    t.verificar(ordem.every(Boolean), 'cada um cruza a chegada na sua posição', fim.filter(m => m.includes('chegada')).join(' | '))
    const podio = fim.find(m => m.includes('CORRIDA MALUCA') && m.includes('1º'))
    t.verificar(!!podio && podio.includes(`1º ${a.username}`) && podio.includes('5.000') && podio.includes(`3º ${c.username}`) && podio.includes('2.000'),
      'o 3º fecha o pódio e a corrida acaba', podio)
    for (const bot of corredores) await t.console(`lp user ${bot.username} permission unset eventos.admin`)
    await t.espera(1000)
    const cheques = []
    for (const bot of corredores) cheques.push(await quantos(bot.username, 'paper'))
    t.verificar(cheques.every(n => n === 1), 'cada um do pódio ganha o seu cheque', cheques.join(','))
    t.verificar(corredores.every(x => pos(x).distanceTo(new Vec3(0.5, 65, 43.5)) < 3), 'e volta pra onde estava',
      corredores.map(x => pos(x).floored()).join(' | '))
    await t.espera(2000)
    t.verificar(await bloco(0, 72, Z0 + 8, 'light_blue_stained_glass') && await bloco((abertas[0] ?? -5) + 1, 72, Z0 + 14, 'purple_concrete'),
      'a pista volta inteira (portão e portas)')
  } finally {
    await t.console('corrida cancelar')
    for (const bot of todos) {
      await t.console(`minecraft:clear ${bot.username}`)
      await t.console(`lp user ${bot.username} permission unset eventos.ip.ignorar`)
      await t.console(`lp user ${bot.username} permission unset eventos.admin`)
    }
  }
  await t.terminar(...todos)
})
