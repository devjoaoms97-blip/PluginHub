// Quiz (EventosPlugin): o /eventos mostra às 12h e 15h; a rodada abre e as perguntas saem no chat;
// resposta errada ("Não é isso"), tentativa rápida demais ("Calma"), resposta certa com acento e
// maiúscula diferentes; pergunta sem acerto mostra a resposta; no fim, quem fez mais pontos ganha o cheque.
// O teste encurta o tempo das perguntas no config do servidor (e devolve o original no fim). A resposta
// certa vem do /quiz resposta (staff).
// Uso: node teste-quiz.js
const fs = require('fs')
const path = require('path')
const t = require('./lib')

const CONFIG = path.join(process.env.MC_TESTE || 'C:\\mc-teste', 'plugins', 'EventosPlugin', 'config.yml')
const quantos = async (nome, item) => Number(((await t.console(`minecraft:clear ${nome} ${item} 0`)).match(/Found (\d+)/) || [, 0])[1])
const resposta = async () => ((await t.console('quiz resposta')).match(/Resposta: (.+)$/) || [, null])[1]

/** Espera a pergunta n aparecer no chat do bot (procura no que ele já recebeu, também). */
async function pergunta(bot, n, limiteMs = 15000) {
  for (const fim = Date.now() + limiteMs; Date.now() < fim;) {
    const m = bot.mensagens.find(x => x.includes(`Pergunta ${n}/10`))
    if (m) return m
    await t.espera(200)
  }
  return null
}

/** Troca acento e maiúscula, pra conferir que a resposta não liga pra isso. */
const bagunçar = r => r.normalize('NFD').replace(/\p{M}/gu, '').toUpperCase()

t.rodar(async () => {
  const original = fs.readFileSync(CONFIG, 'utf8')
  fs.writeFileSync(CONFIG, original.replace(/(\nquiz:[\s\S]*?segundos-por-pergunta:) \d+/, '$1 6').replace(/(\nquiz:[\s\S]*?pausa-segundos:) \d+/, '$1 1'))
  await t.console('quiz cancelar')
  await t.console('vietna reload')
  const a = await t.criarBot(t.nomeNovo('Qza'))
  const b = await t.criarBot(t.nomeNovo('Qzb'))
  try {
    for (const bot of [a, b]) await t.console(`minecraft:clear ${bot.username}`)
    await t.espera(1000)

    let msgs = await t.capturar(b, () => b.chat('/eventos'))
    t.verificar(['12:00', '15:00'].every(h => msgs.some(m => m.includes('Quiz') && m.includes(h))), '/eventos mostra o Quiz às 12h e 15h',
      msgs.find(m => m.includes('Quiz')))
    msgs = await t.capturar(b, () => b.chat('/quiz resposta qualquer'))
    t.verificar(msgs.some(m => m.includes('Não tem pergunta aberta')), 'sem rodada, /quiz não aceita resposta', msgs.find(m => m.includes('pergunta')))

    console.log('\n== Rodada ==')
    await t.console('quiz abrir')
    const primeira = await pergunta(a, 1)
    t.verificar(!!primeira, 'a primeira pergunta sai no chat', primeira)
    let certa = await resposta()
    msgs = await t.capturar(a, () => a.chat('/quiz resposta errada de propósito'), 800)
    t.verificar(msgs.some(m => m.includes('Não é isso')), 'resposta errada: "Não é isso"', msgs.find(m => m.includes('isso')))
    msgs = await t.capturar(a, () => a.chat(`/quiz ${certa}`), 800)
    t.verificar(msgs.some(m => m.includes('Calma')), 'tentar de novo rápido demais: "Calma"', msgs.find(m => m.includes('Calma')))
    await t.espera(2200)
    msgs = await t.capturar(b, () => a.chat(`/quiz ${bagunçar(certa)}`), 1200)
    t.verificar(msgs.some(m => m.includes(a.username) && m.includes('acertou')), 'certa, com acento e maiúscula diferentes: acertou (e todos veem)',
      `${certa} → ${bagunçar(certa)} | ${msgs.find(m => m.includes('acertou'))}`)

    // a 2ª ninguém responde: aparece a resposta
    await pergunta(a, 2)
    certa = await resposta()
    msgs = await t.capturar(b, () => t.espera(7500))
    t.verificar(msgs.some(m => m.includes('Ninguém acertou') && m.includes(certa)), 'sem acerto, a resposta aparece', msgs.find(m => m.includes('Ninguém')))

    // da 3ª à 10ª: o A acerta as ímpares e o B as pares (o A fica com 5 e o B com 4)
    for (let n = 3; n <= 10; n++) {
      if (!(await pergunta(a, n))) break
      certa = await resposta()
      const quem = n % 2 ? a : b
      await t.capturar(quem, () => quem.chat(`/quiz ${certa}`), 600)
    }
    const fim = await t.capturar(a, () => t.espera(3000))
    const venceu = fim.find(m => m.includes('QUIZ') && m.includes('venceu'))
    t.verificar(!!venceu && venceu.includes(a.username) && venceu.includes('5 ponto') && venceu.includes('5.000'),
      'quem fez mais pontos vence e leva R$ 5.000', venceu)
    t.verificar(await quantos(a.username, 'paper') === 1 && await quantos(b.username, 'paper') === 0, 'o cheque vai só pro vencedor')
  } finally {
    fs.writeFileSync(CONFIG, original)
    await t.console('quiz cancelar')
    await t.console('vietna reload')
    for (const bot of [a, b]) await t.console(`minecraft:clear ${bot.username}`)
  }
  await t.terminar(a, b)
})
