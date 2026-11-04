// Corrida Maluca (EventosPlugin), a pista de 3 fases: /eventos às 9h e 14h; os 3 na largada, sem kit, atrás
// do portão, com a contagem regressiva (5, 4, 3, 2, 1 e "JÁ!" na tela, com som) até ele abrir; as portas (de 3,
// só uma abre; a falsa avisa); checkpoint, e quem cai na água volta pra ele (sem sair do evento); o trampolim
// joga por cima do vão; o tapete empurra pra trás; as plataformas somem e voltam (as da fase 2 também); o
// pula-pula joga de um slime pro outro; a barra gira e empurra; o martelo derruba da ponte; o carrossel leva
// junto; o parkour está lá; os canhões soltam vento; a chegada em ordem fecha o pódio e paga os 3 cheques;
// a barra do tempo aparece; quem cruza a chegada vai pra arquibancada dela; e a pista volta inteira.
// Depois, uma largada em cada um dos outros 4 mapas (cada um no seu lugar, com o nome anunciado).
// O teste prende o mapa no parque ("mapa-fixo" no config do servidor; o original volta no fim).
// Os bots não fazem parkour: pulam de um obstáculo pro outro por teleporte (com eventos.admin, que libera
// teleporte na luta). Se os mapas não existirem, o teste constrói (/corrida construir).
// Uso: node teste-corrida.js
const fs = require('fs')
const path = require('path')
const t = require('./lib')
const { Vec3 } = require('vec3')

const CONFIG = path.join(process.env.MC_TESTE || 'C:\\mc-teste', 'plugins', 'EventosPlugin', 'config.yml')
const Z0 = 1800, Y = 71 // o pé em cima do piso (70)
// os outros mapas, a cada 40 em x, e o nome de cada um
const OUTROS = [['doces', 40, 'Fábrica de Doces'], ['gelo', 80, 'Polo Norte'], ['neon', 120, 'Neon'], ['selva', 160, 'Selva']]
const fixar = async mapa => {
  fs.writeFileSync(CONFIG, fs.readFileSync(CONFIG, 'utf8').replace(/(\n  mapa-fixo:).*/, `$1 "${mapa}"`))
  await t.console('corrida reload')
}
const quantos = async (nome, item) => Number(((await t.console(`minecraft:clear ${nome} ${item} 0`)).match(/Found (\d+)/) || [, 0])[1])
const bloco = async (x, y, z, tipo) => /passed/.test(await t.console(`execute in minecraft:arenas if block ${x} ${y} ${z} minecraft:${tipo}`))
const tp = (bot, x, z, y = Y) => t.console(`execute in minecraft:arenas run minecraft:tp ${bot.username} ${x} ${y} ${z} 0 0`)
const pos = bot => bot.entity.position
/** Um bloco de um lugar que pode estar descarregado: carrega o chunk, olha e solta. */
const blocoLonge = async (x, y, z, tipo) => {
  await t.console(`execute in minecraft:arenas run forceload add ${x} ${z}`)
  const ok = await bloco(x, y, z, tipo)
  await t.console(`execute in minecraft:arenas run forceload remove ${x} ${z}`)
  return ok
}

/** Repete a checagem até dar certo (ou acabar o tempo). */
async function ate(fn, limiteMs, passoMs = 300) {
  for (const fim = Date.now() + limiteMs; Date.now() < fim;) {
    if (await fn()) return true
    await t.espera(passoMs)
  }
  return false
}

