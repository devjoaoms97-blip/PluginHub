// Gladiador: modo individual (inscrição paga, preparação sem dano, desconexão elimina, último de
// pé leva o pote) e modo por clã (sem dano entre o mesmo clã, prêmio dividido entre quem lutou
// pelo clã vencedor). Precisa da arena (node montar-arenas.js). Uso: node teste-gladiador.js
const t = require('./lib')

const dinheiro = r => Number((r.match(/\$([\d,.]+)/) || [, 'NaN'])[1].replace(/,/g, ''))
const saldo = async nome => dinheiro(await t.console(`balance ${nome}`))
// a arena do Gladiador fica em x -20..20, z 80..120 do mundo das arenas
// lutar sobe o nível das skills, e cada nível paga um pouco (R$ 50, R$ 75...) por fora do prêmio
const recebeu = (depois, antes, premio) => depois - antes >= premio && depois - antes < premio + 600
const naArena = bot => Math.abs(bot.entity.position.x) <= 20 && bot.entity.position.z >= 80 && bot.entity.position.z <= 120

async function preparar(nomes, saldoInicial) {
  for (const n of nomes) {
    await t.console(`gamemode survival ${n}`)
    await t.console(`minecraft:clear ${n}`)
    await t.console(`eco set ${n} ${saldoInicial}`)
    await t.console(`minecraft:give ${n} diamond_sword`)
  }
  await t.espera(1000)
}

t.rodar(async () => {
  console.log('\n== Individual ==')
  const bots = []
  for (const p of ['Gla', 'Glb', 'Glc', 'Gld']) bots.push(await t.criarBot(t.nomeNovo(p)))
  const [g1, g2, g3, g4] = bots
  await preparar(bots.map(b => b.username), 10000)

  await t.console('gladiador abrir individual 1000')
  for (const b of bots) await t.capturar(b, () => b.chat('/gladiador entrar'), 500)
  t.verificar(await saldo(g1.username) === 9000, 'inscrição cobrada')
  let msgs = await t.capturar(g1, () => t.console('gladiador iniciar'), 2500)
  t.verificar(bots.every(naArena), 'os quatro foram pra arena', bots.map(b => b.entity.position.toString()).join(' '))
  t.verificar(msgs.some(m => m.includes('Pote: R$ 14.000,00')), 'pote = 4 inscrições + R$ 10.000 do servidor')

  t.verificar(!(await t.lutar(g1, g2, 6000)), 'na preparação, ninguém morre')
  t.verificar(g2.health === 20, 'nem toma dano', `vida ${g2.health}`)
  await t.espera(10000)

  msgs = await t.capturar(g1, async () => { g4.quit(); await t.espera(1500) })
  t.verificar(msgs.some(m => m.includes(g4.username) && m.includes('eliminado')), 'quem desconecta é eliminado')
  t.verificar(await t.lutar(g1, g2), `${g1.username} eliminou ${g2.username}`)
  msgs = await t.capturar(g1, () => t.lutar(g1, g3), 2000)
  t.verificar(msgs.some(m => m.includes('venceu')), 'o último de pé venceu', msgs.find(m => m.includes('venceu')))
  t.verificar(recebeu(await saldo(g1.username), 9000, 14000), 'e levou o pote', `${await saldo(g1.username)}`)
  t.verificar(await saldo(g2.username) === 9000, 'quem perdeu, perdeu a inscrição')
  await t.espera(3000)
  t.verificar(!naArena(g2) && !naArena(g3), 'os eliminados renasceram fora da arena')
  console.log('  esperando os 30 s do vencedor...')
  await t.espera(29000)
  t.verificar(!naArena(g1), 'o vencedor voltou')
  msgs = await t.capturar(g1, () => g1.chat('/gladiador top'))
  t.verificar(msgs.some(m => m.includes(g1.username)), 'ranking')
  for (const b of [g1, g2, g3]) b.quit()
  await t.espera(1000)

  console.log('\n== Por clã ==')
  const p1 = await t.criarBot(t.nomeNovo('Cla')), p2 = await t.criarBot(t.nomeNovo('Clb'))
  const p3 = await t.criarBot(t.nomeNovo('Clc')), semCla = await t.criarBot(t.nomeNovo('Sem'))
  await preparar([p1, p2, p3, semCla].map(b => b.username), 20000)
  const tagX = 'X' + Date.now().toString(36).slice(-4), tagY = 'Y' + Date.now().toString(36).slice(-4)
  await t.capturar(p1, () => p1.chat(`/cla criar ${tagX} Gladiadores X`))
  await t.capturar(p2, () => p1.chat(`/cla convidar ${p2.username}`))
  await t.capturar(p2, () => p2.chat(`/cla aceitar ${tagX}`))
  await t.capturar(p3, () => p3.chat(`/cla criar ${tagY} Gladiadores Y`))
  const saldos = { p1: await saldo(p1.username), p2: await saldo(p2.username) }

  await t.console('gladiador abrir cla 0')
  msgs = await t.capturar(semCla, () => semCla.chat('/gladiador entrar'))
  t.verificar(msgs.some(m => m.includes('não tem clã')), 'sem clã não entra no modo por clã')
  for (const b of [p1, p2, p3]) await t.capturar(b, () => b.chat('/gladiador entrar'), 500)
  await t.capturar(p1, () => t.console('gladiador iniciar'), 2500)
  t.verificar([p1, p2, p3].every(naArena), 'os três foram pra arena')
  await t.espera(14000)

  t.verificar(!(await t.lutar(p1, p2, 5000)) && p2.health === 20, 'sem dano entre o mesmo clã', `vida ${p2.health}`)
  msgs = await t.capturar(p1, () => t.lutar(p1, p3), 2000)
  t.verificar(msgs.some(m => m.includes(`[${tagX}]`) && m.includes('venceu')), 'o último clã de pé venceu', msgs.find(m => m.includes('venceu')))
  const [d1, d2] = [await saldo(p1.username), await saldo(p2.username)]
  t.verificar(recebeu(d1, saldos.p1, 5000) && recebeu(d2, saldos.p2, 5000), 'o prêmio de R$ 10.000 foi dividido entre os dois do clã',
    `+${d1 - saldos.p1} e +${d2 - saldos.p2}`)

  console.log('\n== Cancelamento devolve a inscrição ==')
  await t.espera(31000)
  await t.console('gladiador abrir individual 1000')
  const antes = await saldo(semCla.username)
  await t.capturar(semCla, () => semCla.chat('/gladiador entrar'))
  await t.console('gladiador cancelar')
  t.verificar(await saldo(semCla.username) === antes, 'cancelado, a inscrição voltou')

  await t.terminar(p1, p2, p3, semCla)
})
