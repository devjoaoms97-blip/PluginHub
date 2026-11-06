// Evento Sobrevivência (EventosPlugin): o /eventos mostra às 22h; os 3 no pátio com o kit de ferro; sem
// fogo amigo; a onda 1 chega (com mais invasores por ter 3 jogadores) e os mobs machucam de verdade;
// vencida a onda, +16 flechas; quem morre não deixa nada no chão, está fora e renasce na galeria; as 10
// ondas (o teste mata os invasores pelo console pra andar rápido) e quem chegar vivo divide o cheque.
// As cartas de melhoria: vencida a onda, abre a janela com 3 cartas; clicou, ganhou; sem escolher, em 3 s
// sai uma ao acaso; a vida e a velocidade a mais saem na morte e no fim do evento.
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
// os modificadores das cartas (vida e velocidade) que o jogador ainda tem
const melhorias = async nome => {
  let n = 0
  for (const [atr, chave] of [['max_health', 'melhoria_vida'], ['movement_speed', 'melhoria_velocidade']]) {
    // tem: "Value of modifier ... is 4.0"; não tem: um erro
    if (/Value of modifier|is -?\d/i.test(await t.console(`attribute ${nome} minecraft:${atr} modifier value get eventosplugin:${chave}`))) n++
  }
  return n
}
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
    // conferido o dano, os bots (parados) ficam imunes: o resto do teste demora, e um Vingador matava um
    // deles antes da hora (o B morre depois, pelo /kill, que passa por cima da Resistência V)
    for (const x of [a, b, c]) await t.console(`effect give ${x.username} minecraft:resistance 600 4 true`)

    // vence a onda 1 logo, antes que os invasores matem alguém
    const flechasAntes = await quantos(a.username, 'arrow')
    const janela = new Promise(res => { a.once('windowOpen', res); setTimeout(() => res(null), 4000) })
    let msgsA = [], msgsC = []
    msgsC = await t.capturar(c, async () => {
      msgsA = await t.capturar(a, async () => {
        await t.console(MATAR)
        const w = await janela
        const cartas = w ? [2, 4, 6].map(i => w.slots[i]).filter(Boolean) : []
        t.verificar(cartas.length === 3, 'vencida a onda, abre a janela com 3 cartas de melhoria', cartas.map(x => x.name).join(', '))
        if (w) await a.clickWindow(2, 0, 0)
        await t.espera(1000)
      }, 300)
      await t.espera(3500) // o C não escolhe: em 3 s sai uma ao acaso
    }, 300)
    t.verificar(msgsA.some(m => m.includes('Melhoria:')), 'clicou na carta, ganhou a melhoria', msgsA.find(m => m.includes('Melhoria')))
    t.verificar(msgsC.some(m => m.includes('O tempo acabou')), 'sem escolher, em 3 s sai uma das 3 ao acaso', msgsC.find(m => m.includes('tempo acabou')))
    t.verificar(await quantos(a.username, 'arrow') === flechasAntes + 16, 'vencida a onda, +16 flechas', `${flechasAntes} → ${await quantos(a.username, 'arrow')}`)
    // uma carta de Vida certa pro B, pra conferir que sai na morte
    await t.console(`attribute ${b.username} minecraft:max_health modifier add eventosplugin:melhoria_vida 4 add_value`)

    console.log('\n== Encostado na grade ==')
    // a grade do portão norte fica na fileira da muralha (z 1484): quem encosta nela fica com o meio do
    // corpo já nessa fileira, e isso não pode contar como sair da arena (o teleporte na luta precisa do eventos.admin)
    await t.console(`lp user ${c.username} permission set eventos.admin true`)
    await t.espera(500)
    const msgsGrade = await t.capturar(d, async () => {
      await t.console(`execute in minecraft:arenas run minecraft:tp ${c.username} ${CENTRO[0] + 0.5} ${CHAO + 1} ${CENTRO[1] - RAIO - 1 + 0.87} 180 0`)
      await t.espera(2500)
    }, 500)
    await t.console(`lp user ${c.username} permission unset eventos.admin`)
    t.verificar(c.entity.position.z < CENTRO[1] - RAIO && !msgsGrade.some(m => m.includes(c.username) && /saiu|fora/.test(m)),
      'encostado na grade do portão, continua no evento', `${c.entity.position} | ${msgsGrade.find(m => m.includes(c.username)) || ''}`)
    await t.console(`lp user ${c.username} permission set eventos.admin true`)
    await t.espera(300)
    await t.console(`execute in minecraft:arenas run minecraft:tp ${c.username} ${CENTRO[0] + 3.5} ${CHAO + 1} ${CENTRO[1] + 0.5}`)
    await t.console(`lp user ${c.username} permission unset eventos.admin`)

    console.log('\n== Morrer ==')
    const msgsMorte = await t.capturar(d, async () => { await t.console(`minecraft:kill ${b.username}`) }, 3000)
    t.verificar(msgsMorte.some(m => m.includes(b.username) && m.includes('morreu')), 'quem morre está fora', msgsMorte.find(m => m.includes('morreu')))
    t.verificar(naGaleria(b), 'e renasce na galeria', `${b.entity.position.floored()}`)
    t.verificar(await quantos(b.username, 'iron_sword') === 0, 'sem o kit')
    t.verificar(await melhorias(b.username) === 0, 'e sem a vida e a velocidade das cartas')
    const noChao = await t.console(`execute in minecraft:arenas if entity @e[type=item,x=${CENTRO[0] - RAIO},y=${CHAO},z=${CENTRO[1] - RAIO},dx=${2 * RAIO},dy=14,dz=${2 * RAIO}]`)
    t.verificar(/failed/i.test(noChao), 'e nada do kit cai no chão', noChao)

    console.log('\n== As 10 ondas ==')
    let fim = []
    const capt = t.capturar(a, async () => {
      for (let n = 2; n <= 10; n++) {
        // espera a onda chegar (a pausa encurtada), em vez de um tempo fixo: matar antes dela nascer
        // deixava o teste uma onda atrasado
        let e = ''
        for (let i = 0; i < 20 && !new RegExp(`onda ${n} de 10, [1-9]`).test(e); i++) {
          await t.espera(500)
          e = await estado(d)
          // um vex que o evocador chamou logo antes de morrer escapa do kill: mata de novo
          if (new RegExp(`onda ${n - 1} de 10, [1-9]`).test(e)) await t.console(MATAR)
        }
        if (n === 5 || n === 10) t.verificar(new RegExp(`onda ${n} de 10`).test(e), `a onda ${n} (com chefe) chega`, e)
        await t.console(MATAR)
      }
      // a última: até ela cair de vez (os vexes de novo)
      for (let i = 0; i < 10 && /onda 10 de 10, [1-9]/.test(await estado(d)); i++) {
        await t.console(MATAR)
        await t.espera(500)
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
    t.verificar(await melhorias(a.username) === 0 && await melhorias(c.username) === 0, 'e as cartas saem no fim do evento')
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