t.rodar(async () => {
  const original = fs.readFileSync(CONFIG, 'utf8')
  for (const e of ['vietna', 'spleef', 'correoucai', 'sobrevivencia', 'corrida']) await t.console(`${e} cancelar`)
  await fixar('parque')
  // os 5 mapas (o portão do último, em x 160, e a arquibancada da chegada do parque): se não estiverem lá,
  // constrói (um mapa a cada 2 s; o fim aparece no log do servidor)
  if (!(await blocoLonge(160, 72, Z0 + 8, 'light_blue_stained_glass')) || !(await blocoLonge(0, 74, Z0 + 447, 'smooth_quartz'))) {
    console.log('  construindo os mapas...')
    const LOG = path.join(process.env.MC_TESTE || 'C:\\mc-teste', 'logs', 'latest.log')
    const antes = fs.readFileSync(LOG, 'utf8').length
    console.log('  ' + await t.console('corrida construir'))
    const pronto = await ate(async () => fs.readFileSync(LOG, 'utf8').slice(antes).includes('construído (5 de 5)'), 120000, 1000)
    t.verificar(pronto, 'os 5 mapas foram construídos, um de cada vez')
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
    const barrasDeBoss = []
    a.on('bossBarCreated', bb => barrasDeBoss.push(bb))
    msgs = await t.capturar(a, () => t.console('corrida iniciar'), 500)
    t.verificar(msgs.some(m => m.includes('Mapa: Parque de Diversões')), 'o mapa da corrida é anunciado', msgs.find(m => m.includes('Mapa')))
    await t.capturar(d, () => d.chat('/corrida assistir'), 2000)
    t.verificar(corredores.every(x => pos(x).z >= Z0 && pos(x).z < Z0 + 8 && pos(x).y >= 70.5), 'os 3 na largada',
      corredores.map(x => pos(x).floored()).join(' | '))
    t.verificar(pos(d).x >= 8 && pos(d).y >= 80, '/corrida assistir leva pra passarela', `${pos(d).floored()}`)
    t.verificar(await bloco(0, 72, Z0 + 8, 'light_blue_stained_glass'), 'o portão está fechado na preparação')
    const pecas = await t.console('execute in minecraft:arenas if entity @e[type=minecraft:block_display,tag=eventos-corrida]')
    t.verificar(/count: 19/i.test(pecas), 'as peças que se mexem estão lá: 5 barras, 6 martelos (cabo e cabeça) e 2 raios do carrossel', pecas)
    // a contagem regressiva: os títulos e os sons que chegam pro corredor
    const titulos = [], sons = []
    const aoTitulo = texto => titulos.push(String(typeof texto === 'string' ? texto : JSON.stringify(texto)))
    const aoSom = () => sons.push(Date.now())
    a.on('title', aoTitulo)
    a.on('hardcodedSoundEffectHeard', aoSom)
    a.on('soundEffectHeard', aoSom)
    await t.espera(10000)
    a.removeListener('title', aoTitulo)
    a.removeListener('hardcodedSoundEffectHeard', aoSom)
    a.removeListener('soundEffectHeard', aoSom)
    const limpo = titulos.map(x => x.replace(/§./g, ''))
    const numeros = ['5', '4', '3', '2', '1'].filter(n => limpo.some(x => new RegExp(`(^|[^0-9])${n}([^0-9]|$)`).test(x)))
    t.verificar(numeros.length === 5 && limpo.some(x => x.includes('JÁ')), 'a contagem regressiva: 5, 4, 3, 2, 1 e "JÁ!" na tela',
      limpo.map(x => x.slice(0, 30)).join(' | '))
    t.verificar(sons.length >= 10, 'com som a cada segundo (e na abertura)', `${sons.length} sons`)
    t.verificar(await bloco(0, 72, Z0 + 8, 'air'), 'no "JÁ!", o portão abre')
    t.verificar(a.inventory.items().length === 0, 'sem kit nenhum', a.inventory.items().map(i => i.name).join(','))
    await t.espera(1500)
    const tituloBarra = barrasDeBoss.map(bb => String(bb.title)).find(x => x.includes('restantes')) || ''
    t.verificar(/(9:5\d|10:00) restantes/.test(tituloBarra) && tituloBarra.includes('0/3'), 'a barra no alto mostra o tempo que falta e quantos chegaram',
      tituloBarra || barrasDeBoss.map(bb => String(bb.title)).join(' | '))
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
    t.verificar(msgs.some(m => m.includes('Checkpoint 1 de 6')), 'passar o checkpoint avisa (1 de 6)', msgs.filter(m => m.includes('[barra]')).join(' | '))
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

    console.log('\n== Pula-pula ==')
    await tp(b, 0.5, Z0 + 95.5)
    maisLonge = 0
    await ate(async () => { maisLonge = Math.max(maisLonge, pos(b).z - Z0); return false }, 5000, 100)
    t.verificar(maisLonge > 111 && pos(b).z - Z0 > 111 && pos(b).y >= 70.5, 'o pula-pula joga de um slime pro outro até o chão do outro lado',
      `foi até dz ${maisLonge.toFixed(1)}, parou em ${pos(b).floored()}`)

    // as peças que se mexem podem pegar logo: vale chegar perto de onde foi posto e depois sair dali, ou cair
    // na água (a peça derrubou antes de o teste ver o bot lá) e voltar pro checkpoint
    const tirado = async (bot, parado, ms) => {
      const antes = bot.mensagens.length
      await tp(bot, parado.x, parado.z)
      let chegou = false
      return ate(async () => {
        if (pos(bot).distanceTo(parado) < 1) chegou = true
        // perto (o teleporte já chegou) mas fora de onde foi posto: empurrado antes de o teste ver
        const longe = pos(bot).distanceTo(parado)
        return (chegou && longe > 1.5) || (longe > 2.5 && Math.abs(pos(bot).z - parado.z) < 15)
          || bot.mensagens.slice(antes).some(m => m.includes('Caiu!'))
      }, ms, 50)
    }
    console.log('\n== Barra (fase 1) ==')
    let parado = new Vec3(3.5, Y, Z0 + 125.5)
    t.verificar(await tirado(c, parado, 7000), 'a barra giratória empurra quem não pula', `${parado.floored()} -> ${pos(c).floored()}`)

    console.log('\n== Martelo e carrossel (fase 2) ==')
    parado = new Vec3(0.5, Y, Z0 + 160.5)
    t.verificar(await tirado(c, parado, 6000), 'o martelo derruba quem está na ponte', `${parado.floored()} -> ${pos(c).floored()}`)
    parado = new Vec3(3.5, Y, Z0 + 209.5)
    t.verificar(await tirado(c, parado, 5000), 'o carrossel leva junto quem está em cima', `${parado.floored()} -> ${pos(c).floored()}`)
    const sumiu2 = await ate(() => bloco(-4, 70, Z0 + 220, 'air'), 6000)
    const voltou2 = sumiu2 && await ate(() => bloco(-4, 70, Z0 + 220, 'lime_concrete'), 6000)
    t.verificar(sumiu2 && voltou2, 'as plataformas da fase 2 também somem e voltam', `sumiu ${sumiu2}, voltou ${voltou2}`)

    console.log('\n== Parkour e vento (fase 3) ==')
    t.verificar(await bloco(-1, 70, Z0 + 271, 'light_blue_concrete') && await bloco(2, 71, Z0 + 274, 'pink_concrete')
      && await bloco(0, 70, Z0 + 273, 'air'), 'o parkour: blocos soltos sobre a água, em alturas diferentes')
    const vento = await ate(async () => /passed/.test(await t.console(
      `execute in minecraft:arenas if entity @e[type=minecraft:wind_charge,x=0,y=${Y},z=${Z0 + 352},distance=..18]`)), 5000)
    t.verificar(vento, 'os canhões soltam carga de vento na ponte')

    console.log('\n== Chegada ==')
    let naArquibancada = false
    const fim = await t.capturar(d, async () => {
      for (const bot of corredores) {
        await tp(bot, 0.5, Z0 + 441.5)
        await t.espera(1200)
        // o 1º, antes de a corrida acabar: na arquibancada depois da chegada (atrás do vidro, no alto)
        if (bot === a) naArquibancada = pos(a).z > Z0 + 443 && pos(a).z < Z0 + 450 && pos(a).y >= 74.5
      }
    }, 2000)
    t.verificar(naArquibancada, 'quem cruza a chegada vai pra arquibancada dela, assistir', `${pos(a).floored()}`)
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

    console.log('\n== Os outros mapas ==')
    for (const [id, x, nome] of OUTROS) {
      await fixar(id)
      await t.console(`minecraft:clear ${a.username}`) // o cheque da corrida de antes: a inscrição pede o inventário vazio
      await t.console('corrida abrir')
      await t.capturar(a, () => a.chat('/corrida entrar'), 600)
      msgs = await t.capturar(a, () => t.console('corrida iniciar'), 1500)
      t.verificar(msgs.some(m => m.includes(`Mapa: ${nome}`)) && Math.abs(pos(a).x - x) < 7 && pos(a).z >= Z0 && pos(a).z < Z0 + 8,
        `o mapa ${nome} tem a largada no lugar dele (x ${x})`, `${pos(a).floored()} | ${msgs.find(m => m.includes('Mapa')) || ''}`)
      await t.console('corrida cancelar')
      await t.espera(1500)
      await t.console(`execute in minecraft:spawn run minecraft:tp ${a.username} 0.5 65 43.5`)
      await t.espera(500)
    }
  } finally {
    fs.writeFileSync(CONFIG, original)
    await t.console('corrida reload')
    await t.console('corrida cancelar')
    for (const bot of todos) {
      await t.console(`minecraft:clear ${bot.username}`)
      await t.console(`lp user ${bot.username} permission unset eventos.ip.ignorar`)
      await t.console(`lp user ${bot.username} permission unset eventos.admin`)
    }
  }
  await t.terminar(...todos)
})
