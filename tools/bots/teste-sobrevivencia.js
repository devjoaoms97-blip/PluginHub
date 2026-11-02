// Evento Sobrevivência (EventosPlugin): o /eventos mostra às 22h; os 3 no pátio com o kit de ferro; sem
// fogo amigo; a onda 1 chega (com mais invasores por ter 3 jogadores) e os mobs machucam de verdade;
// vencida a onda, +16 flechas; quem morre não deixa nada no chão, está fora e renasce na galeria; as 10
// ondas (o teste mata os invasores pelo console pra andar rápido) e quem chegar vivo divide o cheque.
// O teste encurta a pausa entre as ondas no config do servidor (e devolve o original no fim).
// Uso: node teste-sobrevivencia.js
const fs = require('fs')
const path = require('path')
const t = require('./lib')

const CONFIG = path.join(process.env.MC_TESTE || 'C:\\mc-teste', 'plugins', 'EventosPlugin', 'config.yml')
const CENTRO = [0, 1500]
const RAIO = 15
const CHAO = 64
const MATAR = `execute in minecraft:arenas run minecraft:kill @e[type=!player,x=${CENTRO[0] - RAIO},y=${CHAO},z=${CENTRO[1] - RAIO},dx=${2 * RAIO},dy=14,dz=${2 * RAIO}]`

const quantos = async (nome, item) => Number(((await t.console(`minecraft:clear ${nome} ${item} 0`)).match(/Found (\d+)/) || [, 0])[1])
const noPatio = b => Math.abs(b.entity.position.x - CENTRO[0]) <= RAIO + 1 && Math.abs(b.entity.position.z - CENTRO[1]) <= RAIO + 1 && b.entity.position.y < CHAO + 4
const naGaleria = b => b.entity.position.z < CENTRO[1] - RAIO - 1 && b.entity.position.y > CHAO + 7
const estado = async bot => (await t.capturar(bot, () => bot.chat('/sobrevivencia'), 1000)).find(m => m.includes('Agora')) || ''

