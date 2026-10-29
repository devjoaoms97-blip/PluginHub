// Mapas do tesouro (TesourosPlugin): cavar fora do X não faz nada; cavar no X com o mapa abre a câmara
// e puxa o convidado que está perto; quem não pode entrar é barrado no buraco; o baú fica trancado
// até o último guardião morrer e só o dono abre; /tesouro sair fecha a câmara e devolve o terreno;
// quem morre na câmara perde tudo; o boss deixa um mapa no chão. Precisa do mundo tesouros
// (configurar-mundos.ps1).
// Uso: node teste-tesouros.js
const t = require('./lib')
const { Vec3 } = require('vec3')

const W = 'execute in minecraft:tesouros run'
const SUP = -20 // o chão do superplano
const blocoE = async (x, y, z, bloco) => (await t.console(`${W} execute if block ${x} ${y} ${z} minecraft:${bloco}`)).includes('passed')
const mapas = bot => bot.inventory.items().filter(i => i.name === 'paper').length

/** Mata os guardiões da câmara (pelo console) até o baú destravar. */
async function vencerOndas(bot, x, z) {
  for (let s = 0; s < 90; s++) {
    if (bot.mensagens.some(m => m.includes('baú destravou'))) return true
    await t.console(`${W} minecraft:kill @e[type=!player,type=!item,x=${x - 9},y=${SUP - 30},z=${z - 9},dx=18,dy=20,dz=18]`)
    await t.espera(1500)
  }
  return false
}

