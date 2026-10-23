// Terrenos: compra, borda da grade, proteção, amigo, imposto, confisco por imposto atrasado,
// leilão de imóvel (dono antigo pagando a dívida, lance superado e devolvido) e leilão sem lances.
// Usa dois jogadores novos a cada execução. Uso: node teste-terrenos.js
const t = require('./lib')

const MUNDO = 'terrenos'
const CELULA = 37 // tamanho 32 + rua 5, como no config padrão

const dinheiro = r => Number((r.match(/\$([\d,.]+)/) || [, 'NaN'])[1].replace(/,/g, ''))
const blocoE = async (x, y, z, bloco) =>
  (await t.console(`execute in minecraft:${MUNDO} if block ${x} ${y} ${z} minecraft:${bloco}`)).includes('passed')
const tpPara = (nome, x, y, z) => t.console(`execute in minecraft:${MUNDO} run minecraft:tp ${nome} ${x} ${y} ${z}`)

/** Coloca pedregulho em cima do chão em (x, z), olhando de perto (terra viraria grama sozinha). */
async function colocarBloco(bot, x, z) {
  const bloco = bot.inventory.items().find(i => i.name === 'cobblestone')
  await bot.equip(bloco, 'hand')
  const chao = bot.blockAt(new (require('vec3').Vec3)(x, 64, z))
  await bot.placeBlock(chao, new (require('vec3').Vec3)(0, 1, 0)).catch(() => {})
  await t.espera(800)
}

async function tentarQuebrar(bot, x, y, z) {
  const bloco = bot.blockAt(new (require('vec3').Vec3)(x, y, z))
  await bot.dig(bloco, true).catch(() => {})
  await t.espera(800)
}

