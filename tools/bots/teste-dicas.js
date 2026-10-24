// NPCs de dicas (DicasPlugin, montados pelo montar-mapa.js): clique direito abre o balão em cima do
// NPC, a fala aparece aos poucos, o clique seguinte mostra a próxima fala, o balão some sozinho e o
// NPC não toma dano. Uso: node teste-dicas.js
const t = require('./lib')
const { Vec3 } = require('vec3')

// o Zé, o Guia, fica a sudeste da fonte do spawn (centro 0, 43)
const GUIA = new Vec3(6.5, 65, 49.5)
// o começo do texto do balão (a resposta do data get é cortada com "..." quando o texto é longo)
const balao = () => t.console(`execute in minecraft:spawn positioned ${GUIA.x} ${GUIA.y + 2.6} ${GUIA.z} run data get entity @e[type=text_display,limit=1,sort=nearest,distance=..1] text.extra[0].text`)

t.rodar(async () => {
  const bot = await t.criarBot(t.nomeNovo('Dic'))
  await t.console(`execute in minecraft:spawn run minecraft:tp ${bot.username} ${GUIA.x} 65 ${GUIA.z + 3} 180 0`)
  await t.espera(2000)
  const npc = Object.values(bot.entities).find(e => e.position.distanceTo(GUIA) < 1 && e.type !== 'player' && e.name === 'mannequin')
  t.verificar(!!npc, 'o NPC do Guia está no lugar', npc ? `${npc.name} ${npc.position}` : 'nenhum')
  if (!npc) return t.terminar(bot)

  await bot.activateEntity(npc)
  await t.espera(300)
  const comeco = await balao()
  await t.espera(2500)
  const inteiro = await balao()
  t.verificar(/Bem-vindo/.test(inteiro) && comeco.length < inteiro.length, 'o balão abre e a fala vai aparecendo aos poucos',
    `${comeco.slice(-30)} → ${inteiro.slice(-50)}`)

  // clicar enquanto ele escreve só completa a fala; depois de completa, passa pra próxima
  await t.espera(3500)
  await bot.activateEntity(npc)
  await t.espera(3500)
  const segunda = await balao()
  t.verificar(/X1/.test(segunda), 'o clique seguinte mostra a próxima fala', segunda.slice(-60))

  await t.espera(13000)
  t.verificar(/No entity was found|Nenhuma entidade/.test(await balao()), 'o balão some sozinho')

  await t.console(`execute in minecraft:spawn positioned ${GUIA.x} ${GUIA.y} ${GUIA.z} run damage @e[type=mannequin,limit=1,distance=..1] 1000`)
  await t.espera(500)
  const vivo = await t.console(`execute in minecraft:spawn positioned ${GUIA.x} ${GUIA.y} ${GUIA.z} run execute if entity @e[type=mannequin,distance=..1]`)
  t.verificar(vivo.includes('passed'), 'o NPC não toma dano', vivo)

  await t.terminar(bot)
})
