// Antihack (GrimAC): um bot sob o Grim ({ antihack: true }) luta e tenta flutuar; outro, isento como os
// bots dos outros testes, faz o mesmo pra mostrar que quem barra é o Grim e não o jogo.
// O alcance (Reach) não entra: o Grim confere o golpe junto com os pacotes de movimento de cada tique, que
// o Mineflayer não manda como o cliente (por isso o TickTimer dos bots), e o resultado muda de uma rodada
// pra outra. Luta no pátio da arena de X1 (axepvp), onde o PvP vale. Precisa das arenas (node montar-mapa.js).
// Uso: node teste-antihack.js
const fs = require('fs')
const path = require('path')
const t = require('./lib')

const W = 'execute in minecraft:arenas run'
const LOG = path.join(process.env.MC_TESTE || 'C:\\mc-teste', 'logs', 'latest.log')
// quem acabou de ser teleportado fica 4 s invulnerável (teleport-invulnerability do Essentials)
const DEPOIS_DO_TP = 5000

/** Vida que o alvo perde com um golpe do atacante (espera a vida voltar a cheia antes). */
async function golpe(atacante, alvo) {
  await t.console(`effect give ${alvo.username} minecraft:instant_health 1 10 true`)
  await t.espera(800)
  const antes = alvo.health
  const e = Object.values(atacante.entities).find(x => x.username === alvo.username)
  await atacante.lookAt(e.position.offset(0, 1.6, 0), true)
  atacante.attack(e)
  await t.espera(700)
  return antes - alvo.health
}

/** Sobe 3 blocos e fica parado no ar por 2 s; devolve quanto o servidor acha que o bot subiu. */
async function flutuar(bot) {
  const y = async () => Number((await t.console(`data get entity ${bot.username} Pos[1]`)).match(/(-?[\d.]+)d?$/)[1])
  const chao = await y()
  bot.physics.gravity = 0
  bot.entity.velocity.set(0, 0, 0)
  bot.entity.position.y += 3
  await t.espera(2000)
  const noAr = await y()
  bot.physics.gravity = 0.08
  await t.espera(1500)
  return noAr - chao
}

const alertas = (nome, inicio) => fs.readFileSync(LOG, 'utf8').split(/\r?\n/).slice(inicio)
  .filter(l => l.includes(`Grim » ${nome} `))

t.rodar(async () => {
  const inicio = fs.readFileSync(LOG, 'utf8').split(/\r?\n/).length
  const vigiado = await t.criarBot(t.nomeNovo('Ahv'), { antihack: true })
  const isento = await t.criarBot(t.nomeNovo('Ahi'))
  const alvo = await t.criarBot(t.nomeNovo('Aha'))
  for (const bot of [vigiado, isento, alvo]) {
    await t.console(`gamemode survival ${bot.username}`)
    await t.console(`minecraft:clear ${bot.username}`)
    await t.console(`minecraft:give ${bot.username} diamond_sword`)
  }
  await t.console(`${W} minecraft:tp ${alvo.username} -599.5 65 0.5 90 0`)
  await t.console(`${W} minecraft:tp ${vigiado.username} -601.5 65 0.5 -90 0`)
  await t.espera(DEPOIS_DO_TP)
  await vigiado.equip(vigiado.inventory.items().find(i => i.name === 'diamond_sword'), 'hand')

  console.log('\n== Golpe de perto ==')
  // de vez em quando um golpe do Mineflayer não conta com o Grim ligado: vale a maioria de 3
  const perto = []
  for (let i = 0; i < 3; i++) perto.push(await golpe(vigiado, alvo))
  t.verificar(perto.filter(d => d > 0).length >= 2, 'o bot sob o Grim bate normal a 2 blocos', perto.map(d => d.toFixed(1)).join(' '))

  console.log('\n== Flutuar ==')
  await t.console(`${W} minecraft:tp ${vigiado.username} -601.5 65 4.5 0 0`)
  await t.console(`${W} minecraft:tp ${isento.username} -597.5 65 4.5 0 0`)
  await t.espera(DEPOIS_DO_TP)
  const subiuIsento = await flutuar(isento)
  t.verificar(subiuIsento > 2.5, 'sem o Grim, o servidor aceita o bot parado no ar', subiuIsento.toFixed(2))
  const subiu = await flutuar(vigiado)
  t.verificar(subiu < 1, 'o Grim puxa de volta quem flutua', subiu.toFixed(2))

  console.log('\n== Alertas ==')
  // os primeiros segundos, até o grim.exempt chegar, contam: só Reach e movimento importam
  const doIsento = alertas(isento.username, inicio).filter(l => /Reach|Simulation/.test(l))
  t.verificar(doIsento.length === 0, 'nenhum alerta de Reach ou de movimento do bot isento', doIsento[0])

  await t.terminar(vigiado, isento, alvo)
})
