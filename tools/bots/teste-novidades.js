// Novidades do bloco 🟢 do roadmap: /skills top e XP em dobro, eventos de mercado e categorias da
// loja, chave Pix + extrato da staff + taxa no extrato, cheque nominal/limite/anti-cópia,
// faixa de preço do /vip vender e warp por tier, /boss proximo, ItemCleaner com orbes e raio.
// Uso: node teste-novidades.js
const t = require('./lib')

t.rodar(async () => {
  const a = await t.criarBot(t.nomeNovo('Nov'))
  const b = await t.criarBot(t.nomeNovo('Nov'))
  await t.espera(1000)

  console.log('\n== Skills ==')
  let msgs = await t.capturar(a, () => a.chat('/skills top'), 2500)
  t.verificar(msgs.some(m => m.includes('Top Skills')), '/skills top (soma dos níveis)', msgs[0])
  msgs = await t.capturar(a, () => a.chat('/skills top espadas'), 2500)
  t.verificar(msgs.some(m => m.includes('Top Espadas')), '/skills top espadas', msgs.slice(0, 2).join(' | '))
  await t.console('skills admin xpdobro 2 0.05')
  const xpAntes = await t.console(`skills admin setlevel ${a.username} espadas 1`)
  msgs = await t.capturar(a, async () => { await t.console('skills admin xpdobro parar') }, 800)
  t.verificar(!!xpAntes, 'xp em dobro liga e desliga pela staff')

  console.log('\n== Loja ==')
  msgs = await t.capturar(a, async () => { await t.console('shop admin evento diamond 2 0.1') }, 1000)
  t.verificar(msgs.some(m => m.includes('ALTA') && m.includes('DIAMANTE')), 'evento de mercado anunciado no chat', msgs.join(' | ').slice(0, 140))
  msgs = await t.capturar(a, () => a.chat('/shop evento'))
  t.verificar(msgs.some(m => m.includes('Em alta') && m.includes('diamante')), '/shop evento mostra o item em alta', msgs[0])
  await t.console(`minecraft:give ${a.username} diamond 1`)
  await t.console(`eco set ${a.username} 0`)
  // o /sell é da staff agora (os jogadores vendem nos vendedores da Loja Oficial)
  await t.console(`lp user ${a.username} permission set shop.comandos true`)
  msgs = await t.capturar(a, () => a.chat('/sell all'), 1500)
  await t.console(`lp user ${a.username} permission unset shop.comandos`)
  const comEvento = parseFloat((await t.console(`balance ${a.username}`)).replace(/[^\d,]/g, '').replace(',', '.'))
  await t.console('shop admin evento parar')
  t.verificar(comEvento > 0, 'venda com o evento ligado', `${msgs.join(' ').slice(0, 100)} saldo=${comEvento}`)

  console.log('\n== Pix ==')
  await t.console(`eco set ${a.username} 100000`)
  const chave = 'chave' + a.username.slice(-4).toLowerCase()
  msgs = await t.capturar(b, () => b.chat(`/pix chave ${chave}`))
  t.verificar(msgs.some(m => m.includes('Sua chave Pix agora')), '/pix chave cadastra o apelido', msgs[0])
  msgs = await t.capturar(a, () => a.chat(`/pix chave ${b.username}`))
  t.verificar(msgs.some(m => m.includes('nome de outro jogador')), 'chave não pode ser o nome de outro jogador', msgs[0])
  let recebidas = []
  const ouvir = (m) => recebidas.push(m.toString())
  b.on('title', ouvir)
  msgs = await t.capturar(a, () => a.chat(`/pix pagar ${chave} 1000`), 1200)
  b.off('title', ouvir)
  t.verificar(msgs.some(m => m.includes('Você pagou') && m.includes(b.username)), 'pagar pela chave Pix', msgs[0])
  t.verificar(recebidas.length > 0, 'título na tela de quem recebe o Pix', JSON.stringify(recebidas).slice(0, 80))
  msgs = await t.capturar(b, () => b.chat('/pix historico'))
  t.verificar(msgs.some(m => m.includes('taxa')), 'taxa aparece no extrato', msgs.slice(1, 2).join(''))
  await t.console(`op ${a.username}`)
  msgs = await t.capturar(a, () => a.chat(`/pix admin log ${b.username}`))
  t.verificar(msgs.some(m => m.includes('Extrato de')), '/pix admin log <jogador>', msgs[0])

  console.log('\n== Cheque ==')
  await t.console(`minecraft:item replace entity ${a.username} weapon.mainhand with minecraft:paper 2`)
  await t.espera(500)
  msgs = await t.capturar(a, () => a.chat(`/cheque 500 ${b.username}`))
  t.verificar(msgs.some(m => m.includes('nominal')), 'cheque nominal', msgs[0])
  msgs = await t.capturar(a, () => a.chat('/cheque 500'))
  t.verificar(msgs.some(m => m.includes('Espere')), 'cooldown entre cheques', msgs[0])

  console.log('\n== VIP ==')
  msgs = await t.capturar(a, () => a.chat('/vip setwarp diamante'))
  t.verificar(msgs.some(m => m.includes('marcado aqui')), '/vip setwarp diamante', msgs[0])
  const codigo = (await t.console('vip gerarcodigo bronze')).match(/VIP-([A-Z0-9-]+)/)?.[1]
  msgs = await t.capturar(a, () => a.chat(`/vip vender 1 ${codigo}`))
  t.verificar(msgs.some(m => m.includes('preço mínimo')), 'preço mínimo na revenda', `${codigo} ${msgs[0]}`)
  if (codigo) await t.console(`vip revogarcodigo ${codigo}`)

  console.log('\n== Boss ==')
  msgs = await t.capturar(b, () => b.chat('/boss proximo'))
  t.verificar(msgs.length > 0 && !msgs.some(m => m.includes('permissão')), '/boss proximo aberto a todos', msgs[0])

  console.log('\n== ItemCleaner ==')
  // item longe de todo mundo some; item do lado do jogador fica
  const p = a.entity.position
  await t.console(`execute in minecraft:spawn run summon minecraft:item ${Math.floor(p.x)} ${Math.floor(p.y)} ${Math.floor(p.z)} {Item:{id:"minecraft:stick",count:1},Age:-32768s,PickupDelay:32767s,Tags:["teste_cleaner"]}`)
  await t.console(`execute in minecraft:spawn run summon minecraft:experience_orb ${Math.floor(p.x) + 40} ${Math.floor(p.y)} ${Math.floor(p.z)} {Value:5,Tags:["teste_cleaner"]}`)
  await t.espera(11000) // idade mínima
  const res = await t.console('limparchao agora')
  const ficou = await t.console('execute if entity @e[type=item,tag=teste_cleaner]')
  const orbe = await t.console('execute if entity @e[type=experience_orb,tag=teste_cleaner]')
  t.verificar(ficou.includes('passed') && !orbe.includes('passed'), 'item perto do jogador fica, orbe de XP longe some', `${res} | ${ficou} | ${orbe}`)
  await t.console('minecraft:kill @e[tag=teste_cleaner]')

  await t.console(`deop ${a.username}`)
  b.quit()
  await t.terminar(a)
})
