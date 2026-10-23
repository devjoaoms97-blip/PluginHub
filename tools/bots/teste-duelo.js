// X1: desafio com aposta, aceite, contagem sem dano, proteção contra quem é de fora, morte com
// perda de itens e prêmio menos a taxa, volta pra onde estava, ranking, recusa e cancelamento pela
// staff com devolução. Precisa das arenas (node montar-arenas.js). Uso: node teste-duelo.js
const t = require('./lib')

const dinheiro = r => Number((r.match(/\$([\d,.]+)/) || [, 'NaN'])[1].replace(/,/g, ''))
const saldo = async nome => dinheiro(await t.console(`balance ${nome}`))
const vida = async nome => Number(((await t.console(`minecraft:data get entity ${nome} Health`)).match(/([\d.]+)f/) || [])[1])
const mundo = bot => bot.game.dimension
// as arenas de X1 ficam perto de (100, 65, 0..40) no mundo das arenas
const foraDaArena = bot => Math.abs(bot.entity.position.z - 20) > 60 || Math.abs(bot.entity.position.x - 100) > 30

t.rodar(async () => {
  const nomeA = t.nomeNovo('Due'), nomeB = t.nomeNovo('Lut')
  const a = await t.criarBot(nomeA)
  const b = await t.criarBot(nomeB)
  const fora = await t.criarBot('BotStaff')
  for (const n of [nomeA, nomeB]) {
    await t.console(`gamemode survival ${n}`)
    await t.console(`minecraft:clear ${n}`)
    await t.console(`eco set ${n} 10000`)
  }
  await t.console(`minecraft:give ${nomeB} diamond 5`)
  await t.console(`minecraft:give ${nomeA} diamond_sword`)
  await t.espera(1000)
  const posAntes = b.entity.position.clone()

  console.log('\n== Desafio e aceite ==')
  let msgs = await t.capturar(b, () => a.chat(`/duelo ${nomeB} 1000`))
  t.verificar(msgs.some(m => m.includes('desafiou você')), 'o desafiado recebeu o desafio', msgs[0])
  await t.capturar(b, () => b.chat('/duelo aceitar'), 1000)
  for (let i = 0; i < 20 && (foraDaArena(a) || foraDaArena(b)); i++) await t.espera(250)
  t.verificar(await saldo(nomeA) === 9000 && await saldo(nomeB) === 9000, 'as duas apostas ficaram retidas')
  t.verificar(!foraDaArena(a) && !foraDaArena(b), 'os dois foram pra arena', `${a.entity.position}`)

  console.log('\n== Contagem: sem dano ==')
  await t.console(`minecraft:damage ${nomeB} 6 minecraft:player_attack by ${nomeA}`)
  t.verificar(await vida(nomeB) === 20, 'na contagem, ninguém toma dano')
  await t.espera(5000)

  console.log('\n== Luta ==')
  await t.console(`minecraft:damage ${nomeB} 6 minecraft:player_attack by BotStaff`)
  t.verificar(await vida(nomeB) === 20, 'quem é de fora não bate em quem está duelando')
  msgs = await t.capturar(a, () => a.chat('/spawn'))
  t.verificar(msgs.some(m => m.includes('só o /duelo')), 'comandos bloqueados durante o duelo')
  let golpeou = false
  b.once('health', () => { golpeou = b.health < 20 })
  msgs = await t.capturar(a, () => t.lutar(a, b), 2500)
  t.verificar(golpeou, 'entre os dois, vale dano')
  t.verificar(msgs.some(m => m.includes('venceu')), 'o vencedor foi anunciado', msgs.find(m => m.includes('venceu')))
  t.verificar(await saldo(nomeA) === 10900, 'o vencedor levou o pote menos 5% (R$ 1.900)', `${await saldo(nomeA)}`)
  t.verificar(await saldo(nomeB) === 9000, 'o perdedor perdeu a aposta')
  const chao = t.itensNoChao(a, 30)
  // o vencedor, colado no outro, às vezes já pega na hora
  t.verificar(chao.some(i => i.startsWith('diamond')) || t.inventario(a).diamond === 5, 'os itens do perdedor caíram na arena',
    `chão ${JSON.stringify(chao)}, vencedor ${t.inventario(a).diamond || 0} diamante(s)`)

  await t.espera(3000)
  t.verificar(b.entity.position.distanceTo(posAntes) < 3, 'o perdedor renasceu onde estava', `${b.entity.position}`)
  t.verificar(t.inventario(b).diamond === undefined, 'e sem os itens')
  console.log('  esperando os 20 s do vencedor pegar os itens...')
  await t.espera(19000)
  t.verificar(foraDaArena(a), 'o vencedor voltou pra onde estava', `${a.entity.position}`)
  msgs = await t.capturar(a, () => a.chat('/duelo stats'))
  t.verificar(msgs.some(m => m.includes('1 vitória')), 'ranking', msgs[0])

  console.log('\n== Recusa e cancelamento ==')
  await t.capturar(b, () => a.chat(`/duelo ${nomeB} 500`))
  msgs = await t.capturar(a, () => b.chat('/duelo recusar'))
  t.verificar(msgs.some(m => m.includes('recusou')), 'recusou')
  await t.capturar(b, () => a.chat(`/duelo ${nomeB} 500`))
  await t.capturar(b, () => b.chat('/duelo aceitar'), 2000)
  const [sa, sb] = [await saldo(nomeA), await saldo(nomeB)]
  await t.console(`duelo cancelar ${nomeA}`)
  await t.espera(1500)
  t.verificar(await saldo(nomeA) === sa + 500 && await saldo(nomeB) === sb + 500, 'cancelado pela staff, as apostas voltaram')
  t.verificar(foraDaArena(a) && foraDaArena(b), 'e os dois saíram da arena')

  await t.terminar(a, b, fora)
})
