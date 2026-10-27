// Montaria do VIP no spawn (MontariaPlugin) e PvP só na arena do boss (AreasPlugin).
// Montaria: sem a permissão do VIP não sai; com ela, o bot monta num cavalo 1,6x a corrida, que some
// ao descer, e não é chamada na arena do boss. PvP: no spawn um bot não machuca o outro; na arena do
// boss machuca.
// Uso: node teste-montaria.js   (o mundo spawn com PvP ligado: mv modify spawn set pvp true)
const t = require('./lib')

const vida = async nome => parseFloat((await t.console(`data get entity ${nome} Health`)).split(':').pop())
const cavalos = async () => Number(((await t.console('execute in minecraft:spawn if entity @e[type=horse,tag=!x,x=-50,y=40,z=-10,dx=100,dy=60,dz=100]')).match(/Count: (\d+)/) || [, 0])[1])

t.rodar(async () => {
  const bot = await t.criarBot(t.nomeNovo('Mnt'))
  const nome = bot.username
  await t.console(`execute in minecraft:spawn run minecraft:tp ${nome} 0.5 65 20.5`) // avenida sul do spawn
  await t.espera(1500)

  console.log('\n== Montaria ==')
  let msgs = await t.capturar(bot, () => bot.chat('/montaria'))
  t.verificar(msgs.some(m => /benefício do VIP/.test(m)) && !bot.vehicle, 'sem VIP, a montaria não sai', msgs[0])

  await t.console(`lp user ${nome} permission set vip.perk.montaria true`)
  await t.espera(1000)
  msgs = await t.capturar(bot, () => bot.chat('/montaria'), 2000)
  t.verificar(!!bot.vehicle && bot.vehicle.name === 'horse', 'com VIP, o bot monta no cavalo', bot.vehicle?.name || msgs.join(' | '))
  const vel = await t.console(`execute as ${nome} on vehicle run attribute @s minecraft:movement_speed base get`)
  const valor = Number((vel.match(/(-?\d+(\.\d+)?)\s*$/) || [, 0])[1])
  t.verificar(Math.abs(valor - 5.6 * 1.6 / 43.17) < 0.005, 'na velocidade de 1,6x a corrida', `${valor.toFixed(4)} | ${vel}`)

  // descer (pelo console: o dismount do Mineflayer não passa pelo ViaVersion): a montaria some
  await t.console(`ride ${nome} dismount`)
  await t.espera(1500)
  const resto = await t.console(`execute in minecraft:spawn positioned 0.5 65 20.5 if entity @e[type=horse,distance=..6]`)
  // o bot.vehicle do Mineflayer não atualiza pelo ViaVersion: quem diz se o cavalo sumiu é o servidor
  t.verificar(!/passed|Count/.test(resto), 'ao descer, a montaria some', resto)

  // na arena do boss não sai
  await t.console(`execute in minecraft:spawn run minecraft:tp ${nome} 0.5 33 195.5`)
  await t.espera(6000) // a recarga de 5 s
  msgs = await t.capturar(bot, () => bot.chat('/montaria'))
  const naArena = await t.console(`execute in minecraft:spawn positioned 0.5 33 195.5 if entity @e[type=horse,distance=..6]`)
  t.verificar(msgs.some(m => /arena do boss/.test(m)) && !/passed|Count/.test(naArena), 'na arena do boss a montaria não sai', msgs[0])
  await t.console(`lp user ${nome} permission unset vip.perk.montaria`)

  console.log('\n== PvP só na arena do boss ==')
  const atq = await t.criarBot(t.nomeNovo('Atq'))
  // golpe "de verdade": depois de um teleporte o primeiro não conta; espera e bate de novo
  const bater = async () => {
    await t.espera(1500)
    const alvo = Object.values(atq.entities).find(e => e.username === nome)
    if (alvo) atq.attack(alvo)
    await t.espera(1200)
    if (alvo) atq.attack(alvo)
    await t.espera(800)
  }
  await t.console(`effect clear ${nome}`)
  await t.console(`execute in minecraft:spawn run minecraft:tp ${nome} 0.5 65 20.5 180 0`)
  await t.console(`execute in minecraft:spawn run minecraft:tp ${atq.username} 0.5 65 18.5 0 0`)
  await t.console(`minecraft:effect give ${nome} instant_health 1 5 true`)
  await t.espera(1000)
  let antes = await vida(nome)
  await bater()
  t.verificar(await vida(nome) === antes, 'no spawn, um jogador não machuca o outro', `${antes} → ${await vida(nome)}`)

  await t.console(`execute in minecraft:spawn run minecraft:tp ${nome} 0.5 33 197.5 180 0`)
  await t.console(`execute in minecraft:spawn run minecraft:tp ${atq.username} 0.5 33 195.5 0 0`)
  await t.espera(1000)
  antes = await vida(nome)
  await bater()
  t.verificar(await vida(nome) < antes, 'na arena do boss, o golpe machuca', `${antes} → ${await vida(nome)}`)

  await t.console(`execute in minecraft:spawn run minecraft:tp ${nome} 0.5 65 43.5`)
  await t.console(`execute in minecraft:spawn run minecraft:tp ${atq.username} 0.5 65 43.5`)
  await t.terminar(bot, atq)
})
