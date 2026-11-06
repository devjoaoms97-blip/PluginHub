// Evento Labirinto do Fauno (EventosPlugin): o /eventos mostra às 8h e às 23h; os 3 nascem na borda do
// labirinto, sem nada; quem assiste fica em cima do teto; lá dentro é escuro (luz 0); os baús estão no
// labirinto, com itens; a barra no alto mostra o tempo e os vivos; o PvP vale entre quem luta (até entre
// dois do mesmo clã, que fora do evento não se machucam); o Fauno
// acorda, com o modelo junto, e mata quem está perto ("foi pego pelo Fauno"); quem morre não deixa nada
// e vai pro teto; o último vivo leva o cheque e perde o que achou. Na segunda rodada: o labirinto é outro
// e, no tempo, com 2 vivos, ninguém vence.
// O teste encurta o sono do Fauno e o tempo da 2ª rodada no config do servidor (e devolve o original no fim).
// Uso: node teste-labirinto.js
const fs = require('fs')
const path = require('path')
const t = require('./lib')

const CONFIG = path.join(process.env.MC_TESTE || 'C:\\mc-teste', 'plugins', 'EventosPlugin', 'config.yml')
const CENTRO = [-200, 1800]
const LADO = 124
const X0 = CENTRO[0] - LADO / 2, Z0 = CENTRO[1] - LADO / 2
const CHAO = 64
const TETO = CHAO + 5
const CAIXA = `x=${X0},y=${CHAO},z=${Z0},dx=${LADO},dy=5,dz=${LADO}`

const quantos = async (nome, item) => Number(((await t.console(`minecraft:clear ${nome} ${item} 0`)).match(/Found (\d+)/) || [, 0])[1])
const conta = async sel => Number(((await t.console(`execute in minecraft:arenas if entity ${sel}`)).match(/count: (\d+)/i) || [, 0])[1])
const pos = bot => bot.entity.position
const noLabirinto = b => pos(b).x >= X0 && pos(b).x < X0 + LADO && pos(b).z >= Z0 && pos(b).z < Z0 + LADO && pos(b).y < TETO
const noTeto = b => pos(b).x >= X0 - 1 && pos(b).x < X0 + LADO + 1 && pos(b).z >= Z0 - 1 && pos(b).z < Z0 + LADO + 1 && pos(b).y >= TETO
const mudar = (texto, chave, valor) => texto.replace(new RegExp(`(\\nlabirinto:[\\s\\S]*?\\n    ${chave}:) [^\\n]*`), `$1 ${valor}`)

/** Os baús que o bot enxerga (os chunks do labirinto estão carregados pra ele). */
function baus(bot) {
  const id = bot.registry.blocksByName.chest.id
  return bot.findBlocks({ matching: id, maxDistance: 96, count: 100 })
    .filter(p => p.x >= X0 && p.x < X0 + LADO && p.z >= Z0 && p.z < Z0 + LADO && p.y === CHAO + 1)
}

async function ate(fn, limiteMs, passoMs = 300) {
  for (const fim = Date.now() + limiteMs; Date.now() < fim;) {
    if (await fn()) return true
    await t.espera(passoMs)
  }
  return false
}

