// Evento Vietnã (EventosPlugin): inscrição só com o inventário vazio e uma por IP, os pilares e o kit,
// golpe sem dano, comandos e teleportes barrados na arena e na arquibancada, quem cai na água vai pra
// arquibancada, quem desconecta sai, os pilares somem com o tempo e o último de pé leva o cheque.
// O teste encurta o tempo dos pilares no config do servidor (e devolve o original no fim).
// Uso: node teste-vietna.js
const fs = require('fs')
const path = require('path')
const t = require('./lib')
const { Vec3 } = require('vec3')

const CONFIG = path.join(process.env.MC_TESTE || 'C:\\mc-teste', 'plugins', 'EventosPlugin', 'config.yml')
const AGUA = 64
const TOPO = AGUA + 6 // bloco de cima do pilar
// os 36 pilares (6 x 6, 2 de lado e 3 de vão) em volta de (0, 600): os cantos de menor x/z
const PILARES = []
for (let i = 0; i < 6; i++) for (let j = 0; j < 6; j++) PILARES.push([-13 + i * 5, 587 + j * 5])

const naArquibancada = b => b.entity.position.z < 583 && b.entity.position.z > 576 && b.entity.position.y > TOPO + 2
const noPilar = b => b.entity.position.y >= TOPO + 0.9 && b.entity.position.z > 584
const quantos = async (nome, item) => Number(((await t.console(`minecraft:clear ${nome} ${item} 0`)).match(/Found (\d+)/) || [, 0])[1])

