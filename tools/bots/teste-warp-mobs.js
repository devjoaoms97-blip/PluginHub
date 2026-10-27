// Warp mobs: com um jogador no salão, cada estátua perto dele ganha mobs normais em volta do spawner
// dela, até o limite; eles não passam da linha vermelha, não queimam no sol do teto de vidro, não somem
// de longe e, mortos, a estátua repõe.
//   node teste-warp-mobs.js   (servidor ligado, com o salão construído: montar-mapa.js construir mobs)
const t = require('./lib')

/** Quantos mobs vivos cada estátua tem agora, pelo /spawners mobs lista. */
async function vivos() {
  const lista = await t.console('spawners mobs lista')
  const porId = {}
  for (const m of lista.matchAll(/(\w+) (?:§.)*\S+ (?:§.)*em spawn [-\d ]+(?:§.)*vivos: (?:§.)*(\d+)/g)) porId[m[1]] = Number(m[2])
  return porId
}

t.rodar(async () => {
  const bot = await t.criarBot('MobsTeste')
  await t.console('gamemode creative MobsTeste') // o bot só olha: não apanha dos mobs
  await t.capturar(bot, () => bot.chat('/warp mobs'), 2000)
  // no meio do salão, perto das estátuas do meio
  await t.console('execute in minecraft:spawn run tp MobsTeste -147.5 49 43.5')
  await t.espera(20000)
  let n = await vivos()
  t.verificar(['zumbi', 'esqueleto', 'aranha', 'creeper', 'vaca', 'ovelha'].every(id => n[id] >= 1),
    'cada estátua perto do jogador ganhou mob', JSON.stringify(n))

  const fora = await t.console('execute in minecraft:spawn run execute if entity @e[type=!player,type=!text_display,type=!mannequin,x=-91,y=48,z=4,dx=27,dy=40,dz=78]')
  t.verificar(!fora.includes('passed'), 'nenhum mob no vestíbulo nem na escadaria', fora)

  // de dia, o sol entra pelo vidro: o zumbi da warp não pega fogo (e não perde vida)
  const hora = await t.console('execute in minecraft:spawn run minecraft:time query daytime')
  const fogo = await t.console('execute in minecraft:spawn positioned -147 49 43 run data get entity @e[type=zombie,limit=1,sort=nearest] Fire')
  const vida = await t.console('execute in minecraft:spawn positioned -147 49 43 run data get entity @e[type=zombie,limit=1,sort=nearest] Health')
  t.verificar(/: (-\d+|0)s?\s*$/.test(fogo) && /: 20(\.0)?f/.test(vida), 'o zumbi não pega fogo no sol do teto de vidro', `${hora} | ${fogo} | ${vida}`)
  const persiste = await t.console('execute in minecraft:spawn positioned -147 49 43 run data get entity @e[type=zombie,limit=1,sort=nearest] PersistenceRequired')
  t.verificar(/1b/.test(persiste), 'o mob da warp não some de longe', persiste)

  // matar: some e a estátua repõe no ciclo seguinte
  await t.console('execute in minecraft:spawn run minecraft:kill @e[type=zombie,x=-208,y=48,z=4,dx=116,dy=36,dz=79]')
  const logoDepois = (await vivos()).zumbi
  await t.espera(8000)
  const depois = (await vivos()).zumbi
  t.verificar(logoDepois === 0 && depois >= 1, 'morto o zumbi, a estátua repõe', `${logoDepois} → ${depois}`)

  // as do fundo só nascem com alguém perto delas
  await t.console('execute in minecraft:spawn run tp MobsTeste -187.5 49 43.5')
  await t.espera(14000)
  n = await vivos()
  t.verificar(['slime', 'blaze', 'enderman'].every(id => n[id] >= 1), 'slime, blaze e enderman nascem com o jogador no fundo do salão', JSON.stringify(n))
  await t.espera(24000)
  n = await vivos()
  t.verificar(Math.max(...Object.values(n)) <= 4, 'nenhuma estátua passa do limite (4)', JSON.stringify(n))
  bot.quit()
})
