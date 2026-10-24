// Benefícios do VIP Diamante, com um jogador novo a cada execução (o kit mensal e o bônus de
// ativação só valem uma vez por jogador): Pix com desconto, missão extra, bônus de ativação sem o
// dinheiro por nível das skills, kit mensal encantado e a venda automática da mina com o bônus de VIP.
// Uso: node teste-vip.js [mina]   (padrão: pedreira)
const t = require('./lib')

const dinheiro = r => Number((r.match(/\$([\d,.]+)/) || [, 'NaN'])[1].replace(/,/g, ''))
const diarias = msgs => {
  const inicio = msgs.findIndex(m => m.includes('Missões de hoje'))
  const fim = msgs.findIndex(m => m.includes('Missões da semana'))
  return msgs.slice(inicio, fim < 0 ? undefined : fim).filter(m => m.startsWith('• ') || m.startsWith('✔ ')).length
}

t.rodar(async () => {
  const mina = t.mina(process.argv[2] || 'pedreira')
  const nome = t.nomeNovo('Vip')
  const bot = await t.criarBot(nome)
  const recebe = await t.criarBot('BotDois')
  await t.console(`eco set ${nome} 10000`)

  console.log('\n== Sem VIP ==')
  let msgs = await t.capturar(bot, () => bot.chat('/pix pagar BotDois 1000'))
  t.verificar(msgs.some(m => m.includes('taxa: R$ 10,00')), 'Pix: taxa cheia de 1%', msgs.find(m => m.includes('taxa')))
  msgs = await t.capturar(bot, () => bot.chat('/missoes lista'))
  const diariasSemVip = diarias(msgs)
  console.log(`  missões diárias: ${diariasSemVip}`)

  console.log('\n== Ativação do VIP Diamante (30 dias) ==')
  const antes = dinheiro(await t.console(`balance ${nome}`))
  msgs = await t.capturar(bot, () => t.console(`vip add ${nome} diamante 30`), 3000)
  const depois = dinheiro(await t.console(`balance ${nome}`))
  const bonus = msgs.find(m => m.startsWith('Bônus de ativação'))
  t.verificar(!!bonus, 'mensagem do bônus de ativação', bonus)
  const bonusDinheiro = bonus ? Number(bonus.match(/R\$ ([\d.]+,\d\d)/)[1].replace(/\./g, '').replace(',', '.')) : NaN
  t.verificar(Math.abs(depois - antes - bonusDinheiro) < 0.01, 'o saldo subiu só o bônus em dinheiro (sem o R$ por nível das skills)',
    `${antes} → ${depois}`)
  t.verificar(!msgs.some(m => m.includes('SKILL UP')), 'sem uma mensagem por nível', msgs.find(m => m.includes('subiram')))

  console.log('\n== Perks do VIP Diamante ==')
  msgs = await t.capturar(bot, () => bot.chat('/pix pagar BotDois 1000'))
  t.verificar(msgs.some(m => m.includes('taxa: R$ 2,00')), 'Pix: 80% de desconto na taxa', msgs.find(m => m.includes('taxa')))
  msgs = await t.capturar(bot, () => bot.chat('/missoes lista'))
  t.verificar(diarias(msgs) === diariasSemVip + 1, 'uma missão diária a mais', `${diariasSemVip} → ${diarias(msgs)}`)

  console.log('\n== Kit mensal ==')
  await t.console(`minecraft:clear ${nome}`)
  msgs = await t.capturar(bot, () => bot.chat('/vip kitmensal'), 2500)
  t.verificar(msgs.some(m => m.includes('Kit mensal')), 'kit entregue', `${bot.inventory.items().length} itens`)
  // o Mineflayer não lê os encantamentos nessa versão: pergunta ao servidor
  const espada = await t.console(`minecraft:data get entity ${nome} Inventory[{id:"minecraft:netherite_sword"}].components."minecraft:enchantments"`)
  t.verificar(/sharpness": 7/.test(espada), 'espada com Afiação VII')
  msgs = await t.capturar(bot, () => bot.chat('/vip kitmensal'))
  t.verificar(msgs.some(m => m.includes('cooldown')), 'segundo pedido recusado pelo cooldown')

  console.log('\n== Venda automática ==')
  await t.console(`minecraft:clear ${nome}`)
  await t.console(`gamemode survival ${nome}`)
  await t.console(`minecraft:give ${nome} diamond_pickaxe 1`)
  await t.irParaMina(nome, mina)
  const antesVenda = dinheiro(await t.console(`balance ${nome}`))
  msgs = await t.capturar(bot, () => t.minerar(bot, mina, 6))
  const depoisVenda = dinheiro(await t.console(`balance ${nome}`))
  t.verificar(msgs.some(m => m.includes('+R$')), 'vendeu na hora (barra)', msgs.find(m => m.includes('+R$')))
  t.verificar(depoisVenda > antesVenda, 'o saldo subiu', `${antesVenda} → ${depoisVenda}`)
  t.verificar(Object.keys(t.inventario(bot)).length === 1 && t.itensNoChao(bot).length === 0, 'nada no inventário nem no chão',
    JSON.stringify(t.inventario(bot)))

  await t.console(`vip remove ${nome}`)
  await t.console(`minecraft:clear ${nome}`)
  await t.terminar(bot, recebe)
})
