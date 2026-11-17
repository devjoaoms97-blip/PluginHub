// Fazendas, fase 3: o balcão do Corretor Rural na Loja Oficial (o salão cresceu e a fonte foi pro fundo
// novo, sem mexer nos outros balcões), o Leiloeiro contando as fazendas do terreno em leilão, a fazenda
// parada pra todo mundo com o terreno em leilão, passando pra quem arrematou (com o cofre) e desfeita
// quando o terreno volta à venda (abandonado: o TerrenosPlugin apaga tudo o que tinha dentro).
// Uso: node teste-fazendas-loja.js (reconstrói a Loja Oficial no spawn: uns 30 s)
const t = require('./lib')
const { Vec3 } = require('vec3')

const CELULA = 37
const OZ = 43 // o centro do spawn em z
const CORRETOR = new Vec3(138.5, 65, OZ - 14 + 0.5) // balcão das fazendas, lado norte, x 134..143
const MAQUINISTA = new Vec3(112.5, 65, OZ + 14.5)
const dinheiro = r => Number((r.match(/\$([\d,.]+)/) || [, 'NaN'])[1].replace(/,/g, ''))

/** Espera a próxima janela abrir (com os itens) depois de rodar fn. */
const janela = (bot, fn) => new Promise(ok => {
  const fim = setTimeout(() => ok(null), 3000)
  bot.once('windowOpen', j => { clearTimeout(fim); setTimeout(() => ok(j), 800) })
  fn()
})

