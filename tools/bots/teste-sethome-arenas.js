// /sethome deve ser bloqueado dentro das arenas do boss, X1, Gladiador e câmara dinâmica do tesouro,
// mas liberado imediatamente fora de cada limite. Uso: node teste-sethome-arenas.js
const t = require('./lib')
const { Vec3 } = require('vec3')

const MSG_BLOQUEIO = 'marcar uma home dentro de'
const MSG_SEM_PERMISSAO = 'Você não tem acesso a esse comando'
const foraSpawn = ['terrenos', 20.5, 70, 20.5]

async function tp(bot, mundo, x, y, z) {
  await t.console(`execute in minecraft:${mundo} run minecraft:tp ${bot.username} ${x} ${y} ${z}`)
  await t.espera(700)
}

async function tentarSetHome(bot, nome) {
  return t.capturar(bot, () => bot.chat(`/sethome ${nome}`), 800)
}

// As casas só são apagadas no fim: o /delhome é um comando de teleporte das arenas, e depois dele o
// AreasPlugin barra por 10 s qualquer teleporte pra dentro delas (inclusive o tp do teste).
const casas = []

async function verificarDentroEFora(bot, caso) {
  const nome = t.nomeNovo('home')
  await tp(bot, caso.mundo, ...caso.dentro)
  const dentro = await tentarSetHome(bot, nome)
  t.verificar(dentro.some(m => m.includes(MSG_BLOQUEIO)), `${caso.nome}: /sethome bloqueado dentro`, dentro.join(' | '))

  await tp(bot, caso.mundo, ...caso.fora)
  const fora = await tentarSetHome(bot, nome)
  casas.push(nome)
  t.verificar(!fora.some(m => m.includes(MSG_BLOQUEIO) || m.includes(MSG_SEM_PERMISSAO)), `${caso.nome}: /sethome não é barrado logo fora do limite`, fora.join(' | '))
  await t.espera(2200) // o AreasPlugin limita avisos repetidos a um a cada 2 s
}

t.rodar(async () => {
  const bot = await t.criarBot(t.nomeNovo('Home'))
  const fora = await t.criarBot(t.nomeNovo('HomeF'))
  const nome = bot.username
  await t.console(`deop ${nome}`)
  await t.console(`deop ${fora.username}`)
  for (const jogador of [nome, fora.username]) {
    await t.console(`lp user ${jogador} permission set essentials.sethome true`)
    await t.console(`lp user ${jogador} permission set essentials.delhome true`)
    await t.console(`lp user ${jogador} permission set essentials.sethome.multiple.unlimited true`)
  }
  await t.console(`gamemode survival ${nome}`)
  await t.console(`gamemode survival ${fora.username}`)

  console.log('\n== Caixas fixas das arenas ==')
  const caixas = [
    { nome: 'arena do boss', mundo: 'spawn', dentro: [0.5, 34, 195.5], fora: [61.5, 34, 195.5] },
    { nome: 'piso do X1', mundo: 'arenas', dentro: [-600.5, 70, 0.5], fora: [-573.5, 70, 0.5] },
    { nome: 'piso do Gladiador', mundo: 'arenas', dentro: [600.5, 70, 0.5], fora: [677.5, 70, 0.5] },
  ]
  for (const caso of caixas) await verificarDentroEFora(bot, caso)

  console.log('\n== Câmara dinâmica do tesouro ==')
  const mundo = 'tesouros', superficie = -20, x = 1300, z = -1300
  const ativas = await t.console('tesouro ativas')
  if (!ativas.includes('Nenhuma câmara aberta')) {
    throw new Error('Há uma câmara de jogador ativa; não vou interferir nela. Rode este teste quando estiverem todas fechadas.')
  }

  await t.console(`tesouro dar ${nome} comum ${x} ${z}`)
  await tp(bot, mundo, x + 1.5, superficie + 1, z + 0.5)
  await t.espera(700)
  await t.capturar(bot, () => bot.dig(bot.blockAt(new Vec3(x, superficie, z)), true).catch(() => {}), 1800)
  t.verificar(bot.entity.position.y < superficie - 10, 'o mapa abriu uma câmara para testar a região dinâmica', `${bot.entity.position}`)

  const dentro = await tentarSetHome(bot, t.nomeNovo('camara'))
  t.verificar(dentro.some(m => m.includes(MSG_BLOQUEIO)), '/sethome bloqueado dentro da câmara ativa', dentro.join(' | '))

  await tp(fora, mundo, x + 10.5, superficie + 1, z + 0.5)
  const homeFora = t.nomeNovo('fora')
  const foraCamara = await tentarSetHome(fora, homeFora)
  t.verificar(!foraCamara.some(m => m.includes(MSG_BLOQUEIO) || m.includes(MSG_SEM_PERMISSAO)), '/sethome permitido fora dos limites da câmara ainda ativa')

  const lista = await t.console('tesouro ativas')
  const linha = lista.split(/\r?\n/).find(l => l.includes(`X ${x} Z ${z}`))
  const id = linha?.match(/^\s*([a-z0-9]+):/i)?.[1]
  if (id) await t.console(`tesouro encerrar ${id}`)
  else await t.console('tesouro encerrar todas') // no início não havia outras câmaras abertas

  await tp(fora, ...foraSpawn)
  await t.capturar(fora, () => fora.chat(`/delhome ${homeFora}`), 500)
  await tp(bot, ...foraSpawn)
  for (const casa of casas) await t.capturar(bot, () => bot.chat(`/delhome ${casa}`), 400)
  for (const jogador of [nome, fora.username]) {
    await t.console(`lp user ${jogador} permission unset essentials.sethome`)
    await t.console(`lp user ${jogador} permission unset essentials.delhome`)
    await t.console(`lp user ${jogador} permission unset essentials.sethome.multiple.unlimited`)
  }
  await t.terminar(bot, fora)
})
