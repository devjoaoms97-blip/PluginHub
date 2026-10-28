// Asas da loja de créditos (CreditosPlugin): o bot compra uma asa e ela fica montada nele (item display
// como passageiro, pra o cliente desenhar grudada nas costas). O que importa é que o passageiro não trave
// o teleporte: /tp e /spawn do Essentials (pela API) e o /tp do jogo levam o jogador, e a asa volta pras costas.
// Uso: node teste-asas.js
const t = require('./lib')

const pos = async nome => {
  const r = await t.console(`data get entity ${nome} Pos`)
  return (r.match(/\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/) || []).slice(1).map(Number)
}
// a asa está nas costas: um item display colado no jogador (o "on passengers" do console não enxerga
// passageiro posto pela API num jogador, então mede a distância)
const temAsa = async nome => /passed/i.test(await t.console(`execute at ${nome} if entity @e[type=minecraft:item_display,distance=..2.5]`))
const perto = (a, b) => a.length === 3 && Math.abs(a[0] - b[0]) < 1 && Math.abs(a[2] - b[2]) < 1

t.rodar(async () => {
  const bot = await t.criarBot(t.nomeNovo('Asa'))
  const nome = bot.username
  await t.console(`execute in minecraft:spawn run minecraft:tp ${nome} 0.5 65 20.5`)
  await t.console(`creditos dar ${nome} 500 teste das asas`)
  await t.espera(1500)

  const janela = () => new Promise(ok => { const fim = setTimeout(() => ok(null), 1500); bot.once('windowOpen', j => { clearTimeout(fim); ok(j) }) })
  const clicar = async slot => { const nova = janela(); await bot.clickWindow(slot, 0, 0); return nova }

  console.log('\n== Comprar ==')
  let aberta = janela()
  bot.chat('/creditos')
  await aberta
  await clicar(20) // asas
  await clicar(9) // a primeira: pede confirmação
  await clicar(9) // confirma (já fica em uso)
  bot.closeWindow(bot.currentWindow)
  await t.espera(1000)
  t.verificar(await temAsa(nome), 'a asa comprada fica montada no jogador')

  console.log('\n== Teleportes ==')
  await t.console(`essentials:tp ${nome} 10.5 65 20.5`)
  await t.espera(1500)
  t.verificar(perto(await pos(nome), [10.5, 65, 20.5]), '/tp do Essentials leva o jogador com asa', String(await pos(nome)))
  t.verificar(await temAsa(nome), 'e a asa volta pras costas')

  bot.chat('/spawn')
  await t.espera(2500)
  const noSpawn = await pos(nome)
  t.verificar(noSpawn.length === 3 && !perto(noSpawn, [10.5, 65, 20.5]), '/spawn do Essentials leva o jogador com asa', String(noSpawn))
  t.verificar(await temAsa(nome), 'e a asa volta pras costas')

  await t.console(`minecraft:tp ${nome} 0.5 65 30.5`)
  await t.espera(1500)
  t.verificar(perto(await pos(nome), [0.5, 65, 30.5]), '/tp do jogo leva o jogador com asa', String(await pos(nome)))
  t.verificar(await temAsa(nome), 'e a asa volta pras costas')

  await t.terminar(bot)
})
