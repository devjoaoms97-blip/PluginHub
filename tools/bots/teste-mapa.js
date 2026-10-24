// Mapa funcional (montar-mapa.js): um jogador novo nasce na fonte do spawn, usa /spawn e /warp, vê
// o painel "Como começar", os poços das minas estão cheios (e, se a staff já criou a pedreira, dá pra minerar),
// o spawn é protegido e a saída dele encosta na avenida das minas, o portal do saguão das arenas
// traz de volta, as 32 barracas (com holograma e o NPC de quem aluga) e as arenas estão cadastradas.
// Uso: node teste-mapa.js
const t = require('./lib')
const { Vec3 } = require('vec3')

const SPAWN = new Vec3(0.5, 65, 43.5) // centro do spawn (montar-mapa.js)
const dimensao = async nome => (await t.console(`data get entity ${nome} Dimension`)).match(/"minecraft:(\w+)"/)?.[1]
const blocoE = async (mundo, x, y, z, bloco) =>
  (await t.console(`execute in minecraft:${mundo} if block ${x} ${y} ${z} minecraft:${bloco}`)).includes('passed')

/** Põe o bot em cima da placa de pressão do portal e espera ele mudar de mundo. */
async function pisar(bot, mundo, x, z, esperado) {
  await t.console(`execute in minecraft:${mundo} run minecraft:tp ${bot.username} ${x + 0.5} 65 ${z + 0.5}`)
  for (let i = 0; i < 40; i++) { // o de Recursos procura um lugar seguro e pode gerar chunks
    await t.espera(500)
    if (await dimensao(bot.username) === esperado) return true
  }
  return false
}

