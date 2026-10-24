// Gladiador: modo individual (inscrição paga, preparação sem dano, desconexão elimina, último de
// pé leva o pote, destaques com tag no chat) e modo por clã (sem dano entre o mesmo clã, prêmio dividido entre quem lutou
// pelo clã vencedor). Precisa da arena (node montar-mapa.js). Uso: node teste-gladiador.js
const t = require('./lib')

const dinheiro = r => Number((r.match(/\$([\d,.]+)/) || [, 'NaN'])[1].replace(/,/g, ''))
const saldo = async nome => dinheiro(await t.console(`balance ${nome}`))
// lutar sobe o nível das skills, e cada nível paga um pouco (R$ 50, R$ 75...) por fora do prêmio
const recebeu = (depois, antes, premio) => depois - antes >= premio && depois - antes < premio + 600
// o coliseu (EstruturasPlugin) tem o centro em (600, 0) no mundo arenas e piso de 153 x 129
const naArena = bot => Math.hypot(bot.entity.position.x - 600, bot.entity.position.z) <= 80
// Os pontos de entrada da arena ficam longe uns dos outros e com ruínas no meio, os bots não têm
// pathfinding, e o plugin (certo) não deixa teleportar participante. Então, durante o teste, a arena
// usa 4 pontos colados no centro (livre, por causa da borda final); os originais voltam no fim.
const PONTOS_DO_TESTE = [[598.5, 0.5, -90], [602.5, 0.5, 90], [600.5, -1.5, 0], [600.5, 2.5, 180]]

/**
 * Os 16 pontos de entrada oficiais do coliseu (os mesmos do montar-mapa.js), olhando pro centro:
 * [x, y, z, yaw]. Não lê o arena.yml: um teste interrompido no meio deixava lá os 4 do teste, e
 * o próximo "restaurava" pra eles.
 */
function pontosOriginais() {
  const relativos = [[38, 0], [19, 28], [0, 32], [-27, 23], [-38, 0], [-27, -23], [0, -32], [27, -23],
    [46, 22], [18, 42], [-14, 43], [-46, 22], [-46, -22], [-27, -39], [14, -43], [46, -22]]
  return relativos.map(([dx, dz]) => [600 + dx + 0.5, 65, dz + 0.5, Math.round(Math.atan2(dx, -dz) * 180 / Math.PI)])
}

/** Troca os pontos de entrada da arena (o bot da staff fica em cada um e roda o spawn add). */
async function definirPontos(staff, pontos) {
  await t.capturar(staff, () => staff.chat('/gladiador arena spawn limpar'), 500)
  for (const [x, y, z, yaw] of pontos) {
    await t.console(`execute in minecraft:arenas run minecraft:tp ${staff.username} ${x} ${y} ${z} ${yaw} 0`)
    await t.espera(700)
    await t.capturar(staff, () => staff.chat('/gladiador arena spawn add'), 400)
  }
}

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
  const originais = pontosOriginais()
  const staff = await t.criarBot('BotStaff')
  await t.console('op BotStaff')
  await definirPontos(staff, PONTOS_DO_TESTE.map(([x, z, yaw]) => [x, 65, z, yaw]))
  try {
    await testar()
  } finally {
    await definirPontos(staff, originais)
    const info = await t.console('gladiador arena info')
    console.log(`  pontos originais de volta: ${info}`)
    await t.console('deop BotStaff')
  }
  await t.terminar(staff)
})

async function testar() {
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
  // destaques: o mesmo bot eliminou os outros dois, então é o Ceifador (2 abates)
  const destaques = msgs.join('\n')
  t.verificar(destaques.includes('Destaques do Gladiador'), 'anúncio dos destaques')
  t.verificar(new RegExp(`\\[Ceifador\\] ${g1.username} \\(2 abate`).test(destaques), `${g1.username} é o Ceifador, com 2 abates`,
    msgs.find(m => m.includes('[Ceifador]')))
  t.verificar(destaques.includes('[Fujão]') && destaques.includes('[Top Damage]'), 'e tem Fujão e Top Damage',
    [msgs.find(m => m.includes('[Fujão]')), msgs.find(m => m.includes('[Top Damage]'))].join(' / '))
  t.verificar(recebeu(await saldo(g1.username), 9000, 14000), 'e levou o pote', `${await saldo(g1.username)}`)
  t.verificar(await saldo(g2.username) === 9000, 'quem perdeu, perdeu a inscrição')
  await t.espera(3000)
  t.verificar(!naArena(g2) && !naArena(g3), 'os eliminados renasceram fora da arena')
  console.log('  esperando os 30 s do vencedor...')
  await t.espera(29000)
  t.verificar(!naArena(g1), 'o vencedor voltou')
  msgs = await t.capturar(g1, () => g1.chat('/gladiador top'))
  t.verificar(msgs.some(m => m.includes(g1.username)), 'ranking')
  t.verificar(msgs.some(m => m.includes('[Ceifador]') && m.includes(g1.username)), 'o /gladiador top mostra quem tem cada tag')
  // o bot recebe só o corpo das mensagens de chat: o formato com a tag sai no log do servidor
  g1.chat('oi do ceifador')
  await t.espera(1500)
  const LOG = `${process.env.MC_TESTE || 'C:/mc-teste'}/ultimo-boot.txt`
  const linha = require('fs').readFileSync(LOG, 'latin1').split('\n').reverse().find(l => l.includes('oi do ceifador')) || ''
  t.verificar(linha.includes('[Ceifador]'), 'a tag aparece no chat', linha.replace(/\x1b\[[0-9;]*m/g, '').slice(-70))
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

  // sem t.terminar aqui: ele encerra o processo e o finally (que devolve os pontos da arena) não rodaria
  for (const b of [p1, p2, p3, semCla]) b.quit()
}
