// Terrenos: compra, borda da grade, holograma da entrada, proteção, amigo (só com baús e com tudo), imposto, confisco por imposto atrasado,
// leilão de imóvel (o Leiloeiro contando o que está guardado, dono antigo pagando a dívida, lance superado e devolvido) e leilão sem lances.
// Usa dois jogadores novos a cada execução. Uso: node teste-terrenos.js
const t = require('./lib')

const MUNDO = 'terrenos'
const CELULA = 37 // tamanho 32 + rua 5, como no config padrão

const dinheiro = r => Number((r.match(/\$([\d,.]+)/) || [, 'NaN'])[1].replace(/,/g, ''))
const blocoE = async (x, y, z, bloco) =>
  (await t.console(`execute in minecraft:${MUNDO} if block ${x} ${y} ${z} minecraft:${bloco}`)).includes('passed')
// texto do holograma da entrada (o TextDisplay mais perto de cima da borda), pedaço por pedaço:
// a resposta do console corta textos longos
async function holograma(x, z) {
  let texto = ''
  for (let i = 0; i < 30; i++) {
    const r = await t.console(`execute in minecraft:${MUNDO} positioned ${x + 0.5} 67.5 ${z + 0.5} run data get entity @e[type=text_display,limit=1,sort=nearest,distance=..2] text.extra[${i}].text`)
    const m = r.match(/entity data: "(.*)"$/)
    if (!m) break
    texto += m[1]
  }
  return texto
}
// a fala do NPC: o balão (TextDisplay) acima da cabeça dele, pedaço por pedaço (a resposta do console
// corta textos longos). Com uma cor só, o texto vem no "text" de fora; com várias, nos pedaços do "extra"
async function balao(x, z) {
  const ler = async caminho => {
    const r = await t.console(`execute in minecraft:${MUNDO} positioned ${x} 68.1 ${z} run data get entity @e[type=text_display,limit=1,sort=nearest,distance=..1] ${caminho}`)
    const m = r.match(/entity data: "(.*)"$/)
    return m ? m[1] : null
  }
  let texto = (await ler('text.text')) || ''
  for (let i = 0; i < 30; i++) {
    const pedaco = await ler(`text.extra[${i}].text`)
    if (pedaco === null) break
    texto += pedaco
  }
  return texto
}
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
  // holograma na entrada: no meio do lado norte, em cima da laje
  const [sx, sz] = [minX + 16, minZ - 1]
  t.verificar((await holograma(sx, sz)).includes('VENDA'), 'holograma "À venda" na entrada', await holograma(sx, sz))

  await tpPara(nomeA, minX + 16.5, 65, minZ + 16.5)
  await t.espera(1500)
  msgs = await t.capturar(a, () => a.chat('/terreno comprar'))
  t.verificar(msgs.some(m => m.includes('comprado')), 'comprou', msgs[0])
  t.verificar(dinheiro(await t.console(`balance ${nomeA}`)) === 150000, 'pagou R$ 50.000')
  msgs = await t.capturar(a, () => a.chat('/terreno comprar'))
  t.verificar(msgs.some(m => m.includes('é de')), 'não compra o mesmo terreno de novo')
  t.verificar((await holograma(sx, sz)).includes(nomeA), 'o holograma mostra o dono', await holograma(sx, sz))

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

  console.log('\n== Amigo só com baús ==')
  const [cx, cz] = [minX + 22, minZ + 16]
  await t.console(`execute in minecraft:${MUNDO} run setblock ${cx} 65 ${cz} minecraft:chest`)
  msgs = await t.capturar(a, () => a.chat(`/terreno amigo add ${nomeB} baus`))
  t.verificar(msgs.some(m => m.includes('agora pode baus')), 'amigo só com baús', msgs[0])
  await tpPara(nomeB, minX + 20.5, 65, minZ + 16.5)
  await t.espera(1500)
  const abriu = await new Promise(ok => {
    const fim = setTimeout(() => ok(false), 3000)
    b.once('windowOpen', w => { clearTimeout(fim); b.closeWindow(w); ok(true) })
    b.activateBlock(b.blockAt(new (require('vec3').Vec3)(cx, 65, cz))).catch(() => {})
  })
  t.verificar(abriu, 'com "baús", abre o baú')
  await tentarQuebrar(b, px, 65, pz)
  t.verificar(await blocoE(px, 65, pz, 'cobblestone'), 'com "baús", não quebra bloco')

  msgs = await t.capturar(a, () => a.chat(`/terreno amigo add ${nomeB}`))
  t.verificar(msgs.some(m => m.includes('agora pode tudo')), 'amigo com tudo')
  await tpPara(nomeB, minX + 20.5, 65, minZ + 16.5)
  await t.espera(1500)
  await tentarQuebrar(b, px, 65, pz)
  t.verificar(!(await blocoE(px, 65, pz, 'cobblestone')), 'o amigo quebra')
  await t.capturar(a, () => a.chat(`/terreno amigo remover ${nomeB}`))
  await colocarBloco(a, px, pz) // fica no terreno pro leilão
  t.verificar(await blocoE(px, 65, pz, 'cobblestone'), 'bloco de volta (vai junto no leilão)')
  // um baú com itens: o holograma do leilão mostra o que está guardado
  await t.console(`execute in minecraft:${MUNDO} run setblock ${minX + 5} 65 ${minZ + 5} minecraft:chest{Items:[{Slot:0b,id:"minecraft:diamond",count:20},{Slot:1b,id:"minecraft:iron_ingot",count:5}]}`)
  // e um baú duplo (as duas metades com itens): conta como UM baú
  await t.console(`execute in minecraft:${MUNDO} run setblock ${minX + 8} 65 ${minZ + 5} minecraft:chest[facing=south,type=left]{Items:[{Slot:0b,id:"minecraft:gold_ingot",count:3}]}`)
  await t.console(`execute in minecraft:${MUNDO} run setblock ${minX + 7} 65 ${minZ + 5} minecraft:chest[facing=south,type=right]{Items:[{Slot:0b,id:"minecraft:gold_ingot",count:2}]}`)

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
  const holoLeilao = await holograma(sx, sz)
  t.verificar(holoLeilao === '', 'o terreno em leilão fica sem holograma (quem fala dele é o Leiloeiro)', holoLeilao)

  console.log('\n== Leiloeiro ==')
  await tpPara(nomeB, sx + 0.5, 65, sz - 2.5) // na rua, de frente pro Leiloeiro (do outro lado do atril)
  await t.espera(1500)
  const leiloeiro = Object.values(b.entities).find(e => e.name === 'mannequin' && Math.abs(e.position.x - (sx + 0.5)) < 1 && Math.abs(e.position.z - (sz - 0.5)) < 1)
  t.verificar(!!leiloeiro, 'o Leiloeiro está na rua, colado na entrada do terreno em leilão')
  t.verificar(await blocoE(sx, 65, sz - 2, 'lectern'), 'com um atril na frente dele')
  const falas = []
  for (let i = 0; leiloeiro && i < 4; i++) {
    b.activateEntity(leiloeiro) // a próxima fala
    // espera ele terminar de escrever (uma letra por tick): o texto para de crescer
    let anterior = null, atual = ''
    for (let k = 0; k < 20 && (atual === '' || atual !== anterior); k++) {
      await t.espera(500)
      anterior = atual
      atual = await balao(sx + 0.5, sz - 0.5)
    }
    falas.push(atual)
  }
  console.log(falas.map((f, i) => `  fala ${i + 1}: ${f}`).join('\n'))
  t.verificar(/Dou-lhe uma/.test(falas[0] || '') && /sem lances/.test(falas[0] || ''), 'a 1ª fala anuncia o leilão sem lances', falas[0])
  t.verificar(falas[1]?.includes(nomeA), 'a 2ª fala diz de quem era', falas[1])
  t.verificar(/30 itens/.test(falas[2] || '') && /2 baús/.test(falas[2] || '') && !/iamante|erro|ouro/.test(falas[2] || ''),
    'a 3ª fala conta os 30 itens em 2 baús (o duplo conta um só), sem dizer quais', falas[2])
  t.verificar(/Lance mínimo/.test(falas[3] || ''), 'sem máquinas nem spawners, a fala deles some e vem o lance mínimo', falas[3])
  t.verificar(a.mensagens.slice(-10).some(m => m.includes('foi confiscado')), 'o dono foi avisado')

  console.log('\n== /terreno leiloes ==')
  const brutos = []
  const ouvir = j => brutos.push(JSON.stringify(j.json ?? j))
  b.on('message', ouvir)
  msgs = await t.capturar(b, () => b.chat('/terreno leiloes'), 1200)
  b.removeListener('message', ouvir)
  t.verificar(msgs.some(m => m.includes(String(id)) && m.includes('[IR]') && m.includes('[LANCE]')), 'cada leilão tem os botões [IR] e [LANCE]',
    msgs.find(m => m.includes('[IR]')))
  t.verificar(brutos.some(j => j.includes(`/terreno ir ${id}`)), 'o [IR] roda o /terreno ir do terreno')

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
  t.verificar((await holograma(sx, sz)).includes('VENDA'), 'e o holograma voltou para "À venda"')
  t.verificar(!(await blocoE(sx, 65, sz - 2, 'lectern')), 'e o atril do Leiloeiro saiu da rua')

  await t.terminar(a, b)
})
