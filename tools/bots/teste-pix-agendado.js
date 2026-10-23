// Pix agendado: o primeiro pagamento sai na hora (com a taxa), o próximo sai sozinho no
// vencimento mesmo com quem recebe offline, a falta de saldo conta falha e avisa, e cancelar
// para tudo. Usa a staff (/pix admin vencer) pra não esperar dias. Uso: node teste-pix-agendado.js
const t = require('./lib')

const dinheiro = r => Number((r.match(/\$([\d,.]+)/) || [, 'NaN'])[1].replace(/,/g, ''))
const saldo = async nome => dinheiro(await t.console(`balance ${nome}`))

/** Espera a próxima verificação dos agendamentos (a cada 20 s). */
async function esperarVerificacao(bot, texto) {
  const inicio = bot.mensagens.length
  for (let s = 0; s < 25 && !bot.mensagens.slice(inicio).some(m => m.includes(texto)); s++) await t.espera(1000)
  return bot.mensagens.slice(inicio)
}

t.rodar(async () => {
  const a = await t.criarBot(t.nomeNovo('Pxa')), b = await t.criarBot(t.nomeNovo('Pxb'))
  const [nomeA, nomeB] = [a.username, b.username]
  await t.console(`eco set ${nomeA} 10000`)
  await t.console(`eco set ${nomeB} 0`)
  await t.console(`op ${nomeA}`) // pra usar o /pix admin vencer
  await t.espera(500)

  console.log('\n== Agendar ==')
  let msgs = await t.capturar(a, () => a.chat(`/pix agendar ${nomeB} 1000 7 aluguel`), 2000)
  const id = (msgs.join(' ').match(/#(\d+)/) || [])[1]
  t.verificar(!!id, 'agendou', msgs.find(m => m.includes('agendado')))
  t.verificar(await saldo(nomeA) === 9000, 'o primeiro pagamento saiu na hora')
  t.verificar(await saldo(nomeB) === 990, 'quem recebe ganhou o valor menos a taxa de 1%', `${await saldo(nomeB)}`)
  msgs = await t.capturar(b, () => b.chat('/pix agendamentos'))
  t.verificar(msgs.some(m => m.includes(`#${id}`) && m.includes(nomeA) && m.includes('7 dia')), 'quem recebe vê o agendamento', msgs.find(m => m.includes(`#${id}`)))

  console.log('\n== Vencimento com quem recebe offline ==')
  b.quit()
  await t.espera(1000)
  await t.capturar(a, () => a.chat(`/pix admin vencer ${id}`))
  msgs = await esperarVerificacao(a, 'Pix agendado #')
  t.verificar(msgs.some(m => m.includes('você pagou')), 'o próximo pagamento saiu sozinho', msgs.find(m => m.includes('Pix agendado')))
  t.verificar(await saldo(nomeA) === 8000 && await saldo(nomeB) === 1980, 'mesmo com quem recebe offline', `${await saldo(nomeA)} / ${await saldo(nomeB)}`)

  console.log('\n== Sem saldo ==')
  await t.console(`eco set ${nomeA} 0`)
  await t.capturar(a, () => a.chat(`/pix admin vencer ${id}`))
  msgs = await esperarVerificacao(a, 'não saiu')
  t.verificar(msgs.some(m => m.includes('não saiu') && m.includes('saldo insuficiente') && m.includes('1/3')), 'sem saldo: avisa e tenta de novo em 1 h',
    msgs.find(m => m.includes('não saiu')))

  console.log('\n== Cancelar ==')
  msgs = await t.capturar(a, () => a.chat(`/pix agendamentos cancelar ${id}`))
  t.verificar(msgs.some(m => m.includes('cancelado')), 'cancelou')
  msgs = await t.capturar(a, () => a.chat('/pix agendamentos'))
  t.verificar(msgs.some(m => m.includes('Nenhum')), 'e não aparece mais')

  await t.console(`deop ${nomeA}`)
  await t.terminar(a)
})
