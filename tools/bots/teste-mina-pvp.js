// Mina PvP (2026-11-17): a /warp minapvp chega no saguão seguro; do arco pra dentro tem PvP; qualquer
// rank (o bot é Camponês) minera e vende na hora; a hora de bônus (forçada pela staff) paga o dobro só
// nela; quem morre no campo deixa os itens no chão; o portão norte da avenida e o Banco no fundo da Loja.
// Desde 2026-11-18: o arco é a borda de um pulo de 4 blocos; do campo não se volta a pé, e o /spawn só sai
// fora de combate.
// Precisa do mapa (montar-mapa.js construir minas loja marcar) e da mina cadastrada (cadastrar --mina-pvp).
// Uso: node teste-mina-pvp.js
const { Vec3 } = require('vec3')
const t = require('./lib')

const W = 'execute in minecraft:spawn run'
const reais = m => Number(((m || '').match(/\+R\$ ([\d.]+,\d\d)/) || [, 'NaN'])[1].replace(/\./g, '').replace(',', '.'))

async function bloco(x, y, z, nome) {
  const r = await t.console(`${W} execute if block ${x} ${y} ${z} minecraft:${nome}`)
  return /passed/i.test(r)
}

/** Vida que o alvo perde com um golpe do atacante. */
async function golpe(a, b) {
  await t.console(`effect give ${b.username} minecraft:instant_health 1 10 true`)
  await t.espera(800)
  const antes = b.health
  const e = Object.values(a.entities).find(x => x.username === b.username)
  await a.lookAt(e.position.offset(0, 1.6, 0), true)
  a.attack(e)
  await t.espera(700)
  return antes - b.health
}

/** Põe um minério de ferro no topo do poço, o bot quebra e devolve o "+R$" da barra. */
async function venderUmFerro(bot) {
  await t.console(`${W} setblock 0 64 -205 minecraft:iron_ore`)
  await t.espera(500)
  const msgs = await t.capturar(bot, async () => {
    await bot.lookAt(new Vec3(0.5, 64.5, -204.5), true)
    await bot.dig(bot.blockAt(new Vec3(0, 64, -205)), true)
  }, 1200)
  // só a barra da venda (a recompensa de missão também diz "+R$", mas no chat)
  return reais(msgs.find(m => m.startsWith('[barra]') && m.includes('+R$')))
}

/** Corre e pula na direção do ponto por um tempo. */
async function andar(bot, alvo, ms) {
  await bot.lookAt(alvo, true)
  bot.setControlState('forward', true)
  bot.setControlState('sprint', true)
  bot.setControlState('jump', true)
  await t.espera(ms)
  bot.clearControlStates()
  await t.espera(800)
}

