// Evento Corre ou Cai (EventosPlugin): os 3 no piso de cima e sem kit; na graça do começo o chão não
// some; depois, o bloco debaixo de quem está parado fica vermelho e some, e ele cai pro piso de baixo sem
// sair; do último, cai na água e vai pra galeria; o último de pé leva o cheque e volta; a arena volta
// inteira; e o /eventos mostra o Corre ou Cai na agenda.
// Uso: node teste-correoucai.js
const t = require('./lib')
const { Vec3 } = require('vec3')

const AGUA = 64
const PISOS = [AGUA + 6, AGUA + 12, AGUA + 18] // 70, 76, 82
const CENTRO = [0, 1200]
const LADO = 12

const quantos = async (nome, item) => Number(((await t.console(`minecraft:clear ${nome} ${item} 0`)).match(/Found (\d+)/) || [, 0])[1])
const andar = b => PISOS.findLastIndex(y => b.entity.position.y >= y + 0.5)
const naGaleria = b => b.entity.position.z < CENTRO[1] - LADO - 1 && b.entity.position.y > PISOS[2] + 4

/** Blocos do piso de cima, como o bot D enxerga: [coloridos, vermelhos]. */
function pisoDeCima(bot) {
  let cor = 0, vermelho = 0
  for (let x = CENTRO[0] - LADO; x <= CENTRO[0] + LADO; x++)
    for (let z = CENTRO[1] - LADO; z <= CENTRO[1] + LADO; z++) {
      const n = bot.blockAt(new Vec3(x, PISOS[2], z))?.name
      if (n === 'magenta_concrete') cor++
      if (n === 'red_concrete') vermelho++
    }
  return [cor, vermelho]
}

t.rodar(async () => {
  for (const e of ['vietna', 'spleef', 'correoucai']) await t.console(`${e} cancelar`)
  const a = await t.criarBot(t.nomeNovo('Coa'))
  const b = await t.criarBot(t.nomeNovo('Cob'))
  const c = await t.criarBot(t.nomeNovo('Coc'))
  const d = await t.criarBot(t.nomeNovo('Cod'))
  const todos = [a, b, c, d]
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
    t.verificar(['11:00', '17:00'].every(h => msgs.some(m => m.includes('Corre ou Cai') && m.includes(h))),
      '/eventos mostra o Corre ou Cai às 11h e 17h', msgs.find(m => m.includes('Corre')))
    await t.console('correoucai abrir')
    for (const bot of [a, b, c]) await t.capturar(bot, () => bot.chat('/correoucai entrar'), 600)
    msgs = await t.capturar(d, () => d.chat('/correoucai'))
    t.verificar(msgs.some(m => m.includes('3 inscrito')), '/correoucai mostra os 3 inscritos', msgs.find(m => m.includes('Agora')))

    console.log('\n== Piso de cima ==')
    await t.console('correoucai iniciar')
    await t.capturar(d, () => d.chat('/correoucai assistir'), 2000)
    t.verificar([a, b, c].every(x => andar(x) === 2), 'os 3 no piso de cima', [a, b, c].map(x => x.entity.position.floored()).join(' | '))
    t.verificar(naGaleria(d), '/correoucai assistir leva pra galeria', `${d.entity.position.floored()}`)
    await t.espera(9000 + 1000) // preparação e o começo da graça (3 s)
    t.verificar(a.inventory.items().length === 0, 'na luta, sem kit nenhum', a.inventory.items().map(i => i.name).join(','))
    t.verificar([a, b, c].every(x => andar(x) === 2) && pisoDeCima(d)[1] === 0, 'na graça do começo, o chão não some',
      `andares ${[a, b, c].map(andar)}, ${pisoDeCima(d)[1]} vermelhos`)

    console.log('\n== O chão some ==')
    // depois da graça: parados, eles caem andar por andar até a água
    let viuVermelho = 0, menor = { [a.username]: 2, [b.username]: 2, [c.username]: 2 }, passouPeloMeio = false
    const fim = await t.capturar(a, async () => {
      for (let i = 0; i < 60; i++) {
        viuVermelho = Math.max(viuVermelho, pisoDeCima(d)[1])
        for (const x of [a, b, c]) {
          const n = andar(x)
          if (n >= 0 && n < menor[x.username]) menor[x.username] = n
          if (n === 1) passouPeloMeio = true
        }
        await t.espera(200)
      }
    }, 2000)
    t.verificar(viuVermelho > 0, 'o bloco debaixo de quem está parado fica vermelho', `${viuVermelho} vermelhos`)
    t.verificar(passouPeloMeio, 'e some: eles caem pro piso do meio sem sair do evento', JSON.stringify(menor))
    const eliminados = fim.filter(m => m.includes('caiu na água')).length
    const venceu = fim.find(m => m.includes('venceu'))
    t.verificar(eliminados === 2 && !!venceu, 'do último piso, caem na água; o último de pé vence', `${eliminados} na água | ${venceu}`)
    const vencedor = [a, b, c].find(x => venceu && venceu.includes(x.username))
    await t.espera(1500)
    if (vencedor) {
      t.verificar(await quantos(vencedor.username, 'paper') === 1, 'o vencedor ganha o cheque')
      t.verificar(vencedor.entity.position.distanceTo(new Vec3(0.5, 65, 43.5)) < 3, 'e volta pra onde estava', `${vencedor.entity.position.floored()}`)
    }
    const perdedor = [a, b, c].find(x => x !== vencedor)
    t.verificar(perdedor && naGaleria(perdedor), 'quem caiu na água está na galeria', perdedor && `${perdedor.entity.position.floored()}`)
    await t.espera(2500)
    const [cor, vermelho] = pisoDeCima(d)
    t.verificar(cor === (2 * LADO + 1) ** 2 && vermelho === 0, 'a arena volta inteira depois', `${cor} magenta, ${vermelho} vermelhos`)
  } finally {
    await t.console('correoucai cancelar')
    for (const bot of todos) {
      await t.console(`minecraft:clear ${bot.username}`)
      await t.console(`lp user ${bot.username} permission unset eventos.ip.ignorar`)
    }
  }
  await t.terminar(...todos)
})