/** O balão (TextDisplay) acima da cabeça do Leiloeiro, pedaço por pedaço. */
async function balao(x, z) {
  const ler = async caminho => {
    const r = await t.console(`execute in minecraft:terrenos positioned ${x} 68.1 ${z} run data get entity @e[type=text_display,limit=1,sort=nearest,distance=..1] ${caminho}`)
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

t.rodar(async () => {
  console.log('\n== Loja Oficial reconstruída ==')
  console.log('  ' + await t.console(`estrutura construir loja spawn 0 65 ${OZ}`))
  for (let i = 0; i < 120; i++) {
    await t.espera(1000)
    if (/Nenhuma/.test(await t.console('estrutura status'))) break
  }
  const a = await t.criarBot(t.nomeNovo('Lj'))
  const nomeA = a.username
  await t.espera(1500)
  await t.console(`eco set ${nomeA} 1000000`)
  await t.console(`minecraft:clear ${nomeA}`)
  await t.console(`ranks admin set ${nomeA} fazendeiro`)
  await t.console(`execute in minecraft:spawn run minecraft:tp ${nomeA} 138.5 65 ${OZ - 10.5} 180 0`)
  await t.espera(2000)
  const corretor = Object.values(a.entities).find(e => e.name === 'mannequin' && e.position.distanceTo(CORRETOR) < 1)
  t.verificar(!!corretor, 'o Corretor Rural está no balcão novo (norte, x 134..143)')
  let j = corretor && await janela(a, () => a.activateEntity(corretor))
  t.verificar(!!j && j.slots[11]?.name === 'egg', 'o Corretor Rural abre a loja das fazendas', j ? j.slots.slice(10, 16).map(s => s?.name).join(',') : 'não abriu')
  if (j) {
    await a.clickWindow(10, 0, 0) // a Horta
    await t.espera(1200)
    a.closeWindow(a.currentWindow)
  }
  const temEscritura = (await t.console(`data get entity ${nomeA} Inventory[0].components."minecraft:custom_data".PublicBukkitValues."fazendasplugin:escritura"`)).includes('"horta"')
  t.verificar(temEscritura, 'comprou a Escritura da Horta no Corretor')
  const blocoSpawn = async (x, y, z, b) => (await t.console(`execute in minecraft:spawn if block ${x} ${y} ${z} minecraft:${b}`)).includes('passed')
  t.verificar(await blocoSpawn(146, 64, OZ, 'water') && !(await blocoSpawn(133, 64, OZ, 'water')), 'a fonte foi pro fundo novo (x 144..148) e saiu do lugar antigo')
  await t.console(`execute in minecraft:spawn run minecraft:tp ${nomeA} 112.5 65 ${OZ + 10.5} 0 0`)
  await t.espera(1500)
  t.verificar(!!Object.values(a.entities).find(e => e.name === 'mannequin' && e.position.distanceTo(MAQUINISTA) < 1), 'os balcões antigos não mudaram de lugar (o Maquinista continua no dele)')

  console.log('\n== Fazenda no terreno ==')
  let msgs = await t.capturar(a, () => a.chat('/terreno auto'), 3000)
  const [gx, gz] = ((msgs.join(' ').match(/terreno (-?\d+;-?\d+)/) || [])[1] || '0;0').split(';').map(Number)
  const id = `${gx};${gz}`
  const mx = gx * CELULA, mz = gz * CELULA
  const tpA = async (x, z) => { await t.console(`execute in minecraft:terrenos run minecraft:tp ${nomeA} ${x} 65 ${z} 0 30`); await t.espera(1200) }
  await tpA(mx + 16.5, mz + 12.5)
  msgs = await t.capturar(a, () => a.chat('/terreno comprar'), 1500)
  t.verificar(msgs.some(m => m.includes('comprado')), 'comprou um terreno', msgs[0])
  const [cx, cz] = [mx + 16, mz + 16]
  await tpA(cx + 0.5, cz - 1.5)
  await a.equip(a.inventory.items().find(i => i.name === 'paper'), 'hand')
  await t.espera(300)
  const chao = a.blockAt(new Vec3(cx, 64, cz))
  await a.activateBlock(chao, new Vec3(0, 1, 0)).catch(() => {})
  await t.espera(800)
  msgs = await t.capturar(a, () => a.activateBlock(chao, new Vec3(0, 1, 0)).catch(() => {}), 1500)
  t.verificar(msgs.some(m => m.includes('construída')), 'a Horta foi construída', msgs[0])
  await t.console(`fazendas dar ${nomeA} adubo 20`)
  await tpA(cx + 0.5, cz - 4.5)
  const npc = () => Object.values(a.entities).find(e => e.name === 'mannequin' && e.position.distanceTo(new Vec3(cx + 0.5, 65, cz - 2.5)) < 1.5)
  await a.equip(a.inventory.items().find(i => i.name === 'paper'), 'hand')
  await t.capturar(a, () => a.activateEntity(npc()), 1000) // abastece com o adubo na mão
  await t.console('fazendas acelerar 20')

  console.log('\n== Leilão ==')
  await t.console(`terreno admin leiloar ${id}`)
  await t.espera(1500)
  msgs = await t.capturar(a, () => a.activateEntity(npc()), 1000)
  t.verificar(msgs.some(m => m.includes('em leilão')), 'com o terreno em leilão, a fazenda fica parada (nem o antigo dono mexe)', msgs.find(m => m.includes('leilão')))
  const sx = mx + 16, sz = mz - 1 // a entrada, no meio do lado norte
  await tpA(sx + 0.5, sz - 2.5)
  await t.espera(1500)
  const leiloeiro = Object.values(a.entities).find(e => e.name === 'mannequin' && Math.abs(e.position.x - (sx + 0.5)) < 1 && Math.abs(e.position.z - (sz - 0.5)) < 1)
  t.verificar(!!leiloeiro, 'o Leiloeiro está na entrada do terreno')
  const falas = []
  for (let i = 0; leiloeiro && i < 5; i++) {
    a.activateEntity(leiloeiro)
    let anterior = null, atual = ''
    for (let k = 0; k < 20 && (atual === '' || atual !== anterior); k++) {
      await t.espera(500)
      anterior = atual
      atual = await balao(sx + 0.5, sz - 0.5)
    }
    falas.push(atual)
  }
  console.log(falas.map((f, i) => `  fala ${i + 1}: ${f}`).join('\n'))
  t.verificar(falas.some(f => /1 fazenda\b/.test(f) && /pronta pra trabalhar/.test(f)), 'o Leiloeiro conta a fazenda do terreno', falas.find(f => /fazenda/.test(f)))

  console.log('\n== Quem arremata leva a fazenda ==')
  const b = await t.criarBot(t.nomeNovo('Nd'))
  const nomeB = b.username
  await t.espera(1500)
  await t.console(`eco set ${nomeB} 200000`)
  msgs = await t.capturar(b, () => b.chat(`/terreno lance ${id} 60000`), 1500)
  t.verificar(msgs.some(m => /lance/i.test(m)), 'o outro jogador deu um lance', msgs[0])
  await t.console(`terreno admin encerrar ${id}`)
  // o leilão fecha na próxima verificação do TerrenosPlugin (até 30 s) e a fazenda passa no tick dela (20 s)
  let passou = false
  for (let s = 0; s < 70 && !passou; s++) {
    await t.espera(1000)
    if (s % 5 === 4) passou = (await t.console('fazendas lista')).includes(`dono ${nomeB}`)
  }
  t.verificar(passou, 'a fazenda passou pra quem arrematou o terreno')
  t.verificar(b.mensagens.some(m => m.includes('agora é sua')), 'e ele foi avisado', b.mensagens.find(m => m.includes('agora é sua')))
  await t.console(`execute in minecraft:terrenos run minecraft:tp ${nomeB} ${cx + 0.5} 65 ${cz - 4.5} 0 30`)
  await t.espera(1500)
  const npcB = Object.values(b.entities).find(e => e.name === 'mannequin' && e.position.distanceTo(new Vec3(cx + 0.5, 65, cz - 2.5)) < 1.5)
  const antes = dinheiro(await t.console(`balance ${nomeB}`))
  j = npcB && await janela(b, () => b.activateEntity(npcB))
  if (j) {
    await b.clickWindow(10, 0, 0) // coletar
    await t.espera(1200)
    b.closeWindow(b.currentWindow)
  }
  const recebeu = dinheiro(await t.console(`balance ${nomeB}`)) - antes
  t.verificar(!!j && recebeu > 0, 'com o cofre que tinha: o novo dono abre e coleta', `+${recebeu.toFixed(2)}`)
  await tpA(cx + 0.5, cz - 4.5)
  msgs = await t.capturar(a, () => a.activateEntity(npc()), 1000)
  t.verificar(msgs.some(m => m.includes(`é de ${nomeB}`)), 'o antigo dono não abre mais', msgs[0])

  console.log('\n== Terreno abandonado ==')
  await t.capturar(b, () => b.chat(`/terreno abandonar ${id}`), 1000)
  msgs = await t.capturar(b, () => b.chat(`/terreno abandonar confirmar`), 1500)
  t.verificar(msgs.some(m => m.includes('abandonado')), 'o novo dono abandonou o terreno', msgs.find(m => m.includes('bandon')))
  let sumiu = false
  for (let s = 0; s < 30 && !sumiu; s++) {
    await t.espera(1000)
    if (s % 5 === 4) sumiu = !(await t.console('fazendas lista')).includes(`em terrenos ${cx} 65 ${cz} `)
  }
  t.verificar(sumiu, 'com o terreno de volta à venda, a fazenda é desfeita junto')
  const npcs = await t.console(`execute in minecraft:terrenos positioned ${cx + 0.5} 65 ${cz + 0.5} if entity @e[type=mannequin,distance=..5]`)
  t.verificar(!npcs.includes('passed'), 'e o fazendeiro sumiu', npcs)
  await t.terminar(a, b)
})
