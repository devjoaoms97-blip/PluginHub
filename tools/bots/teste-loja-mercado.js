// Pedidos de 2026-10-31: o /loja das barracas (lista, teleporte até o vendedor, anúncio no chat com
// intervalo), o /pix qrcode só pra um jogador (e sem pagar duas vezes) e o mercado das máquinas por
// jogador (as vendas de um não baixam o preço do outro, e o mínimo cobre o combustível).
// Uso: node teste-loja-mercado.js
const t = require('./lib')
const { Vec3 } = require('vec3')

const dinheiro = r => Number((r.match(/\$([\d,.]+)/) || [, 'NaN'])[1].replace(/,/g, ''))
const saldo = async nome => dinheiro(await t.console(`balance ${nome}`))
const MAQUINISTA = new Vec3(112.5, 65, 43 + 14.5) // balcão Máquinas, lado sul da loja
const BARRACA = 'b5'

const janela = (bot, fn) => new Promise(ok => {
  const fim = setTimeout(() => ok(null), 3000)
  bot.once('windowOpen', j => { clearTimeout(fim); setTimeout(() => ok(j), 800) })
  fn()
})

/** Dá n tecidos ao jogador, de 64 em 64. */
async function darTecidos(nome, n) {
  for (let falta = n; falta > 0; falta -= 64) await t.console(`maquinas dar ${nome} produto tecido_fino ${Math.min(64, falta)}`)
}

/** Vende todos os tecidos do inventário no Maquinista e devolve quanto entrou. */
async function venderTecidos(bot) {
  await t.console(`execute in minecraft:spawn run minecraft:tp ${bot.username} 112.5 65 ${43 + 11.5} 0 0`)
  await t.espera(1500)
  const npc = Object.values(bot.entities).find(e => e.position.distanceTo(MAQUINISTA) < 1 && e.name === 'mannequin')
  const antes = await saldo(bot.username)
  const j = npc && await janela(bot, () => bot.activateEntity(npc))
  if (!j) return NaN
  await bot.clickWindow(38, 0, 0) // o Tecido Fino
  await t.espera(1200)
  bot.closeWindow(bot.currentWindow)
  return await saldo(bot.username) - antes
}

t.rodar(async () => {
  const dono = await t.criarBot(t.nomeNovo('Loja'))
  const visita = await t.criarBot(t.nomeNovo('Vis'))
  await t.espera(1500)
  for (const b of [dono, visita]) {
    await t.console(`eco set ${b.username} 100000`)
    await t.console(`minecraft:clear ${b.username}`)
    await t.console(`ranks admin set ${b.username} fazendeiro`)
  }

  console.log('\n== /loja ==')
  await t.console(`barraca liberar ${BARRACA}`)
  let msgs = await t.capturar(dono, () => dono.chat(`/barraca alugar ${BARRACA}`))
  t.verificar(msgs.some(m => /alugou|Barraca/.test(m)), `o dono alugou a ${BARRACA}`, msgs.join(' | ').slice(0, 160))
  msgs = await t.capturar(dono, () => dono.chat('/loja anunciar'))
  t.verificar(msgs.some(m => m.includes('não tem nada à venda')), 'sem nada à venda, não anuncia', msgs.join(' | '))
  await t.console(`minecraft:give ${dono.username} minecraft:diamond 10`)
  await t.espera(500)
  dono.setQuickBarSlot(0)
  await t.espera(300)
  await t.capturar(dono, () => dono.chat('/barraca vender 1 100'))

  msgs = await t.capturar(visita, () => visita.chat('/loja'))
  t.verificar(msgs.some(m => m.includes(dono.username) && m.includes('à venda')), '/loja lista a barraca alugada, com os itens à venda', msgs.join(' | ').slice(0, 200))

  await t.console(`execute in minecraft:terrenos run minecraft:tp ${visita.username} 0.5 80 0.5`)
  await t.espera(1000)
  msgs = await t.capturar(visita, () => visita.chat(`/loja ${dono.username}`), 2000)
  const vendedor = Object.values(visita.entities).filter(e => e.name === 'mannequin')
    .sort((a, b) => a.position.distanceTo(visita.entity.position) - b.position.distanceTo(visita.entity.position))[0]
  const dist = vendedor ? vendedor.position.distanceTo(visita.entity.position) : NaN
  t.verificar(msgs.some(m => m.includes('Você está na loja')) && dist < 5, '/loja <dono> leva à frente do vendedor', `${dist.toFixed?.(1)} blocos | ${msgs.join(' | ')}`)

  msgs = await t.capturar(visita, () => dono.chat('/loja anunciar Diamante barato'))
  t.verificar(msgs.some(m => m.includes('[Loja]') && m.includes('Diamante barato') && m.includes('VISITAR')), 'o anúncio chega no chat de todo mundo, com o [VISITAR]', msgs.join(' | '))
  msgs = await t.capturar(dono, () => dono.chat('/loja anunciar de novo'))
  t.verificar(msgs.some(m => m.includes('anunciar de novo em')), 'o segundo anúncio espera o intervalo', msgs.join(' | '))

  await t.console(`barraca liberar ${BARRACA}`)
  msgs = await t.capturar(visita, () => visita.chat(`/loja ${dono.username}`))
  t.verificar(msgs.some(m => m.includes('não tem barraca alugada')), 'sem aluguel, o destino deixa de existir', msgs.join(' | '))

  console.log('\n== /pix qrcode ==')
  msgs = await t.capturar(dono, () => dono.chat('/pix qrcode 100'))
  t.verificar(msgs.some(m => m.includes('Uso') || m.includes('não está online')), 'o /pix qrcode <valor> público saiu', msgs.join(' | '))
  msgs = await t.capturar(visita, () => dono.chat(`/pix qrcode ${visita.username} 1000 teste`))
  t.verificar(msgs.some(m => m.includes(`QR Code Pix de ${dono.username}`) && m.includes('[PAGAR]')), 'o QR Code chega só pra quem foi cobrado, com o [PAGAR]', msgs.join(' | '))
  const antesPix = await saldo(visita.username)
  await t.capturar(visita, () => visita.chat(`/pix aceitar ${dono.username}`))
  msgs = await t.capturar(visita, () => visita.chat(`/pix aceitar ${dono.username}`))
  t.verificar(await saldo(visita.username) === antesPix - 1000, 'clicar duas vezes no [PAGAR] paga uma vez só',
    `${antesPix} → ${await saldo(visita.username)} | ${msgs.join(' | ')}`)

  console.log('\n== Mercado das máquinas por jogador ==')
  await darTecidos(dono.username, 1600) // de R$ 16 a R$ 8 (o mínimo) são ~1.400 vendas com passo 0,05%
  const muito = await venderTecidos(dono)
  t.verificar(muito > 1600 * 8 * 0.98, 'mesmo vendendo 1.600 de uma vez, nenhuma unidade sai abaixo do mínimo (R$ 8)', `+${muito.toFixed(2)}`)
  await darTecidos(dono.username, 10)
  await darTecidos(visita.username, 10)
  const doDono = await venderTecidos(dono)
  const daVisita = await venderTecidos(visita)
  t.verificar(daVisita > doDono * 1.5, 'as vendas do dono baixaram só o preço dele: a visita vende 10 pelo preço cheio',
    `dono +${doDono.toFixed(2)}, visita +${daVisita.toFixed(2)}`)
  t.verificar(doDono >= 10 * 8 * 0.98 - 0.01, 'no mínimo, o tecido ainda paga o combustível (R$ 6,25)', `+${(doDono / 10).toFixed(2)} cada`)

  await t.terminar(dono, visita)
})
