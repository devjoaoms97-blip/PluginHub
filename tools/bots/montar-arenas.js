// Monta arenas simples no mundo "arenas" pra testar (chão e muro de pedra) e marca os pontos
// do X1 e do Gladiador com um bot operador. A versão bonita fica pra quando o mapa chegar.
// Uso: node montar-arenas.js
const t = require('./lib')

const W = 'execute in minecraft:arenas run'

async function caixa(x1, z1, x2, z2, alturaMuro) {
  await t.console(`${W} forceload add ${x1} ${z1} ${x2} ${z2}`)
  await t.console(`${W} fill ${x1} 64 ${z1} ${x2} 64 ${z2} minecraft:smooth_stone`)
  await t.console(`${W} fill ${x1} 65 ${z1} ${x2} ${64 + alturaMuro} ${z2} minecraft:stone_bricks hollow`)
  await t.console(`${W} fill ${x1 + 1} 65 ${z1 + 1} ${x2 - 1} ${64 + alturaMuro} ${z2 - 1} minecraft:air`)
  await t.console(`${W} forceload remove ${x1} ${z1} ${x2} ${z2}`)
}

/** O bot vai até o ponto (olhando pra yaw) e roda o comando de staff. */
async function marcar(bot, x, y, z, yaw, comando) {
  await t.console(`${W} minecraft:tp ${bot.username} ${x} ${y} ${z} ${yaw} 0`)
  await t.espera(1200)
  const msgs = await t.capturar(bot, () => bot.chat(comando))
  t.verificar(msgs.some(m => /marcad|criada|atualizada|Pronta|pronta/.test(m)), comando, msgs[0])
}

t.rodar(async () => {
  // duas arenas de X1 (21x21, muro de 4) e a do Gladiador (41x41, muro de 6)
  await caixa(90, -10, 110, 10, 4)
  await caixa(90, 30, 110, 50, 4)
  await caixa(-20, 80, 20, 120, 6)

  const staff = await t.criarBot('BotStaff')
  await t.console('op BotStaff')
  await t.console('gamemode creative BotStaff')
  await t.espera(1000)

  for (const [nome, z] of [['a1', 0], ['a2', 40]]) {
    await marcar(staff, 100.5, 65, z + 0.5, 0, `/duelo arena criar ${nome}`)
    await marcar(staff, 93.5, 65, z + 0.5, -90, `/duelo arena pos1 ${nome}`)
    await marcar(staff, 107.5, 65, z + 0.5, 90, `/duelo arena pos2 ${nome}`)
  }

  await marcar(staff, -19.5, 64, 81.5, 0, '/gladiador arena pos1')
  await marcar(staff, 19.5, 72, 119.5, 0, '/gladiador arena pos2')
  await t.capturar(staff, () => staff.chat('/gladiador arena spawn limpar')) // rodar de novo não duplica os pontos
  for (const [x, z, yaw] of [[-14.5, 86.5, -45], [15.5, 86.5, 45], [-14.5, 114.5, -135], [15.5, 114.5, 135]]) {
    await marcar(staff, x, 65, z, yaw, '/gladiador arena spawn add')
  }
  await t.console('deop BotStaff')
  await t.console('gamemode survival BotStaff')
  await t.console(`${W} minecraft:tp BotStaff 0.5 65 0.5`)
  await t.terminar(staff)
})