t.rodar(async () => {
  const dono = await t.criarBot(t.nomeNovo('Tsd'))
  const amigo = await t.criarBot(t.nomeNovo('Tsa'))
  const estranho = await t.criarBot(t.nomeNovo('Tse'))
  for (const b of [dono, amigo, estranho]) {
    await t.console(`gamemode survival ${b.username}`)
    await t.console(`clear ${b.username}`)
    // os bots não lutam: Resistência V pra os guardiões não matarem ninguém antes da hora (o kill ignora)
    await t.console(`effect give ${b.username} minecraft:resistance infinite 4 true`)
  }

  console.log('\n== /tesouro ir ==')
  let msgs = await t.capturar(dono, () => dono.chat('/tesouro ir'), 3000)
  t.verificar(msgs.some(m => m.includes('mundo dos tesouros')), 'foi pro mundo dos tesouros')
  t.verificar(Math.round(dono.entity.position.y) === SUP + 1, 'de pé no chão do superplano', `${dono.entity.position}`)
  const borda = await t.console(`${W} worldborder get`)
  t.verificar(borda.includes('3200'), 'borda de 3.200 blocos (raio máximo + 100)', borda)

  const [X, Z] = [400, 37]
  await t.console(`tesouro dar ${dono.username} comum ${X} ${Z}`)
  await t.espera(500)
  t.verificar(mapas(dono) === 1, 'staff deu o mapa')
  dono.setQuickBarSlot(dono.inventory.items().find(i => i.name === 'paper').slot - 36)
  await t.console(`give ${dono.username} diamond 3`)

  for (const [b, dx] of [[dono, 1], [amigo, 2], [estranho, 12]]) {
    await t.console(`${W} minecraft:tp ${b.username} ${X + dx + 0.5} ${SUP + 1} ${Z + 0.5}`)
  }
  await t.espera(2000)
  msgs = await t.capturar(dono, async () => {}, 1500)
  t.verificar(msgs.some(m => m.includes('faltam 1 blocos')), 'com o mapa na mão, a barra mostra quanto falta', msgs.find(m => m.includes('[barra]')))

  console.log('\n== Cavar fora do X ==')
  const lado = dono.blockAt(new Vec3(X + 1, SUP, Z + 1))
  msgs = await t.capturar(dono, () => dono.dig(lado, true).catch(() => {}), 1500)
  t.verificar(await blocoE(X + 1, SUP, Z + 1, 'grass_block'), 'o bloco fora do X continua lá')

  console.log('\n== Cavar no X ==')
  await t.capturar(dono, () => dono.chat(`/tesouro convidar ${amigo.username}`), 800)
  msgs = await t.capturar(dono, () => dono.dig(dono.blockAt(new Vec3(X, SUP, Z)), true).catch(() => {}), 2500)
  t.verificar(msgs.some(m => m.includes('achou um tesouro')), 'abriu a câmara', msgs.find(m => m.includes('tesouro')))
  t.verificar(mapas(dono) === 0, 'o mapa foi gasto')
  t.verificar(dono.entity.position.y < SUP - 10, 'o dono desceu pra câmara', `${dono.entity.position}`)
  t.verificar(amigo.entity.position.y < SUP - 10, 'o convidado perto desceu junto', `${amigo.entity.position}`)
  t.verificar(estranho.entity.position.y > SUP, 'o estranho longe ficou em cima', `${estranho.entity.position}`)
  t.verificar(await blocoE(X, SUP - 18, Z, 'chest'), 'o baú está no centro da câmara')

  console.log('\n== Estranho no buraco ==')
  msgs = await t.capturar(estranho, () => t.console(`${W} minecraft:tp ${estranho.username} ${X + 0.5} ${SUP + 1.5} ${Z + 0.5}`), 2500)
  t.verificar(estranho.entity.position.y > SUP - 2, 'o estranho não entra pelo buraco', `${estranho.entity.position}`)
  t.verificar(msgs.some(m => m.includes('Só o clã e os convidados')), 'e é avisado')

  console.log('\n== Baú trancado e ondas ==')
  // a chegada fica longe do baú: chega perto (teleporte dentro da mesma câmara pode)
  await t.console(`${W} minecraft:tp ${dono.username} ${X + 0.5} ${SUP - 18} ${Z - 1.5}`)
  await t.console(`${W} minecraft:tp ${amigo.username} ${X + 1.5} ${SUP - 18} ${Z - 1.5}`)
  await t.espera(1000)
  const bau = () => dono.blockAt(new Vec3(X, SUP - 18, Z))
  msgs = await t.capturar(dono, () => dono.activateBlock(bau()), 1500)
  t.verificar(msgs.some(m => m.includes('Trancado')), 'baú trancado com guardiões vivos')
  msgs = await t.capturar(dono, () => t.console(`${W} minecraft:tp ${dono.username} 0 ${SUP + 1} 0`), 1500)
  t.verificar(dono.entity.position.y < SUP - 10, 'não dá pra fugir por teleporte durante a luta', `${dono.entity.position}`)
  t.verificar(msgs.some(m => m.includes('Não dá pra fugir')), 'e é avisado')
  t.verificar(await vencerOndas(dono, X, Z), 'as ondas acabaram e o baú destravou')

  console.log('\n== Abrir o baú ==')
  msgs = await t.capturar(amigo, () => amigo.activateBlock(amigo.blockAt(new Vec3(X, SUP - 18, Z))), 1500)
  t.verificar(msgs.some(m => m.includes('O tesouro é de')), 'o convidado não abre o baú')
  const janela = await dono.openContainer(bau())
  const noBau = janela.containerItems()
  t.verificar(noBau.length > 0, 'o baú tem itens', noBau.map(i => i.name + 'x' + i.count).join(', '))
  if (noBau.length) await dono.clickWindow(noBau[0].slot, 0, 1) // tira uma pilha (shift)
  dono.closeWindow(janela)
  await t.espera(800)
  t.verificar(dono.mensagens.some(m => m.includes('O baú tinha')), 'as coins foram pro dono')

  console.log('\n== Sair ==')
  msgs = await t.capturar(amigo, () => amigo.chat('/tesouro sair'), 1500)
  t.verificar(amigo.entity.position.y > SUP, 'o convidado saiu sozinho', `${amigo.entity.position}`)
  const antes = Object.values(t.inventario(dono)).reduce((a, b) => a + b, 0)
  msgs = await t.capturar(dono, () => dono.chat('/tesouro sair'), 2000)
  t.verificar(dono.entity.position.y > SUP, 'o dono voltou pra superfície', `${dono.entity.position}`)
  t.verificar(Object.values(t.inventario(dono)).reduce((a, b) => a + b, 0) >= antes, 'o resto do baú foi pro inventário', JSON.stringify(t.inventario(dono)))
  t.verificar(await blocoE(X, SUP - 18, Z, 'stone') && await blocoE(X, SUP, Z, 'grass_block'), 'a câmara virou pedra e o buraco fechou')

  console.log('\n== Morrer na câmara ==')
  const [X2, Z2] = [-500, 120]
  await t.console(`tesouro dar ${dono.username} raro ${X2} ${Z2}`)
  await t.console(`${W} minecraft:tp ${dono.username} ${X2 + 1.5} ${SUP + 1} ${Z2 + 0.5}`)
  await t.console(`${W} minecraft:tp ${amigo.username} ${X2 + 3.5} ${SUP + 1} ${Z2 + 0.5}`)
  await t.espera(2000)
  await t.capturar(dono, () => dono.dig(dono.blockAt(new Vec3(X2, SUP, Z2)), true).catch(() => {}), 2500)
  t.verificar(dono.entity.position.y < SUP - 10, 'abriu a segunda câmara')
  t.verificar((t.inventario(dono).diamond || 0) > 0, 'o dono tem diamantes antes de morrer')
  msgs = await t.capturar(amigo, () => t.console(`minecraft:kill ${dono.username}`), 2500)
  t.verificar(msgs.some(m => m.includes('tesouro se perdeu')), 'o convidado vê a câmara desabar')
  t.verificar(amigo.entity.position.y > SUP, 'o convidado vivo voltou pra superfície')
  dono.respawn?.()
  await t.espera(3000)
  t.verificar(!(t.inventario(dono).diamond > 0), 'o dono perdeu os itens', JSON.stringify(t.inventario(dono)))
  t.verificar(await blocoE(X2, SUP - 18, Z2, 'stone'), 'a segunda câmara foi desfeita')
  const itens = await t.console(`${W} execute if entity @e[type=item,x=${X2 - 10},y=${SUP - 25},z=${Z2 - 10},dx=20,dy=30,dz=20]`)
  t.verificar(!itens.includes('passed'), 'nada ficou no chão da câmara', itens)

  console.log('\n== Spawner, máquina e células no baú (loot comum trocado por um instante) ==')
  const fs = require('fs'), path = require('path')
  const lootYml = path.join(process.env.MC_TESTE || 'C:\\mc-teste', 'plugins', 'TesourosPlugin', 'loot.yml')
  const lootOriginal = fs.readFileSync(lootYml, 'utf8')
  try {
    fs.writeFileSync(lootYml, 'comum:\n  coins: [5000, 5000]\n  rolagens: 4\n  itens:\n'
      + '    a: {item: "spawner:zumbi", qtd: [1, 1], peso: 1}\n    b: {item: "maquina:tear", peso: 1}\n'
      + '    c: {item: combustivel, qtd: [100, 100], peso: 1}\n')
    await t.console('tesouro reload')
    const [X3, Z3] = [700, -300]
    await t.console(`tesouro dar ${dono.username} comum ${X3} ${Z3}`)
    await t.console(`${W} minecraft:tp ${dono.username} ${X3 + 1.5} ${SUP + 1} ${Z3 + 0.5}`)
    await t.espera(2000)
    await t.capturar(dono, () => dono.dig(dono.blockAt(new Vec3(X3, SUP, Z3)), true).catch(() => {}), 2500)
    await t.console(`${W} minecraft:tp ${dono.username} ${X3 + 0.5} ${SUP - 18} ${Z3 - 1.5}`)
    const inicio = dono.mensagens.length
    for (let s = 0; s < 90 && !dono.mensagens.slice(inicio).some(m => m.includes('baú destravou')); s++) {
      await t.console(`${W} minecraft:kill @e[type=!player,type=!item,x=${X3 - 9},y=${SUP - 30},z=${Z3 - 9},dx=18,dy=20,dz=18]`)
      await t.espera(1500)
    }
    const j3 = await dono.openContainer(dono.blockAt(new Vec3(X3, SUP - 18, Z3)))
    const nomes = j3.containerItems().map(i => i.name + 'x' + i.count)
    dono.closeWindow(j3)
    t.verificar(nomes.includes('spawnerx1') && nomes.includes('barrierx1') && nomes.includes('paperx64') && nomes.includes('paperx36'),
      'o baú trouxe o spawner, a máquina e as 100 células (em pilhas de 64), sem repetir', nomes.join(', '))
    await t.capturar(dono, () => dono.chat('/tesouro sair'), 2000)
  } finally {
    fs.writeFileSync(lootYml, lootOriginal)
    await t.console('tesouro reload')
  }

  console.log('\n== Boss deixa um mapa ==')
  // com o chunk carregado: o BossPlugin tira o "boss" que aparece quando o chunk carrega
  await t.console(`${W} minecraft:tp ${estranho.username} 3.5 ${SUP + 1} 30.5`)
  await t.espera(2000)
  await t.console(`${W} summon zombie 0 ${SUP + 1} 30 {NoAI:1b,BukkitValues:{"pluginhub:boss_mob":1b}}`)
  await t.console(`${W} minecraft:kill @e[type=zombie,x=0,y=${SUP + 1},z=30,distance=..2]`)
  await t.espera(1000)
  const mapaNoChao = await t.console(`${W} execute if entity @e[type=item,nbt={Item:{id:"minecraft:paper"}},x=0,y=${SUP + 1},z=30,distance=..4]`)
  t.verificar(mapaNoChao.includes('passed'), 'o mapa ficou no chão onde o boss morreu', mapaNoChao)
  await t.console(`${W} minecraft:kill @e[type=item,x=0,y=${SUP + 1},z=30,distance=..4]`)

  console.log('\n== Mapa minerando (chance em 100% por um instante) ==')
  const cfg = path.join(process.env.MC_TESTE || 'C:\\mc-teste', 'plugins', 'TesourosPlugin', 'config.yml')
  const original = fs.readFileSync(cfg, 'utf8')
  try {
    fs.writeFileSync(cfg, original.replace(/chance-por-bloco: [\d.]+/, 'chance-por-bloco: 1'))
    await t.console('tesouro reload')
    const mina = t.mina('pedreira')
    await t.console(`minecraft:clear ${amigo.username}`)
    await t.console(`minecraft:give ${amigo.username} diamond_pickaxe 1`)
    await t.irParaMina(amigo.username, mina)
    msgs = await t.capturar(amigo, () => t.minerar(amigo, mina, 1), 1500)
    t.verificar(msgs.some(m => m.includes('Você achou um Mapa do Tesouro')), 'minerar deu um mapa', msgs.find(m => m.includes('Mapa')))
    t.verificar(mapas(amigo) === 1, 'o mapa foi direto pro inventário')
  } finally {
    fs.writeFileSync(cfg, original)
    await t.console('tesouro reload')
  }

  await t.terminar(dono, amigo, estranho)
})
