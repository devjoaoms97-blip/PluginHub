// Regras de 2026-10-28: login (AuthMe), espera de 3 s no teleporte pra quem não é VIP (anda à vontade,
// VIP passa direto), 5 s sem teleporte depois de bater ou apanhar de jogador, deslogar em combate mata,
// PvP da arena do boss só depois do pulo do túnel (com o holograma de aviso), boss preso na arena,
// ninguém renasce com efeito e a limpeza leva os itens mesmo com gente perto. Uso: node teste-regras-combate.js
const t = require('./lib')

const ARENA = [0.5, 33, 180.5]       // dentro da arena do boss (PvP sempre)
const TUNEL = [0.5, 37, 143.5]       // túnel da muralha, antes do pulo (sem PvP)
const pos = b => b.entity.position
const longe = (b, de) => pos(b).distanceTo(de) > 20
const vida = async nome => parseFloat((await t.console(`data get entity ${nome} Health`)).split(':').pop())

async function tp(nome, [x, y, z]) {
  await t.console(`execute in minecraft:spawn run minecraft:tp ${nome} ${x} ${y} ${z}`)
  await t.espera(1200)
}

/** a bate uma vez em b (de perto) e devolve quanto de vida b perdeu. */
async function golpe(a, b, onde) {
  await tp(a.username, onde)
  await tp(b.username, [onde[0] + 1.5, onde[1], onde[2]])
  await t.console(`minecraft:effect give ${b.username} minecraft:instant_health 1 5 true`)
  await t.espera(4500) // o Essentials deixa invulnerável 4 s depois do teleporte
  const antes = await vida(b.username)
  const alvo = Object.values(a.entities).find(e => e.type === 'player' && e.username === b.username)
  if (alvo) { await a.lookAt(alvo.position.offset(0, 1.6, 0), true); a.attack(alvo) }
  await t.espera(1000)
  const perdeu = antes - (await vida(b.username))
  console.log(`  golpe: ${alvo ? 'acertou' : 'B não visto'}, perdeu ${perdeu}`)
  return perdeu
}