t.rodar(async () => {
  const original = fs.readFileSync(CONFIG, 'utf8')
  for (const e of ['vietna', 'spleef', 'correoucai', 'sobrevivencia', 'corrida', 'labirinto']) await t.console(`${e} cancelar`)
  fs.writeFileSync(CONFIG, mudar(original, 'acorda-segundos', 3))
  await t.console('labirinto reload')
  console.log(await t.console('labirinto construir'))
  const a = await t.criarBot(t.nomeNovo('Lba'))
  const b = await t.criarBot(t.nomeNovo('Lbb'))
  const c = await t.criarBot(t.nomeNovo('Lbc'))
  const d = await t.criarBot(t.nomeNovo('Lbd'))
  const todos = [a, b, c, d]
  const jogadores = [a, b, c]
  // a e b no mesmo clã: fora do evento, o ClasPlugin não deixa um machucar o outro; no Labirinto, deixa
  const tag = 'L' + Date.now().toString(36).slice(-4)
  await t.console(`eco give ${a.username} 5000`)
  await t.capturar(a, () => a.chat(`/cla criar ${tag} Labirinto`))
  await t.capturar(b, () => a.chat(`/cla convidar ${b.username}`))
  await t.capturar(b, () => b.chat(`/cla aceitar ${tag}`))
  const preparar = async () => {
    for (const bot of todos) {
      await t.console(`minecraft:clear ${bot.username}`)
      await t.console(`gamemode survival ${bot.username}`)
      await t.console(`execute in minecraft:spawn run minecraft:tp ${bot.username} 0.5 65 43.5`)
    }
    await t.espera(1500)
  }
  try {
    for (const bot of todos) await t.console(`lp user ${bot.username} permission set eventos.ip.ignorar true`)
    await preparar()

    console.log('\n== /eventos e inscrição ==')
    let msgs = await t.capturar(d, () => d.chat('/eventos'))
    t.verificar(['08:00', '23:00'].every(h => msgs.some(m => m.includes('Labirinto do Fauno') && m.includes(h))), '/eventos mostra o Labirinto às 8h e às 23h',
      msgs.find(m => m.includes('Labirinto')))
    // só as linhas da agenda (o "Próximo:" de cima também tem nome e hora)
    const agenda = msgs.indexOf(msgs.find(m => m.includes('Agenda:')))
    const linha = (nome, hora) => msgs.findIndex((m, i) => i > agenda && m.includes(nome) && m.includes(hora))
    const ordem = [linha('Labirinto', '08:00'), linha('Corrida', '09:00'), linha('Corrida', '14:00'), linha('Labirinto', '23:00')]
    t.verificar(ordem.every((i, k) => i >= 0 && (k === 0 || i > ordem[k - 1])), '/eventos: um evento por linha, na ordem do horário', ordem.join(', '))
    msgs = await t.capturar(d, () => d.chat('/labirinto'))
    t.verificar(msgs.some(m => m.includes('ninguém')), '/labirinto explica que, no tempo, ninguém vence', msgs.find(m => m.includes('ninguém')))
    await t.console('labirinto abrir')
    for (const bot of jogadores) await t.capturar(bot, () => bot.chat('/labirinto entrar'), 600)

    console.log('\n== Largada ==')
    const barras = []
    a.on('bossBarCreated', bb => barras.push(bb))
    await t.console('labirinto iniciar')
    await t.capturar(d, () => d.chat('/labirinto assistir'), 2500)
    t.verificar(jogadores.every(noLabirinto), 'os 3 no labirinto', jogadores.map(x => pos(x).floored()).join(' | '))
    const doMeio = x => Math.max(Math.abs(pos(x).x - CENTRO[0]), Math.abs(pos(x).z - CENTRO[1]))
    t.verificar(jogadores.every(x => doMeio(x) >= LADO / 2 - 4), 'cada um nasce na borda', jogadores.map(doMeio).map(Math.round).join(', '))
    t.verificar(noTeto(d), '/labirinto assistir leva pra cima do teto', `${pos(d).floored()}`)
    // a luz do servidor no pé de cada um (céu e blocos juntos)
    const p0 = pos(a).floored()
    const escuro = await t.console(`execute in minecraft:arenas positioned ${p0.x} ${p0.y} ${p0.z} if predicate {condition:"minecraft:location_check",predicate:{light:{light:{max:0}}}}`)
    t.verificar(/passed/i.test(escuro), 'lá dentro é escuro: luz 0, nem do céu', escuro)
    const efeitos = await t.console(`data get entity ${a.username} active_effects`)
    t.verificar(/darkness/.test(efeitos), 'e quem luta fica com o efeito Escuridão (pulsos, sem enxergar longe)', efeitos.slice(0, 120))
    t.verificar(!/darkness/.test(await t.console(`data get entity ${d.username} active_effects`)), 'quem assiste, não')
    const bausA = baus(a)
    t.verificar(bausA.length >= 15, 'os baús estão no labirinto', `${bausA.length} baús`)
    if (bausA.length) {
      const p = bausA[0]
      const dentro = await t.console(`execute in minecraft:arenas run data get block ${p.x} ${p.y} ${p.z} Items`)
      t.verificar(/minecraft:/.test(dentro), 'e têm itens', dentro.slice(0, 160))
    }

    console.log('\n== Luta ==')
    await t.espera(9000)
    t.verificar(jogadores.every(x => Object.keys(t.inventario(x)).length === 0), 'ninguém ganha kit: o que tiver, acha nos baús',
      jogadores.map(x => JSON.stringify(t.inventario(x))).join(' | '))
    const barra = barras.map(bb => t.semCor(String(bb.title))).find(s => s.includes('Labirinto')) || ''
    t.verificar(/(9:5\d|10:00) restantes/.test(barra) && barra.includes('3 vivos'), 'a barra no alto: o tempo que falta e os vivos', barra)
    const vida = b.health
    await t.console(`damage ${b.username} 4 minecraft:player_attack by ${a.username}`)
    await t.espera(600)
    t.verificar(b.health < vida, 'o PvP vale entre quem luta, até do mesmo clã', `vida ${vida} → ${b.health}`)
    await t.console(`effect give ${b.username} minecraft:instant_health 1 3 true`)

    console.log('\n== O Fauno ==')
    t.verificar(a.mensagens.some(m => m.includes('O Fauno acordou')), 'o Fauno acorda', a.mensagens.find(m => m.includes('acordou')))
    const husk = `@e[type=minecraft:husk,${CAIXA}]`
    t.verificar(await conta(husk) === 1, 'o Fauno está lá (um mob que anda e bate)')
    t.verificar(await conta(`@e[type=minecraft:item_display,${CAIXA}]`) === 1, 'com o modelo do Fauno')
    // a e b ficam a salvo (Resistência V); o Fauno vai pra cima do c
    for (const x of [a, b]) await t.console(`effect give ${x.username} minecraft:resistance 600 4 true`)
    await t.console(`execute in minecraft:arenas run minecraft:tp ${husk} ${c.username}`)
    await t.espera(1500)
    const junto = await t.console(`execute in minecraft:arenas at ${husk} if entity @e[type=minecraft:item_display,distance=..1.5]`)
    t.verificar(/passed/i.test(junto), 'o modelo anda junto com o mob', junto)
    msgs = []
    const morreu = await ate(async () => {
      msgs.push(...a.mensagens.splice(0))
      return msgs.some(m => m.includes(c.username) && m.includes('pego pelo'))
    }, 20000, 500)
    t.verificar(morreu, 'o Fauno mata quem está perto: "foi pego pelo Fauno"', msgs.find(m => m.includes('pego pelo')))
    await t.espera(2000)
    t.verificar(noTeto(c), 'quem morre vai pra cima do teto', `${pos(c).floored()}`)
    t.verificar(Object.keys(t.inventario(c)).length === 0 && /failed/i.test(await t.console(`execute in minecraft:arenas if entity @e[type=minecraft:item,${CAIXA}]`)),
      'e não deixa nada', JSON.stringify(t.inventario(c)))

    console.log('\n== O último vivo ==')
    await t.console(`minecraft:give ${a.username} minecraft:iron_sword`)
    await t.console(`effect clear ${b.username}`)
    msgs = await t.capturar(d, () => t.console(`damage ${b.username} 100 minecraft:player_attack by ${a.username}`), 3000)
    t.verificar(msgs.some(m => m.includes(b.username) && m.includes(`derrotado por ${a.username}`)), 'quem mata aparece no anúncio',
      msgs.find(m => m.includes(b.username)))
    const venceu = msgs.find(m => m.includes('venceu'))
    t.verificar(!!venceu && venceu.includes(a.username) && venceu.includes('10.000'), 'o último vivo vence e leva R$ 10.000', venceu)
    t.verificar(await quantos(a.username, 'paper') === 1 && await quantos(a.username, 'iron_sword') === 0, 'o vencedor leva o cheque e perde o que achou')
    t.verificar(!noLabirinto(a) && !noTeto(a), 'e volta pra onde estava', `${pos(a).floored()}`)
    t.verificar(!/darkness/.test(await t.console(`data get entity ${a.username} active_effects`)), 'sem a Escuridão')
    await t.espera(2500)
    t.verificar(await conta(husk) === 0 && await conta(`@e[type=minecraft:item_display,${CAIXA}]`) === 0, 'o Fauno some no fim')

    console.log('\n== 2ª rodada: no tempo, ninguém vence ==')
    fs.writeFileSync(CONFIG, mudar(mudar(original, 'acorda-segundos', 600), 'tempo-maximo-minutos', 1))
    await t.console('labirinto reload')
    await preparar()
    await t.console('labirinto abrir')
    for (const bot of [a, b]) await t.capturar(bot, () => bot.chat('/labirinto entrar'), 600)
    await t.console('labirinto iniciar')
    await t.espera(2500)
    const bausDepois = baus(a)
    const chave = l => l.map(p => `${p.x},${p.z}`).sort().join(' ')
    t.verificar(bausDepois.length >= 15 && chave(bausDepois) !== chave(bausA), 'o labirinto é outro (os baús mudaram de lugar)', `${bausDepois.length} baús`)
    for (const x of [a, b]) await t.console(`effect give ${x.username} minecraft:resistance 600 4 true`)
    await t.console(`minecraft:give ${a.username} minecraft:golden_apple`)
    msgs = await t.capturar(d, () => t.espera(73000), 2000)
    const fim = msgs.find(m => m.includes('tempo acabou'))
    t.verificar(!!fim && fim.includes('2 vivos') && fim.includes('ninguém venceu'), 'com 2 vivos no fim do tempo, ninguém vence', fim)
    t.verificar(await quantos(a.username, 'paper') === 0 && await quantos(a.username, 'golden_apple') === 0 && !noLabirinto(a),
      'sem cheque, sem o que achou e de volta pra onde estava')
  } finally {
    fs.writeFileSync(CONFIG, original)
    await t.console('labirinto cancelar')
    await t.console('labirinto reload')
    for (const bot of todos) {
      await t.console(`minecraft:clear ${bot.username}`)
      await t.console(`effect clear ${bot.username}`)
      await t.console(`lp user ${bot.username} permission unset eventos.ip.ignorar`)
    }
  }
  await t.terminar(...todos)
})