t.rodar(async () => {
  const nomeA = t.nomeNovo('Ter'), nomeB = t.nomeNovo('Viz')
  const a = await t.criarBot(nomeA)
  const b = await t.criarBot(nomeB)
  for (const n of [nomeA, nomeB]) {
    await t.console(`gamemode survival ${n}`)
    await t.console(`minecraft:clear ${n}`)
  }
  await t.console(`eco set ${nomeA} 200000`)

  console.log('\n== Compra ==')
  let msgs = await t.capturar(a, () => a.chat('/terreno auto'), 3000)
  const id = (msgs.join(' ').match(/terreno (-?\d+;-?\d+)/) || [])[1]
  t.verificar(!!id, '/terreno auto achou um terreno à venda', id)
  const [gx, gz] = id.split(';').map(Number)
  const minX = gx * CELULA, minZ = gz * CELULA
  t.verificar(await blocoE(minX - 1, 65, minZ + 5, 'stone_brick_slab'), 'borda de laje em volta do terreno')
  t.verificar(await blocoE(minX - 3, 64, minZ + 5, 'stone_bricks'), 'rua de pedra entre os terrenos')

  await tpPara(nomeA, minX + 16.5, 65, minZ + 16.5)
  await t.espera(1500)
  msgs = await t.capturar(a, () => a.chat('/terreno comprar'))
  t.verificar(msgs.some(m => m.includes('comprado')), 'comprou', msgs[0])
  t.verificar(dinheiro(await t.console(`balance ${nomeA}`)) === 150000, 'pagou R$ 50.000')
  msgs = await t.capturar(a, () => a.chat('/terreno comprar'))
  t.verificar(msgs.some(m => m.includes('é de')), 'não compra o mesmo terreno de novo')

  console.log('\n== Proteção e amigo ==')
  const [px, pz] = [minX + 18, minZ + 16]
  await t.console(`minecraft:give ${nomeA} cobblestone 4`)
  await t.espera(800)
  await colocarBloco(a, px, pz)
  t.verificar(await blocoE(px, 65, pz, 'cobblestone'), 'o dono constrói no próprio terreno')

  await tpPara(nomeB, minX + 20.5, 65, minZ + 16.5)
  await t.espera(1500)
  await tentarQuebrar(b, px, 65, pz)
  t.verificar(await blocoE(px, 65, pz, 'cobblestone'), 'o vizinho não quebra')
  await tpPara(nomeB, minX - 2.5, 65, minZ + 16.5)
  await t.espera(1000)
  await tentarQuebrar(b, minX - 3, 64, minZ + 16)
  t.verificar(await blocoE(minX - 3, 64, minZ + 16, 'stone_bricks'), 'ninguém quebra a rua')

  msgs = await t.capturar(a, () => a.chat(`/terreno amigo add ${nomeB}`))
  t.verificar(msgs.some(m => m.includes('agora pode construir')), 'adicionou o amigo')
  await tpPara(nomeB, minX + 20.5, 65, minZ + 16.5)
  await t.espera(1500)
  await tentarQuebrar(b, px, 65, pz)
  t.verificar(!(await blocoE(px, 65, pz, 'cobblestone')), 'o amigo quebra')
  await t.capturar(a, () => a.chat(`/terreno amigo remover ${nomeB}`))
  await colocarBloco(a, px, pz) // fica no terreno pro leilão
  t.verificar(await blocoE(px, 65, pz, 'cobblestone'), 'bloco de volta (vai junto no leilão)')

  console.log('\n== Imposto ==')
  msgs = await t.capturar(a, () => a.chat('/terreno imposto'))
  t.verificar(msgs.some(m => m.includes('R$ 1.000,00')), 'imposto mínimo de R$ 1.000 no primeiro rank', msgs[0])
  msgs = await t.capturar(a, () => a.chat('/terreno imposto pagar'))
  t.verificar(msgs.some(m => m.includes('Imposto pago')), 'pagou uma semana adiantada')

  console.log('\n== Confisco por imposto atrasado ==')
  await t.console(`eco set ${nomeA} 0`)
  await t.console(`terreno admin vencer ${id}`)
  const inicio = b.mensagens.length
  for (let s = 0; s < 40 && !b.mensagens.slice(inicio).some(m => m.includes('vai a leilão')); s++) await t.espera(1000)
  t.verificar(b.mensagens.slice(inicio).some(m => m.includes('vai a leilão')), 'sem saldo, o terreno foi a leilão (anúncio no chat)')
  t.verificar(a.mensagens.slice(-10).some(m => m.includes('foi confiscado')), 'o dono foi avisado')

  console.log('\n== Leilão de imóvel ==')
  await t.console(`eco set ${nomeA} 100000`)
  msgs = await t.capturar(a, () => a.chat(`/terreno lance ${id} 60000`))
  t.verificar(msgs.some(m => m.includes('pague antes a dívida')), 'o dono antigo não dá lance sem pagar a dívida')
  msgs = await t.capturar(a, () => a.chat(`/terreno divida pagar ${id}`))
  t.verificar(msgs.some(m => m.includes('Dívida paga')), 'pagou a dívida')
  const divida = 100000 - dinheiro(await t.console(`balance ${nomeA}`))
  t.verificar(divida >= 1000, 'a dívida foi cobrada', `R$ ${divida}`)
  msgs = await t.capturar(a, () => a.chat(`/terreno lance ${id} 60000`))
  t.verificar(msgs.some(m => m.includes('Lance de')), 'o dono antigo deu lance')

  await t.console(`eco set ${nomeB} 200000`)
  msgs = await t.capturar(b, () => b.chat(`/terreno lance ${id} 61000`))
  t.verificar(msgs.some(m => m.includes('lance mínimo')), 'recusa lance abaixo do incremento de 5%')
  const saldoAantes = dinheiro(await t.console(`balance ${nomeA}`))
  msgs = await t.capturar(b, () => b.chat(`/terreno lance ${id} 70000`))
  t.verificar(msgs.some(m => m.includes('Lance de')), 'o vizinho deu um lance maior')
  t.verificar(dinheiro(await t.console(`balance ${nomeA}`)) === saldoAantes + 60000, 'o lance superado voltou')

  await t.console(`terreno admin encerrar ${id}`)
  await t.espera(2500)
  msgs = await t.capturar(b, () => b.chat(`/terreno info ${id}`))
  t.verificar(msgs.some(m => m.includes(`Dono: ${nomeB}`)), 'o maior lance levou o terreno')
  t.verificar(await blocoE(px, 65, pz, 'cobblestone'), 'com tudo o que tinha dentro')
  t.verificar(dinheiro(await t.console(`balance ${nomeB}`)) === 130000, 'o lance vencedor foi cobrado (e some da economia)')

  console.log('\n== Leilão sem lances ==')
  await t.console(`terreno admin leiloar ${id}`)
  await t.console(`terreno admin encerrar ${id}`)
  await t.espera(4000)
  t.verificar(await blocoE(px, 65, pz, 'air') && await blocoE(px, 64, pz, 'grass_block'), 'sem lances, o terreno foi limpo')
  msgs = await t.capturar(b, () => b.chat(`/terreno info ${id}`))
  t.verificar(msgs.some(m => m.includes('À venda')), 'e voltou à venda')

  await t.terminar(a, b)
})