t.rodar(async () => {
  const bot = await t.criarBot(t.nomeNovo('Map'))
  const nome = bot.username
  await t.espera(1000)

  console.log('\n== Chegada ==')
  t.verificar(await dimensao(nome) === 'spawn' && bot.entity.position.distanceTo(SPAWN) < 4,
    'o jogador novo nasce na fonte do spawn', `${await dimensao(nome)} ${bot.entity.position}`)
  let msgs = await t.capturar(bot, () => bot.chat('/warp minas'), 2000)
  t.verificar(bot.entity.position.distanceTo(new Vec3(0.5, 65, -24.5)) < 3, '/warp minas leva à avenida das minas', `${bot.entity.position}`)
  msgs = await t.capturar(bot, () => bot.chat('/spawn'), 2000)
  t.verificar(bot.entity.position.distanceTo(SPAWN) < 3, '/spawn volta pra fonte', `${bot.entity.position} ${msgs.join(' ')}`)
  msgs = await t.capturar(bot, () => bot.chat('/warp'))
  t.verificar(msgs.some(m => ['minas', 'mercado', 'boss', 'plateia-x1', 'plateia-gladiador'].every(w => m.includes(w))),
    'lista de warps', msgs.join(' ').slice(0, 160))

  // um painel "Como começar" só, no fim da avenida oeste, no lugar dos hologramas espalhados
  await t.console('execute in minecraft:spawn run forceload add -43 43')
  await t.espera(1000)
  const holos = await t.console('execute in minecraft:spawn positioned 0 65 43 run execute if entity @e[type=text_display,tag=mapa,distance=..16]')
  const painel = await t.console('execute in minecraft:spawn positioned -42.5 66.2 43.5 run data get entity @e[type=text_display,tag=mapa,limit=1,sort=nearest,distance=..1] text.text')
  t.verificar(!holos.includes('passed') && painel.includes('COMO COME'), 'o painel "Como começar" no fim da avenida oeste, sem hologramas espalhados', `${holos} | ${painel}`)

  console.log('\n== Minas ==')
  t.verificar(!(await blocoE('spawn', -19, 64, -40, 'air')) && !(await blocoE('spawn', 19, 50, -165, 'air')),
    'os poços das minas estão cheios até o chão')
  if ((await t.console('mina admin info pedreira')).includes('não existe')) {
    console.log('  (a pedreira ainda não foi criada pela staff: pulei a mineração)')
  } else {
    await t.console(`minecraft:give ${nome} diamond_pickaxe`)
    await t.console(`gamemode survival ${nome}`)
    const mina = t.mina('pedreira')
    await t.irParaMina(nome, mina)
    const quebrou = await t.minerar(bot, mina, 3)
    t.verificar(quebrou >= 1 && Object.keys(t.inventario(bot)).length > 1, 'dá pra minerar na pedreira', JSON.stringify(t.inventario(bot)))
  }

  console.log('\n== Spawn ==')
  // a avenida sul do spawn: o bot fica na calçada e tenta quebrar o chão à frente
  await t.console(`execute in minecraft:spawn run minecraft:tp ${nome} 0.5 65 60.5 0 60`)
  await t.espera(1000)
  await bot.dig(bot.blockAt(new Vec3(0, 64, 62)), true).catch(() => {})
  await t.espera(800)
  t.verificar(!(await blocoE('spawn', 0, 64, 62, 'air')), 'ninguém quebra o chão do spawn')
  // o túnel do portão norte do spawn (z -20) desemboca direto na avenida das minas (z -21)
  t.verificar(await blocoE('spawn', 0, 64, -20, 'dirt_path') && !(await blocoE('spawn', 0, 64, -21, 'air'))
    && await blocoE('spawn', 0, 65, -20, 'air') && await blocoE('spawn', 0, 65, -21, 'air'),
    'o portão do spawn está colado na avenida das minas')
  // e o portão sul (z 106) desemboca no patamar da escadaria do boss (z 107), que desce até a arena
  t.verificar(await blocoE('spawn', 0, 65, 106, 'air') && await blocoE('spawn', 0, 65, 107, 'air')
    && !(await blocoE('spawn', 0, 64, 107, 'air')) && await blocoE('spawn', 0, 33, 152, 'air'),
    'o portão sul do spawn está colado na escadaria do boss')
  await t.capturar(bot, () => bot.chat('/warp arenas'), 2000)
  t.verificar(await pisar(bot, 'arenas', 0, 5, 'spawn'), 'o portal do saguão das arenas volta pro spawn')

  console.log('\n== Cadastros ==')
  const lista = await t.console('barraca lista')
  t.verificar(['b1', 'b28', 'e1', 'e4'].every(id => new RegExp(`\\b${id}\\b`).test(lista)) && !/\bn1\b/.test(lista),
    '32 barracas no spawn (b1..b28 e e1..e4), sem as do mercado antigo', lista.slice(0, 120))
  // b1: NPC em (5, 65, 30) e holograma em cima do toldo (y 69 + altura do holograma)
  await t.console('execute in minecraft:spawn run forceload add 5 30')
  await t.espera(1500)
  const holoBarraca = await t.console('execute in minecraft:spawn positioned 5.5 71 30.5 run data get entity @e[type=text_display,limit=1,sort=nearest,distance=..3] text.extra[1].text')
  t.verificar(holoBarraca.includes('b1'), 'holograma da barraca b1, em cima do toldo', holoBarraca)
  await t.console(`eco give ${nome} 100000`)
  msgs = await t.capturar(bot, () => bot.chat('/barraca alugar b1'), 1500)
  const npc = await t.console('execute in minecraft:spawn positioned 5.5 65 30.5 run execute if entity @e[type=mannequin,distance=..1.5]')
  t.verificar(npc.includes('passed'), 'quem aluga a b1 ganha o NPC vendedor atrás do balcão', `${msgs.join(' ').slice(0, 80)} | ${npc}`)
  // o NPC não morre (nem com dano de criativo/comando) e, se for removido, volta sozinho
  await t.console('execute in minecraft:spawn positioned 5.5 65 30.5 run damage @e[type=mannequin,limit=1,distance=..1.5] 1000')
  await t.espera(500)
  const vivo = await t.console('execute in minecraft:spawn positioned 5.5 65 30.5 run execute if entity @e[type=mannequin,distance=..1.5]')
  t.verificar(vivo.includes('passed'), 'o NPC não toma dano', vivo)
  await t.console('execute in minecraft:spawn positioned 5.5 65 30.5 run minecraft:kill @e[type=mannequin,distance=..1.5]')
  await t.espera(1500)
  const voltou = await t.console('execute in minecraft:spawn positioned 5.5 65 30.5 run execute if entity @e[type=mannequin,distance=..1.5]')
  t.verificar(voltou.includes('passed'), 'o NPC morto por /kill volta sozinho', voltou)
  await t.capturar(bot, () => bot.chat('/barraca sair confirmar'), 1500)
  await t.console('execute in minecraft:spawn run forceload remove 5 30')
  const x1 = await t.console('duelo arena lista')
  t.verificar(x1.includes('axe') && !/\ba[123]\b/.test(x1) && !x1.includes('falta'), 'a arena de X1 (axepvp) pronta, sem as caixas antigas', x1)
  const glad = await t.console('gladiador arena info')
  t.verificar(glad.includes('pronta') && glad.includes('16 ponto'), 'arena do Gladiador pronta, com 16 pontos de entrada', glad)

  await t.terminar(bot)
})