t.rodar(async () => {
  const a = await t.criarBot(t.nomeNovo('Rga')), b = await t.criarBot(t.nomeNovo('Rgb'))
  for (const n of [a.username, b.username]) {
    await t.console(`gamemode survival ${n}`)
    await t.console(`minecraft:give ${n} iron_sword`)
  }

  console.log('\n== Login (AuthMe) ==')
  const contas = await t.console(`authme getip ${a.username}`)
  t.verificar(!/não|not|nenhum/i.test(contas) || contas.includes('127.0.0.1'), 'o bot registrou e logou', contas)

  console.log('\n== Teleporte: espera de 3 s pra quem não é VIP ==')
  await tp(a.username, ARENA)
  let inicio = pos(a).clone()
  let msgs = await t.capturar(a, async () => {
    a.chat('/spawn')
    await t.espera(1500)
    t.verificar(!longe(a, inicio), 'com 1,5 s ainda não teleportou')
    a.setControlState('forward', true) // andar não cancela
    await t.espera(800)
    a.setControlState('forward', false)
    await t.espera(2200)
  }, 0)
  t.verificar(longe(a, inicio), 'depois dos 3 s teleportou, mesmo andando')
  const contagem = ['Teleporte em 3 segundos.', 'Teleporte em 2 segundos.', 'Teleporte em 1 segundo.', 'Teleportando...']
  t.verificar(contagem.every(c => msgs.some(m => m.includes(c))), 'contagem 3, 2, 1 e "Teleportando..."',
    msgs.filter(m => m.includes('Telep')).join(' | '))
  t.verificar(msgs.filter(m => m.includes('Teleportando')).length === 1, 'um "Teleportando..." só')

  // dano de queda: o Essentials sozinho só cancelava no ataque de uma entidade (queda, fogo e lava passavam)
  await tp(a.username, ARENA)
  await t.espera(4500) // passa a invulnerabilidade do teleporte
  inicio = pos(a).clone()
  msgs = await t.capturar(a, async () => {
    a.chat('/spawn')
    await t.espera(1000)
    await t.console(`minecraft:damage ${a.username} 2 minecraft:fall`)
    await t.espera(3000)
  }, 0)
  t.verificar(!longe(a, inicio) && msgs.some(m => m.includes('Teleporte cancelado')), 'dano de queda na espera cancela o teleporte',
    msgs.filter(m => m.includes('Telep')).join(' | '))

  await tp(a.username, ARENA)
  await t.espera(4500) // passa a invulnerabilidade do teleporte, senão o dano não pega
  inicio = pos(a).clone()
  a.chat('/spawn')
  await t.espera(1000)
  await t.console(`minecraft:damage ${a.username} 1 minecraft:generic`)
  await t.espera(3000)
  t.verificar(!longe(a, inicio), 'tomar dano na espera cancela o teleporte')

  await t.console(`lp user ${a.username} parent add vip_bronze`)
  await tp(a.username, ARENA)
  inicio = pos(a).clone()
  a.chat('/spawn')
  await t.espera(1200)
  t.verificar(longe(a, inicio), 'VIP teleporta na hora')
  await t.console(`lp user ${a.username} parent remove vip_bronze`)

  console.log('\n== PvP da arena do boss: só depois do pulo ==')
  t.verificar((await golpe(a, b, TUNEL)) === 0, 'no túnel (antes do pulo) não tem PvP')
  t.verificar((await golpe(a, b, ARENA)) > 0, 'dentro da arena tem PvP')
  await t.console('minecraft:execute in minecraft:spawn run forceload add 0 143')
  await t.espera(2500)
  const holo = await t.console('minecraft:execute in minecraft:spawn if entity @e[type=text_display,x=0.5,y=38.3,z=146.5,distance=..1.5]')
  t.verificar(/passed|sucesso|1/i.test(holo), 'o holograma de aviso está na borda do pulo', holo)
  await t.console('minecraft:execute in minecraft:spawn run forceload remove 0 143')

  console.log('\n== 5 s sem teleporte depois de apanhar ==')
  await golpe(a, b, ARENA)
  msgs = await t.capturar(b, () => b.chat('/spawn'), 1000)
  t.verificar(msgs.some(m => m.includes('em combate')), 'em combate o /spawn é barrado', msgs.find(m => m.includes('combate')))
  msgs = await t.capturar(a, () => a.chat('/warp loja'), 1000)
  t.verificar(msgs.some(m => m.includes('em combate')), 'quem bateu também fica em combate')
  await t.espera(5000)
  inicio = pos(b).clone()
  msgs = await t.capturar(b, () => b.chat('/spawn'), 6000)
  t.verificar(!msgs.some(m => m.includes('em combate')) && longe(b, inicio), 'depois dos 5 s o teleporte volta')

  console.log('\n== Deslogar em combate mata ==')
  const c = await t.criarBot(t.nomeNovo('Rgc'))
  await t.console(`gamemode survival ${c.username}`)
  await t.console(`minecraft:give ${c.username} diamond 3`)
  await golpe(a, c, ARENA)
  msgs = await t.capturar(a, async () => { c.quit(); await t.espera(1500) })
  t.verificar(msgs.some(m => m.includes(c.username) && m.includes('deslogou em combate')), 'aviso no chat de quem deslogou em combate',
    msgs.find(m => m.includes('deslogou')))
  const chao = await t.console(`minecraft:execute in minecraft:spawn if entity @e[type=item,x=${ARENA[0]},y=${ARENA[1]},z=${ARENA[2]},distance=..6]`)
  t.verificar(/passed|sucesso|[1-9]/i.test(chao), 'e os itens dele ficaram no chão', chao)

  console.log('\n== Ninguém renasce com efeito ==')
  // o efeito dado com o jogador já morto (na tela de morte) atravessava o renascimento
  b.once('death', async () => { await t.console(`minecraft:effect give ${b.username} minecraft:wither 60 1`) })
  await t.console(`minecraft:kill ${b.username}`)
  await t.espera(4000)
  const efeitos = await t.console(`minecraft:data get entity ${b.username} active_effects`)
  t.verificar(!/wither/i.test(efeitos), 'renasceu sem o Wither', efeitos.slice(0, 80))

  console.log('\n== Boss preso na arena ==')
  await t.console('boss stop')
  await t.console('boss start wither')
  await t.espera(1500)
  // o caminho por onde ele fugiu: o túnel da entrada, 54 blocos do centro (fora do raio de 46)
  await t.console('minecraft:execute in minecraft:spawn as @e[type=wither,limit=1] run minecraft:tp @s 0.5 39 141.5')
  await t.espera(2000)
  const voltou = await t.console('minecraft:execute in minecraft:spawn if entity @e[type=wither,x=0.5,y=33,z=195.5,distance=..46]')
  t.verificar(/passed|sucesso|1/i.test(voltou), 'o Wither levado pro túnel voltou pra dentro da arena', voltou)
  await t.console('boss stop')

  console.log('\n== A limpeza leva o item mesmo com gente perto ==')
  await tp(a.username, ARENA)
  await t.console(`minecraft:execute in minecraft:spawn run minecraft:summon item ${ARENA[0] + 1} ${ARENA[1]} ${ARENA[2]} {Item:{id:"minecraft:diamond",count:1}}`)
  await t.console('limparchao agora')
  await t.espera(1500)
  const sobrou = await t.console(`minecraft:execute in minecraft:spawn if entity @e[type=item,x=${ARENA[0]},y=${ARENA[1]},z=${ARENA[2]},distance=..6]`)
  t.verificar(/failed|falhou|^0|não/i.test(sobrou) || !/passed/i.test(sobrou), 'o item do lado do jogador sumiu', sobrou)

  await t.terminar(a, b)
})