t.rodar(async () => {
  const original = fs.readFileSync(CONFIG, 'utf8')
  await t.console('vietna cancelar')
  await t.console('vietna reload')

  const a = await t.criarBot(t.nomeNovo('Vta'))
  const b = await t.criarBot(t.nomeNovo('Vtb'))
  const c = await t.criarBot(t.nomeNovo('Vtc'))
  const d = await t.criarBot(t.nomeNovo('Vtd'))
  const todos = [a, b, c, d]
  try {
    for (const bot of todos) {
      await t.console(`minecraft:clear ${bot.username}`)
      await t.console(`gamemode survival ${bot.username}`)
      await t.console(`execute in minecraft:spawn run minecraft:tp ${bot.username} 0.5 65 43.5`)
    }
    await t.espera(1500)

    console.log('\n== Inscrição ==')
    await t.console('vietna abrir')
    await t.espera(500)
    await t.console(`minecraft:give ${a.username} dirt 1`)
    let msgs = await t.capturar(a, () => a.chat('/vietna entrar'))
    t.verificar(msgs.some(m => m.includes('inventário precisa estar totalmente vazio')), 'com um item, não entra', msgs.find(m => m.includes('Vietnã') || m.includes('inventário')))
    await t.console(`minecraft:clear ${a.username}`)
    await t.console(`minecraft:item replace entity ${a.username} armor.head with minecraft:leather_helmet`)
    msgs = await t.capturar(a, () => a.chat('/vietna entrar'))
    t.verificar(msgs.some(m => m.includes('totalmente vazio')), 'com armadura, também não', msgs.find(m => m.includes('vazio')))
    await t.console(`minecraft:clear ${a.username}`)
    msgs = await t.capturar(a, () => a.chat('/vietna entrar'))
    t.verificar(msgs.some(m => m.includes('Inscrito!')), 'vazio, entra', msgs.find(m => m.includes('Vietnã')))
    // os bots entram todos do mesmo IP: o B tenta sem a liberação dos testes
    msgs = await t.capturar(b, () => b.chat('/vietna entrar'))
    t.verificar(msgs.some(m => m.includes('uma por IP')), 'segunda inscrição do mesmo IP é barrada', msgs.find(m => m.includes('IP')))
    for (const bot of [b, c]) {
      await t.console(`lp user ${bot.username} permission set eventos.ip.ignorar true`)
      await t.espera(300)
      await t.capturar(bot, () => bot.chat('/vietna entrar'), 800)
    }
    const info = await t.capturar(d, () => d.chat('/vietna'))
    t.verificar(info.some(m => m.includes('3 inscrito')), '/vietna mostra os 3 inscritos', info.find(m => m.includes('Agora')))

    console.log('\n== Pilares ==')
    await t.console('vietna iniciar')
    await t.espera(2500)
    t.verificar([a, b, c].every(noPilar), 'os 3 em cima dos pilares', [a, b, c].map(x => x.entity.position.floored()).join(' | '))
    t.verificar(await quantos(a.username, 'bow') === 0, 'sem kit na preparação')
    await t.espera(10000)
    const kit = {}
    for (const item of ['bow', 'snowball', 'wind_charge', 'fishing_rod', 'arrow']) kit[item] = await quantos(a.username, item)
    t.verificar(kit.bow === 1 && kit.snowball === 16 && kit.wind_charge === 3 && kit.fishing_rod === 1 && kit.arrow === 1,
      'na luta, o kit: arco, 16 bolas de neve, 3 cargas de vento, vara e 1 flecha', JSON.stringify(kit))

    console.log('\n== Regras ==')
    await t.console(`damage ${b.username} 6 minecraft:player_attack by ${a.username}`)
    await t.espera(500)
    t.verificar(b.health >= 20, 'golpe não tira vida', `vida ${b.health}`)
    msgs = await t.capturar(a, () => a.chat('/spawn'))
    t.verificar(msgs.some(m => m.includes('só o /vietna')) && noPilar(a), 'lutando, /spawn não funciona', msgs.find(m => m.includes('Vietnã')))
    msgs = await t.capturar(a, () => a.chat(`/tpa ${d.username}`))
    t.verificar(msgs.some(m => m.includes('só o /vietna')), 'nem /tpa', msgs.find(m => m.includes('Vietnã')))
    await t.console(`execute in minecraft:arenas run minecraft:tp ${d.username} 0.5 75 600.5`)
    await t.espera(800)
    t.verificar(d.entity.position.z < 500, 'teleporte de fora pra dentro da arena é barrado', `${d.entity.position.floored()}`)
    msgs = await t.capturar(d, () => d.chat('/vietna assistir'), 2000)
    t.verificar(naArquibancada(d), '/vietna assistir leva pra arquibancada', `${d.entity.position.floored()}`)
    msgs = await t.capturar(d, () => d.chat('/sethome casa'))
    t.verificar(msgs.some(m => m.includes('só /vietna e /spawn')), 'na arquibancada, /sethome não funciona', msgs.find(m => m.includes('Vietnã')))
    msgs = await t.capturar(d, () => d.chat(`/tpa ${a.username}`))
    t.verificar(msgs.some(m => m.includes('só /vietna e /spawn')), 'nem /tpa', msgs.find(m => m.includes('Vietnã')))

    const pilaresDePe = () => PILARES.filter(([x, z]) => ['moss_block', 'red_concrete'].includes(d.blockAt(new Vec3(x, TOPO, z))?.name)).length

    console.log('\n== Eliminação ==')
    // o B anda pra fora do pilar e cai na água
    const msgsB = await t.capturar(b, async () => {
      b.setControlState('forward', true)
      await t.espera(2000)
      b.clearControlStates()
    }, 2500)
    t.verificar(msgsB.some(m => m.includes('caiu na água')), 'quem cai na água é eliminado', msgsB.find(m => m.includes('Vietnã')))
    t.verificar(naArquibancada(b), 'e vai pra arquibancada', `${b.entity.position.floored()}`)
    t.verificar(await quantos(b.username, 'bow') === 0 && await quantos(b.username, 'snowball') === 0, 'sem o kit')

    // o C desconecta: eliminado, e o A vence
    const msgsA = await t.capturar(a, async () => { c.quit() }, 3000)
    t.verificar(msgsA.some(m => m.includes('desconectou')), 'quem desconecta é eliminado', msgsA.find(m => m.includes('desconectou')))
    t.verificar(msgsA.some(m => m.includes(a.username) && m.includes('venceu')), 'o último de pé vence', msgsA.find(m => m.includes('venceu')))
    await t.espera(1000)
    t.verificar(await quantos(a.username, 'bow') === 0, 'o vencedor perde o kit')
    t.verificar(await quantos(a.username, 'paper') === 1, 'e ganha o cheque', msgsA.find(m => m.includes('cheque')))
    t.verificar(a.entity.position.distanceTo(new Vec3(0.5, 65, 43.5)) < 3, 'e volta pra onde estava', `${a.entity.position.floored()}`)
    await t.espera(2500)
    t.verificar(pilaresDePe() === 36, 'a arena volta inteira depois', `${pilaresDePe()} de 36`)
    msgs = await t.capturar(d, () => d.chat('/spawn'), 4500)
    t.verificar(!naArquibancada(d), 'da arquibancada, /spawn funciona', `${d.entity.position.floored()}`)

    // o C volta: nada a fazer (já tinha sido devolvido quando saiu)
    const c2 = await t.criarBot(c.username)
    await t.espera(1500)
    t.verificar(await quantos(c2.username, 'bow') === 0 && c2.entity.position.z < 500, 'quem desconectou volta sem o kit e fora da arena',
      `${c2.entity.position.floored()}`)
    todos.push(c2)

    // os pilares somem: outro evento, com o tempo encurtado (começa com 4 s, um a cada 2 s, 1 s de aviso)
    console.log('\n== Pilares somem ==')
    fs.writeFileSync(CONFIG, original.replace(/comecar-segundos: \d+/, 'comecar-segundos: 4')
      .replace(/intervalo-segundos: \d+/, 'intervalo-segundos: 2').replace(/aviso-segundos: \d+/, 'aviso-segundos: 1'))
    await t.console('vietna reload')
    for (const bot of [a, b, c2]) {
      await t.console(`minecraft:clear ${bot.username}`)
      await t.console(`lp user ${bot.username} permission set eventos.ip.ignorar true`)
      await t.console(`execute in minecraft:spawn run minecraft:tp ${bot.username} 0.5 65 43.5`)
    }
    await t.espera(1500)
    await t.console('vietna abrir')
    for (const bot of [a, b, c2]) await t.capturar(bot, () => bot.chat('/vietna entrar'), 600)
    await t.console('vietna iniciar')
    await t.capturar(d, () => d.chat('/vietna assistir'), 2000)
    await t.espera(10000 + 7000) // preparação, e uns pilares somem
    const dePe = pilaresDePe()
    t.verificar(dePe < 36 && dePe > 0, 'com o tempo, os pilares somem', `${dePe} de 36 de pé`)
  } finally {
    fs.writeFileSync(CONFIG, original)
    await t.console('vietna cancelar')
    await t.console('vietna reload')
    for (const bot of todos) {
      await t.console(`minecraft:clear ${bot.username}`)
      await t.console(`lp user ${bot.username} permission unset eventos.ip.ignorar`)
    }
  }
  await t.terminar(...todos.filter(x => x !== c))
})