t.rodar(async () => {
  const original = fs.readFileSync(CONFIG, 'utf8')
  fs.writeFileSync(CONFIG, original.replace(/(sobrevivencia:[\s\S]*?pausa-segundos:) \d+/, '$1 2'))
  for (const e of ['vietna', 'spleef', 'correoucai', 'sobrevivencia']) await t.console(`${e} cancelar`)
  await t.console('sobrevivencia reload')
  const a = await t.criarBot(t.nomeNovo('Sva'))
  const b = await t.criarBot(t.nomeNovo('Svb'))
  const c = await t.criarBot(t.nomeNovo('Svc'))
  const d = await t.criarBot(t.nomeNovo('Svd'))
  const todos = [a, b, c, d]
  try {
    for (const bot of todos) {
      await t.console(`minecraft:clear ${bot.username}`)
      await t.console(`gamemode survival ${bot.username}`)
      await t.console(`lp user ${bot.username} permission set eventos.ip.ignorar true`)
      await t.console(`execute in minecraft:spawn run minecraft:tp ${bot.username} 0.5 65 43.5`)
    }
    await t.espera(1500)
    console.log('  dificuldade das arenas:', await t.console('execute in minecraft:arenas run difficulty'))

    console.log('\n== /eventos e inscrição ==')
    let msgs = await t.capturar(d, () => d.chat('/eventos'))
    t.verificar(msgs.some(m => m.includes('Sobrevivência') && m.includes('22:00')), '/eventos mostra a Sobrevivência às 22h', msgs.find(m => m.includes('Sobreviv')))
    await t.console('sobrevivencia abrir')
    for (const bot of [a, b, c]) await t.capturar(bot, () => bot.chat('/sobrevivencia entrar'), 600)
    await t.console('sobrevivencia iniciar')
    await t.capturar(d, () => d.chat('/sobrevivencia assistir'), 2000)
    t.verificar([a, b, c].every(noPatio), 'os 3 no pátio do castelo', [a, b, c].map(x => x.entity.position.floored()).join(' | '))
    t.verificar(naGaleria(d), '/sobrevivencia assistir leva pra galeria', `${d.entity.position.floored()}`)

    console.log('\n== Kit e fogo amigo ==')
    await t.espera(9000)
    // logo no começo da luta, antes da onda 1 (ela chega 3 s depois)
    await t.console(`damage ${b.username} 6 minecraft:player_attack by ${a.username}`)
    await t.espera(500)
    t.verificar(b.health >= 20, 'sem fogo amigo: um jogador não machuca o outro', `vida ${b.health}`)
    // os bots ficam parados: com Resistência IV (80% a menos) e Regeneração eles tomam dano, mas aguentam as 10 ondas
    for (const x of [a, b, c]) {
      await t.console(`effect give ${x.username} minecraft:resistance 600 3 true`)
      await t.console(`effect give ${x.username} minecraft:regeneration 600 1 true`)
    }
    const kit = {}
    for (const item of ['iron_helmet', 'iron_chestplate', 'iron_leggings', 'iron_boots', 'iron_sword', 'bow', 'arrow', 'golden_apple']) kit[item] = await quantos(a.username, item)
    t.verificar(Object.values(kit).slice(0, 6).every(n => n === 1) && kit.arrow === 32 && kit.golden_apple === 4,
      'o kit de ferro: armadura, espada, arco, 32 flechas e 4 maçãs douradas', JSON.stringify(kit))

    console.log('\n== Onda 1 ==')
    await t.espera(2500)
    let agora = await estado(d)
    t.verificar(/onda 1 de 10, ([1-9]\d*) invasor/.test(agora), 'a onda 1 chega', agora)
    const invasores = Number((agora.match(/(\d+) invasor/) || [, 0])[1])
    t.verificar(invasores === 8, 'com 3 jogadores, 50% a mais por jogador: 3 saqueadores e 1 vingador viram 6 e 2', `${invasores} invasores`)
    // os mobs machucam de verdade (os bots ficam parados)
    let machucou = false
    for (let i = 0; i < 30 && !machucou; i++) {
      machucou = [a, b, c].some(x => x.health < 20)
      await t.espera(500)
    }
    t.verificar(machucou, 'os invasores machucam de verdade', [a, b, c].map(x => x.health).join(', '))

    // vence a onda 1 logo, antes que os invasores matem alguém
    const flechasAntes = await quantos(a.username, 'arrow')
    await t.console(MATAR)
    await t.espera(2500)
    t.verificar(await quantos(a.username, 'arrow') === flechasAntes + 16, 'vencida a onda, +16 flechas', `${flechasAntes} → ${await quantos(a.username, 'arrow')}`)

    console.log('\n== Morrer ==')
    const msgsMorte = await t.capturar(d, async () => { await t.console(`minecraft:kill ${b.username}`) }, 3000)
    t.verificar(msgsMorte.some(m => m.includes(b.username) && m.includes('morreu')), 'quem morre está fora', msgsMorte.find(m => m.includes('morreu')))
    t.verificar(naGaleria(b), 'e renasce na galeria', `${b.entity.position.floored()}`)
    t.verificar(await quantos(b.username, 'iron_sword') === 0, 'sem o kit')
    const noChao = await t.console(`execute in minecraft:arenas if entity @e[type=item,x=${CENTRO[0] - RAIO},y=${CHAO},z=${CENTRO[1] - RAIO},dx=${2 * RAIO},dy=14,dz=${2 * RAIO}]`)
    t.verificar(/failed/i.test(noChao), 'e nada do kit cai no chão', noChao)

    console.log('\n== As 10 ondas ==')
    let fim = []
    const capt = t.capturar(a, async () => {
      for (let n = 2; n <= 10; n++) {
        await t.espera(3500) // a pausa (encurtada) e a onda chegar
        if (n === 5 || n === 10) {
          const e = await estado(d)
          t.verificar(new RegExp(`onda ${n} de 10`).test(e), `a onda ${n} (com chefe) chega`, e)
        }
        await t.console(MATAR)
      }
      await t.espera(3000)
    }, 500)
    fim = await capt
    const resultado = fim.find(m => m.includes('sobreviveram') || m.includes('sobreviveu'))
    t.verificar(fim.some(m => m.includes('ONDA 10') && m.includes('CHEFE')), 'a onda 10 tem o chefe', fim.find(m => m.includes('ONDA 10')))
    t.verificar(!!resultado && resultado.includes(a.username) && resultado.includes(c.username) && !resultado.includes(b.username)
      && resultado.includes('5.000'), 'quem chegou vivo ao fim divide o prêmio (R$ 5.000 pra cada um)', resultado)
    await t.espera(1500)
    t.verificar(await quantos(a.username, 'paper') === 1 && await quantos(a.username, 'iron_sword') === 0, 'o vencedor perde o kit e ganha o cheque')
  } finally {
    fs.writeFileSync(CONFIG, original)
    await t.console('sobrevivencia cancelar')
    await t.console('sobrevivencia reload')
    for (const bot of todos) {
      await t.console(`minecraft:clear ${bot.username}`)
      await t.console(`lp user ${bot.username} permission unset eventos.ip.ignorar`)
    }
  }
  await t.terminar(...todos)
})
