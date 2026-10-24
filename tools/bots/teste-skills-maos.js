// Skills por mão (CombatePlugin + SkillsPlugin): o golpe da segunda mão treina a skill da arma
// dela (machado na esquerda = Machado; mão vazia = Porradeiro), e não a da mão principal. Lê o XP
// salvo em plugins/SkillsPlugin/playerdata depois que o bot sai. Uso: node teste-skills-maos.js
const t = require('./lib')
const fs = require('fs')
const PASTA = process.env.MC_TESTE || 'C:/mc-teste'
t.rodar(async () => {
  const a = await t.criarBot(t.nomeNovo('Skl')), b = await t.criarBot(t.nomeNovo('Skv'))
  const W = 'execute in minecraft:arenas run'
  for (const bot of [a, b]) { await t.console(`gamemode survival ${bot.username}`); await t.console(`minecraft:clear ${bot.username}`) }
  await t.console(`${W} minecraft:tp ${a.username} -601.5 65 0.5 -90 0`)
  await t.console(`${W} minecraft:tp ${b.username} -599.5 65 0.5 90 0`)
  await t.console(`minecraft:item replace entity ${a.username} weapon.mainhand with minecraft:diamond_sword`)
  await t.console(`minecraft:item replace entity ${a.username} weapon.offhand with minecraft:diamond_axe`)
  await t.espera(1500)
  const e = () => Object.values(a.entities).find(x => x.username === b.username)
  const seg = () => a._client.write('use_entity', { target: e().id, mouse: 0, sneaking: false, hand: 1, location: { x: 0, y: 0, z: 0 } })
  await a.lookAt(e().position.offset(0, 1.6, 0), true)
  for (let i = 0; i < 3; i++) { seg(); await t.espera(700); await t.console(`effect give ${b.username} minecraft:instant_health 1 10 true`) }
  await t.console(`minecraft:item replace entity ${a.username} weapon.offhand with minecraft:air`)
  await t.espera(500)
  for (let i = 0; i < 3; i++) { seg(); await t.espera(700); await t.console(`effect give ${b.username} minecraft:instant_health 1 10 true`) }
  const nome = a.username
  a.quit(); b.quit(); await t.espera(2000)
  const cache = JSON.parse(fs.readFileSync(`${PASTA}/usercache.json`, 'utf8'))
  const uuid = cache.find(c => c.name === nome).uuid
  const yml = fs.readFileSync(`${PASTA}/plugins/SkillsPlugin/playerdata/${uuid}.yml`, 'utf8')
  const linhas = yml.split(/\r?\n/)
  const campo = (s, c) => { const i = linhas.indexOf(`${s}:`); return Number(linhas.slice(i + 1, i + 3).find(l => l.trim().startsWith(c + ':')).split(':')[1]) }
  const xp = s => campo(s, 'xp')
  const nivel = s => campo(s, 'nivel')
  console.log(`  XP: espadas ${xp('espadas')}, machado ${xp('machado')}, porradeiro ${xp('porradeiro')}`)
  t.verificar(nivel('machado') > 1 || xp('machado') > 0, 'o machado da mão esquerda treinou a skill Machado')
  t.verificar(nivel('porradeiro') > 1 || xp('porradeiro') > 0, 'o soco da mão esquerda treinou o Porradeiro')
  t.verificar(nivel('espadas') === 1 && xp('espadas') === 0, 'a espada da direita não treinou (não bateu)')
  await t.terminar()
})
