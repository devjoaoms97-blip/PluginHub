// Ranking geral de vencedores (EventosPlugin): soma as vitórias de todos os eventos (1 por vitória). Dois
// bots ganham: o B vence uma Corrida Maluca (o A chega em 2º, que não conta) e o A vence dois Vietnãs (o B
// cai na água). Confere o /eventos top (a ordem dos dois e a posição de quem pediu), o /eventos top
// <jogador> (as vitórias por evento), os recordes e o holograma do saguão das arenas.
// Os bots vão por teleporte (com eventos.admin, que libera teleporte na luta).
// Uso: node teste-ranking.js
const t = require('./lib')

const tp = (bot, mundo, x, y, z) => t.console(`execute in minecraft:${mundo} run minecraft:tp ${bot.username} ${x} ${y} ${z} 0 0`)

/** Abre o evento, inscreve os bots, começa e espera a preparação acabar. */
async function comecar(evento, bots) {
  await t.console(`${evento} abrir`)
  for (const bot of bots) await t.capturar(bot, () => bot.chat(`/${evento} entrar`), 600)
  await t.console(`${evento} iniciar`)
  await t.espera(11000)
}

/** Quem perdeu fica na arquibancada, no mundo das arenas, e de lá não se inscreve: volta pro spawn. */
async function voltar(bots) {
  for (const bot of bots) await t.console(`execute in minecraft:spawn run minecraft:tp ${bot.username} 0.5 65 43.5`)
  await t.espera(1000)
}

t.rodar(async () => {
  for (const e of ['vietna', 'spleef', 'correoucai', 'sobrevivencia', 'corrida']) await t.console(`${e} cancelar`)
  const a = await t.criarBot(t.nomeNovo('Rka'))
  const b = await t.criarBot(t.nomeNovo('Rkb'))
  const todos = [a, b]
  try {
    for (const bot of todos) {
      await t.console(`minecraft:clear ${bot.username}`)
      await t.console(`gamemode survival ${bot.username}`)
      await t.console(`lp user ${bot.username} permission set eventos.ip.ignorar true`)
      await t.console(`execute in minecraft:spawn run minecraft:tp ${bot.username} 0.5 65 43.5`)
    }
    await t.espera(1500)
    let msgs = await t.capturar(a, () => a.chat('/eventos top'))
    t.verificar(msgs.some(m => m.includes('Vencedores de eventos')) && msgs.some(m => m.includes('ainda não venceu')),
      'antes de vencer: o top e "você ainda não venceu"', msgs.find(m => m.includes('Você')))

    console.log('\n== B vence a Corrida, A chega em 2º ==')
    await comecar('corrida', todos)
    for (const bot of todos) await t.console(`lp user ${bot.username} permission set eventos.admin true`)
    await tp(b, 'arenas', 0.5, 71, 1937.5)
    await t.espera(1200)
    await tp(a, 'arenas', 0.5, 71, 1937.5)
    await t.espera(3000)
    for (const bot of todos) await t.console(`minecraft:clear ${bot.username}`)
    await voltar(todos)

    for (let i = 1; i <= 2; i++) {
      console.log(`\n== A vence o Vietnã (${i}) ==`)
      for (const bot of todos) await t.console(`lp user ${bot.username} permission unset eventos.admin`)
      await t.espera(500)
      await comecar('vietna', todos)
      await t.console(`lp user ${b.username} permission set eventos.admin true`)
      const fim = await t.capturar(a, () => tp(b, 'arenas', 0.5, 63, 600.5), 3000)
      t.verificar(fim.some(m => m.includes(a.username) && m.includes('venceu')), `o A vence o Vietnã (${i})`, fim.find(m => m.includes('venceu')))
      for (const bot of todos) await t.console(`minecraft:clear ${bot.username}`)
      await voltar(todos)
    }
    for (const bot of todos) await t.console(`lp user ${bot.username} permission unset eventos.admin`)

    console.log('\n== /eventos top ==')
    await t.console(`execute in minecraft:spawn run minecraft:tp ${a.username} 0.5 65 43.5`)
    await t.espera(1000)
    msgs = await t.capturar(a, () => a.chat('/eventos top'))
    const voce = msgs.find(m => m.startsWith('Você:'))
    t.verificar(!!voce && voce.includes('2 vitórias'), 'mostra a posição de quem pediu, com as 2 vitórias', voce)
    t.verificar(msgs.some(m => m.startsWith('Recordes')) && msgs.some(m => m.includes('Corrida Maluca')) && msgs.some(m => m.includes('Gladiador')),
      'e os recordes de cada evento (o Gladiador também)', msgs.filter(m => m.includes(' - ')).slice(-8).join(' | '))
    msgs = await t.capturar(b, () => b.chat('/eventos top'))
    const posA = Number((voce || '').match(/(\d+)º/)?.[1]), posB = Number((msgs.find(m => m.startsWith('Você:')) || '').match(/(\d+)º/)?.[1])
    t.verificar(posA > 0 && posB > posA, 'quem tem mais vitórias fica na frente', `A ${posA}º, B ${posB}º`)

    msgs = await t.capturar(b, () => b.chat(`/eventos top ${a.username}`))
    t.verificar(msgs.some(m => m.includes(a.username) && m.includes('2 vitórias')) && msgs.some(m => m.includes('Vietnã') && m.includes('2 vitórias'))
      && !msgs.some(m => m.includes('Corrida')), '/eventos top <jogador>: as do A são as 2 do Vietnã (o 2º lugar na corrida não conta)',
    msgs.slice(0, 4).join(' | '))
    msgs = await t.capturar(a, () => a.chat(`/eventos top ${b.username}`))
    t.verificar(msgs.some(m => m.includes('Corrida Maluca') && m.includes('1 vitória')), 'e a do B é a da Corrida', msgs.slice(0, 3).join(' | '))

    console.log('\n== Holograma ==')
    // o holograma só nasce com o saguão carregado (alguém perto): aparece em até 1 minuto
    await tp(a, 'arenas', 0.5, 65, 0.5)
    let holo = ''
    for (const fim = Date.now() + 65000; Date.now() < fim && !/VENCEDORES/.test(holo);) {
      await t.espera(3000)
      holo = await t.console('execute in minecraft:arenas positioned 0.5 65.5 5.5 run data get entity @e[type=minecraft:text_display,tag=eventos-ranking,limit=1,distance=..2] text')
    }
    t.verificar(/VENCEDORES DE EVENTOS/.test(holo), 'o holograma do saguão mostra o ranking', holo.slice(0, 160))
  } finally {
    for (const e of ['vietna', 'corrida']) await t.console(`${e} cancelar`)
    for (const bot of todos) {
      await t.console(`minecraft:clear ${bot.username}`)
      await t.console(`lp user ${bot.username} permission unset eventos.ip.ignorar`)
      await t.console(`lp user ${bot.username} permission unset eventos.admin`)
    }
  }
  await t.terminar(...todos)
})