t.rodar(async () => {
  const a = await t.criarBot(t.nomeNovo('Mpa')), b = await t.criarBot(t.nomeNovo('Mpb'))
  for (const bot of [a, b]) {
    await t.console(`gamemode survival ${bot.username}`)
    await t.console(`minecraft:clear ${bot.username}`)
    await t.console(`vip remove ${bot.username}`)
  }

  console.log('\n== Chegada pelo /warp minapvp ==')
  a.chat('/warp minapvp')
  await t.espera(5000) // 3 s de espera do Essentials pra quem não é VIP
  const p = a.entity.position
  t.verificar(p.z <= -186 && p.z >= -196 && Math.abs(p.x) < 6, 'a warp deixa no saguão, antes do arco', `${p.x.toFixed(1)} ${p.y.toFixed(1)} ${p.z.toFixed(1)}`)
  const bonus = await t.capturar(a, () => a.chat('/mina bonus'), 1000)
  t.verificar(bonus.some(m => /Hora de bônus/.test(m) && /(hoje|amanhã|agora)/.test(m)), 'o /mina bonus diz quando é a hora de venda em dobro', bonus.find(m => /bônus/.test(m)))

  // com o bot no saguão, os chunks do fundo da avenida estão carregados
  console.log('\n== Mapa ==')
  t.verificar(await bloco(0, 65, -184, 'air') && await bloco(0, 64, -184, 'polished_andesite'),
    'o fundo da avenida tem o portão aberto (o Banco saiu dali)')
  t.verificar(await bloco(10, 64, -224, 'air') === false && await bloco(10, 50, -224, 'air') === false, 'o poço da mina PvP está cheio')

  console.log('\n== PvP: o saguão é seguro, o campo não ==')
  await t.console(`minecraft:give ${a.username} diamond_sword`)
  await t.espera(500)
  await a.equip(a.inventory.items().find(i => i.name === 'diamond_sword'), 'hand')
  await t.console(`${W} minecraft:tp ${a.username} 0.5 65 -190.5 -90 0`)
  await t.console(`${W} minecraft:tp ${b.username} 2.5 65 -190.5 90 0`)
  await t.espera(5000) // o Essentials deixa invulnerável 4 s depois do teleporte
  const noSaguao = await golpe(a, b)
  t.verificar(noSaguao < 0.5, 'no saguão, o golpe não tira vida', noSaguao.toFixed(1))
  await t.console(`${W} minecraft:tp ${a.username} 0.5 65 -200.5 -90 0`)
  await t.console(`${W} minecraft:tp ${b.username} 2.5 65 -200.5 90 0`)
  await t.espera(5000)
  const noCampo = await golpe(a, b)
  t.verificar(noCampo > 2, 'do arco pra dentro, o golpe tira vida', noCampo.toFixed(1))

  console.log('\n== Quem morre no campo deixa os itens ==')
  await t.console(`minecraft:give ${b.username} diamond 5`)
  await t.espera(500)
  await t.console(`minecraft:kill ${b.username}`)
  await t.espera(1500)
  const chao = await t.console(`${W} execute if entity @e[type=item,x=2.5,y=65,z=-200.5,distance=..4,nbt={Item:{id:"minecraft:diamond"}}]`)
  t.verificar(/passed/i.test(chao), 'os diamantes de quem morreu ficaram no chão', chao)
  await t.console(`${W} minecraft:kill @e[type=item,x=2.5,y=65,z=-200.5,distance=..6]`)

  console.log('\n== Mina: qualquer rank, venda na hora e a hora de bônus ==')
  await t.console(`minecraft:clear ${a.username}`)
  await t.console(`minecraft:give ${a.username} iron_pickaxe`)
  await t.espera(5500) // o "em combate" da luta acima barra o /tp por 5 s
  await t.console('mina admin bonus 0')
  await t.console(`${W} minecraft:tp ${a.username} 0.5 65 -202.5 180 40`)
  await t.espera(1500)
  await a.equip(a.inventory.items().find(i => i.name === 'iron_pickaxe'), 'hand')
  const normal = await venderUmFerro(a)
  t.verificar(normal > 0, 'o Camponês minera na mina PvP e o ferro é vendido na hora', `R$ ${normal}`)
  await t.console('mina admin bonus 5')
  await t.espera(1500)
  const comBonus = await venderUmFerro(a)
  t.verificar(Math.abs(comBonus / normal - 2) < 0.05, 'na hora de bônus, a mesma venda paga o dobro', `R$ ${normal} → R$ ${comBonus}`)
  // o texto inteiro sai cortado com "...": lê pedaço por pedaço
  let holo = ''
  for (let i = 0; i < 12; i++) {
    const r = await t.console(`${W} data get entity @e[type=text_display,limit=1,sort=nearest,x=0.5,y=67,z=-192.5] text.extra[${i}].text`)
    if (!/has the following/.test(r)) break
    holo += r.split('entity data: ').pop()
  }
  t.verificar(/VENDA x2 AGORA/.test(holo), 'o holograma da mina mostra a venda em dobro', holo.slice(-120))
  await t.console('mina admin bonus 0')
  await t.espera(1500)
  const desligado = await venderUmFerro(a)
  t.verificar(Math.abs(desligado / normal - 1) < 0.05, 'desligada a hora de bônus, volta ao preço normal', `R$ ${desligado}`)

  console.log('\n== O degrau: do saguão se pula no campo, do campo não se volta a pé ==')
  t.verificar(await bloco(0, 68, -197, 'polished_diorite') && await bloco(0, 68, -198, 'polished_diorite') && await bloco(0, 65, -199, 'air'),
    'o piso do arco é a borda de um pulo de 4 blocos')
  await t.console(`minecraft:clear ${a.username}`)
  await t.console(`effect clear ${a.username}`)
  await t.console(`${W} minecraft:tp ${a.username} 0.5 65 -189.5 180 0`)
  await t.espera(1500)
  await andar(a, new Vec3(0.5, 66, -215), 5000)
  let pa = a.entity.position
  t.verificar(pa.z < -199 && pa.y < 66, 'subindo a escada do saguão, o jogador pula no campo', `${pa.x.toFixed(1)} ${pa.y.toFixed(1)} ${pa.z.toFixed(1)}`)
  await t.console(`${W} minecraft:tp ${a.username} 0.5 65 -201.5 180 0`)
  await t.espera(1000)
  await andar(a, new Vec3(0.5, 70, -185), 6000)
  pa = a.entity.position
  t.verificar(pa.z < -198.6 && pa.y < 66, 'do campo, correndo e pulando contra a parede, não se volta pro saguão', `${pa.x.toFixed(1)} ${pa.y.toFixed(1)} ${pa.z.toFixed(1)}`)

  console.log('\n== Do campo só se sai por comando, e fora de combate ==')
  await t.console(`${W} minecraft:tp ${a.username} 0.5 65 -201.5 -90 0`)
  await t.console(`${W} minecraft:tp ${b.username} 2.5 65 -201.5 90 0`)
  await t.espera(5000) // invulnerável 4 s depois do teleporte
  const levou = await golpe(b, a)
  const preso = await t.capturar(a, () => a.chat('/spawn'), 4000)
  pa = a.entity.position
  t.verificar(levou > 0 && pa.z < -198 && preso.some(m => /combate/i.test(m)), 'em combate, o /spawn não tira do campo', `${levou.toFixed(1)} | ${preso.join(' | ')}`)
  await t.espera(5500)
  a.chat('/spawn')
  await t.espera(5000) // 3 s de espera do Essentials
  pa = a.entity.position
  t.verificar(pa.z > -100, 'fora de combate, o /spawn tira do campo', `${pa.x.toFixed(1)} ${pa.y.toFixed(1)} ${pa.z.toFixed(1)}`)

  await t.console(`minecraft:clear ${a.username}`)
  await t.terminar(a, b)
})
