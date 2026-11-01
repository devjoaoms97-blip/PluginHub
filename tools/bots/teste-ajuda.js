// AjudaPlugin: o convite do /tutorial ao entrar, o /comandos (índice e páginas), o tutorial inteiro
// (minerar na mina, /warp loja, /ranks, /terreno auto, /diaria) com o Mapa do Tesouro Comum no fim,
// guardado se o inventário estiver cheio e entregue uma vez só, e o /tpa com os botões de aceitar.
// Uso: node teste-ajuda.js
const t = require('./lib')

t.rodar(async () => {
  const nome = t.nomeNovo('Aju')
  // os comandos de clique vêm em JSON: guarda o bruto pra achar o "/tpaccept <nome>" do botão
  const bot = await t.criarBot(nome)
  const brutos = []
  bot.on('message', j => brutos.push(JSON.stringify(j.json ?? j)))

  console.log('\n== Convite ao entrar ==')
  await t.espera(9000)
  t.verificar(bot.mensagens.some(m => m.includes('Faça o tutorial') && m.includes('[COMEÇAR]')), 'quem nunca fez recebe o convite com [COMEÇAR]')

  console.log('\n== /comandos ==')
  let msgs = await t.capturar(bot, () => bot.chat('/comandos'), 1000)
  t.verificar(msgs.some(m => m.includes('Comandos do servidor')) && msgs.some(m => m.includes('[Amigos]')), 'o índice lista os temas', msgs.slice(0, 3).join(' | '))
  msgs = await t.capturar(bot, () => bot.chat('/comandos amigo'), 1000)
  t.verificar(msgs.some(m => m.includes('/terreno amigo add <jogador>')), 'a página "amigo" explica o /terreno amigo', msgs.find(m => m.includes('amigo add')))
  msgs = await t.capturar(bot, () => bot.chat('/comando terreno'), 1000)
  t.verificar(msgs.some(m => m.includes('/terreno auto')), 'o /comando terreno (apelido) abre a página do terreno')
  msgs = await t.capturar(bot, () => bot.chat('/comandos xyz'), 1000)
  t.verificar(msgs.some(m => m.includes('Não tem página')), 'tema que não existe avisa e mostra o índice')

  console.log('\n== Tutorial ==')
  await t.console(`minecraft:clear ${nome}`)
  msgs = await t.capturar(bot, () => bot.chat('/tutorial'), 1000)
  t.verificar(msgs.some(m => m.includes('Tutorial começado')) && msgs.some(m => m.includes('(1/5)') && m.includes('Minere na mina')),
    '/tutorial começa na etapa 1 (minerar)', msgs.find(m => m.includes('(1/5)')))
  // minerar fora da mina não conta
  // o /warp tem 3 s de espera: aguarda chegar antes de ir pra mina
  msgs = await t.capturar(bot, () => bot.chat('/warp loja'), 4500)
  t.verificar(!msgs.some(m => m.includes('Conheça a Loja') && m.includes('(2/5)')), 'pular etapa não vale: /warp loja antes de minerar não conta')
  const mina = t.mina('pedreira')
  await t.console(`minecraft:give ${nome} diamond_pickaxe 1`)
  await t.irParaMina(nome, mina)
  // o bot só alcança uns 20 blocos de onde está: minera em rodadas, resetando a mina entre elas
  let quebrados = 0
  msgs = await t.capturar(bot, async () => {
    for (let rodada = 0; rodada < 8 && !bot.mensagens.some(m => m.includes('Minere na mina concluída')); rodada++) {
      if (rodada > 0) await t.irParaMina(nome, mina)
      quebrados += await t.minerar(bot, mina, 20)
    }
  }, 1000)
  t.verificar(msgs.some(m => m.includes('Minere na mina concluída')) && msgs.some(m => m.includes('(2/5)')),
    'com 64 blocos na mina, conclui e mostra a etapa 2', `${quebrados} quebrados; ${msgs.filter(m => m.includes('Tutorial')).slice(-2).join(' | ')}`)
  msgs = await t.capturar(bot, () => bot.chat('/warp loja'), 4500)
  t.verificar(msgs.some(m => m.includes('(3/5)')), '/warp loja conclui a etapa 2')
  msgs = await t.capturar(bot, () => bot.chat('/ranks lista'), 1500)
  t.verificar(msgs.some(m => m.includes('(4/5)')), '/ranks conclui a etapa 3')
  msgs = await t.capturar(bot, () => bot.chat('/terreno auto'), 3000)
  t.verificar(msgs.some(m => m.includes('(5/5)')), '/terreno auto conclui a etapa 4')
  // última etapa com o inventário cheio: o prêmio fica guardado (antes, tira algum mapa que a mina deu)
  await t.console(`minecraft:clear ${nome} paper`)
  for (let i = 0; i < 36; i++) await t.console(`minecraft:give ${nome} cobblestone 64`)
  await t.espera(800)
  msgs = await t.capturar(bot, () => bot.chat('/diaria'), 2000)
  t.verificar(msgs.some(m => m.includes('inventário está cheio')) && !msgs.some(m => m.includes('Você ganhou um Mapa')),
    'concluindo com o inventário cheio, o prêmio fica guardado (nada no chão)', msgs.find(m => m.includes('cheio')))
  await t.console(`minecraft:clear ${nome} cobblestone 64`)
  msgs = await t.capturar(bot, () => bot.chat('/tutorial'), 1500)
  const mapas = Number(((await t.console(`minecraft:clear ${nome} paper 0`)).match(/Found (\d+)/) || [, 0])[1])
  t.verificar(msgs.some(m => m.includes('Você ganhou um Mapa do Tesouro Comum')) && mapas === 1, 'com espaço, o /tutorial entrega o Mapa do Tesouro Comum', `${mapas} mapa(s)`)
  msgs = await t.capturar(bot, () => bot.chat('/tutorial'), 1000)
  const mapas2 = Number(((await t.console(`minecraft:clear ${nome} paper 0`)).match(/Found (\d+)/) || [, 0])[1])
  t.verificar(msgs.some(m => m.includes('já concluiu')) && mapas2 === 1, 'o prêmio sai uma vez só', msgs[0])

  console.log('\n== /tpa com clique ==')
  const outro = await t.criarBot(t.nomeNovo('Ajt'))
  await t.espera(1500)
  // sem permissão no usuário: o /tpa, o /tpaccept e o /tpdeny vêm do grupo default
  await t.console(`execute in minecraft:spawn run minecraft:tp ${nome} 0.5 65 20.5`)
  await t.console(`execute in minecraft:spawn run minecraft:tp ${outro.username} 0.5 65 -20.5`)
  await t.espera(1500)
  const inicio = brutos.length
  msgs = await t.capturar(bot, () => outro.chat(`/tpa ${nome}`), 1500)
  const botao = brutos.slice(inicio).find(j => j.includes(`/tpaccept ${outro.username}`))
  t.verificar(msgs.some(m => m.includes('[✔ ACEITAR]') && m.includes('[✖ RECUSAR]')) && !!botao,
    'quem recebe o /tpa ganha [ACEITAR] (roda /tpaccept <quem pediu>) e [RECUSAR]', msgs.find(m => m.includes('ACEITAR')))
  // o clique roda o comando do botão
  bot.chat(`/tpaccept ${outro.username}`)
  await t.espera(6000)
  const e = Object.values(bot.entities).find(x => x.username === outro.username)
  t.verificar(!!e && e.position.distanceTo(bot.entity.position) < 3, 'aceitar pelo comando do botão traz quem pediu', e ? `a ${e.position.distanceTo(bot.entity.position).toFixed(1)} blocos` : 'longe')

  await t.capturar(bot, () => bot.chat('/terreno abandonar confirmar'), 500)
  await t.console(`tutorial admin resetar ${nome}`)
  await t.console(`minecraft:clear ${nome}`)
  await t.terminar(bot, outro)
})
