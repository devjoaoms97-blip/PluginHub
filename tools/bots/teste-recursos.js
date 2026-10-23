// Mundo de recursos (RecursosPlugin): /recursos leva a um lugar seguro dentro da borda; reset
// marcado com aviso no chat, que tira quem está lá, regenera o mundo (o bloco colocado some) e
// resgata quem estava lá desconectado. Demora uns 2 minutos (o reset é marcado pra 1 min).
// Uso: node teste-recursos.js
const t = require('./lib')

const W = 'execute in minecraft:recursos run'
const blocoE = async (x, y, z, bloco) => (await t.console(`${W} execute if block ${x} ${y} ${z} minecraft:${bloco}`)).includes('passed')

t.rodar(async () => {
  const a = await t.criarBot(t.nomeNovo('Rca')), b = await t.criarBot(t.nomeNovo('Rcb'))
  for (const bot of [a, b]) await t.console(`gamemode survival ${bot.username}`)

  console.log('\n== /recursos ==')
  let msgs = await t.capturar(a, () => a.chat('/recursos'), 6000)
  t.verificar(msgs.some(m => m.includes('Bem-vindo')), 'foi pro mundo de recursos', msgs.find(m => /Bem-vindo|Não achei/.test(m)))
  const p = a.entity.position
  const abaixo = a.blockAt(p.offset(0, -1, 0))
  t.verificar(abaixo && abaixo.boundingBox === 'block' && !/water|lava/.test(abaixo.name), 'de pé em chão firme', `${abaixo && abaixo.name} em ${p}`)
  t.verificar(Math.abs(p.x) <= 2500 && Math.abs(p.z) <= 2500, 'dentro do raio do teleporte', `${p}`)
  const borda = await t.console(`${W} worldborder get`)
  t.verificar(borda.includes('6000'), 'borda de 6000 blocos', borda)
  msgs = await t.capturar(a, () => a.chat('/recursos'))
  t.verificar(msgs.some(m => m.includes('Espere')), 'cooldown entre um /recursos e outro')

  // marca um bloco no mundo antigo, ao lado do bot
  const [mx, my, mz] = [Math.floor(p.x) + 2, Math.floor(p.y), Math.floor(p.z)]
  await t.console(`${W} setblock ${mx} ${my} ${mz} minecraft:diamond_block`)
  t.verificar(await blocoE(mx, my, mz, 'diamond_block'), 'bloco de diamante colocado no mundo antigo')

  // b também vai pro mundo de recursos e sai do servidor lá dentro
  await t.capturar(b, () => b.chat('/recursos'), 6000)
  const nomeB = b.username
  b.quit()
  await t.espera(1000)

  console.log('\n== Reset marcado (1 min) ==')
  const inicio = a.mensagens.length
  await t.console('recursos admin resetar 1')
  for (let s = 0; s < 120 && !a.mensagens.slice(inicio).some(m => m.includes('foi resetado')); s++) await t.espera(1000)
  const avisos = a.mensagens.slice(inicio)
  t.verificar(avisos.some(m => m.includes('vai ser resetado em 1 min')), 'aviso no chat antes do reset')
  t.verificar(avisos.some(m => m.includes('foi tirado do mundo de recursos')), 'quem estava lá foi tirado antes')
  t.verificar(avisos.some(m => m.includes('foi resetado')), 'o mundo foi resetado')
  await t.espera(2000)
  t.verificar(!(await blocoE(mx, my, mz, 'diamond_block')), 'o bloco do mundo antigo sumiu')
  t.verificar(String(a.game.dimension).length > 0 && Math.abs(a.entity.position.x - p.x) + Math.abs(a.entity.position.z - p.z) > 1,
    'o bot está fora do mundo de recursos', `${a.entity.position}`)

  console.log('\n== Quem estava lá desconectado ==')
  const b2 = await t.criarBot(nomeB)
  await t.espera(4000)
  t.verificar(b2.mensagens.some(m => m.includes('foi resetado enquanto você estava fora')), 'avisado ao entrar', JSON.stringify(b2.mensagens))
  const pb = b2.entity.position
  t.verificar(String(b2.game.dimension).includes('recursos') || (await t.console(`data get entity ${nomeB} Dimension`)).includes('recursos'),
    'no mundo de recursos novo (e não no mundo principal)', await t.console(`data get entity ${nomeB} Dimension`))
  const chaoB = b2.blockAt(pb.offset(0, -1, 0)), cabecaB = b2.blockAt(pb.offset(0, 1, 0))
  t.verificar(chaoB && chaoB.boundingBox === 'block' && cabecaB && cabecaB.boundingBox === 'empty', 'e levado pra um lugar seguro', `${chaoB && chaoB.name} em ${pb}`)

  await t.terminar(a, b2)
})
