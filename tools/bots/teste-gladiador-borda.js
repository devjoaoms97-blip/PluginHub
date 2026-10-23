// Borda do Gladiador: muda o config por alguns segundos (a borda começa a fechar 1 s depois da luta
// e fecha em 10 s), abre um Gladiador com 4 bots parados nos cantos e confere o aviso, o dano
// crescendo a cada segundo fora (2, 3, 4...), a armadura não reduzindo esse dano e o fim do evento.
// Volta o config original no fim. Precisa da arena (node montar-arenas.js).
// Uso: node teste-gladiador-borda.js
const t = require('./lib')
const fs = require('fs')

const PASTA = process.env.MC_TESTE || 'C:/mc-teste'
const CONFIG = `${PASTA}/plugins/GladiadorPlugin/config.yml`

t.rodar(async () => {
  const original = fs.readFileSync(CONFIG, 'utf8')
  const rapido = original.replace(/preparacao-segundos: \d+/, 'preparacao-segundos: 3').replace(/\nborda:[\s\S]*?(?=\n\S|$)/, '') +
    '\nborda:\n  ativado: true\n  comeca-depois-segundos: 1\n  fecha-em-segundos: 10\n  tamanho-final: 4\n' +
    '  dano-inicial: 2\n  aumento-por-segundo: 1\n  dano-maximo: 20\n'
  fs.writeFileSync(CONFIG, rapido)
  const REGEN = 'execute in minecraft:arenas run gamerule natural_health_regeneration'
  try {
    await t.console('gladiador reload')
    await t.console(`${REGEN} false`) // sem a regeneração natural no meio, as quedas de vida são o dano exato
    const bots = []
    for (const p of ['Bda', 'Bdb', 'Bdc', 'Bdd']) bots.push(await t.criarBot(t.nomeNovo(p)))
    for (const b of bots) {
      await t.console(`gamemode survival ${b.username}`)
      await t.console(`minecraft:clear ${b.username}`)
    }
    // o primeiro luta de netherite completa: o dano da borda tem que ser o mesmo
    const blindado = bots[0]
    for (const [slot, item] of [['armor.head', 'netherite_helmet'], ['armor.chest', 'netherite_chestplate'],
      ['armor.legs', 'netherite_leggings'], ['armor.feet', 'netherite_boots']]) {
      await t.console(`minecraft:item replace entity ${blindado.username} ${slot} with minecraft:${item}[enchantments={protection:4}]`)
    }
    await t.console('gladiador abrir individual 0')
    for (const b of bots) await t.capturar(b, () => b.chat('/gladiador entrar'), 400)
    const inicio = bots[0].mensagens.length

    // vida de cada bot a cada 250 ms, desde a luta
    const historico = bots.map(() => [])
    const coletor = setInterval(() => bots.forEach((b, i) => historico[i].push(b.health)), 250)
    await t.console('gladiador iniciar')

    let acabou = false
    for (let s = 0; s < 40 && !acabou; s++) {
      await t.espera(1000)
      acabou = bots[0].mensagens.slice(inicio).some(m => m.includes('venceu') || m.includes('Ninguém sobrou'))
    }
    clearInterval(coletor)

    const msgs = bots[0].mensagens.slice(inicio)
    t.verificar(msgs.some(m => m.includes('borda da arena está fechando')), 'aviso de que a borda está fechando')

    // perdas de vida, em ordem, de cada bot (cada queda de vida é um segundo de dano da borda)
    const quedas = historico.map(h => {
      const q = []
      for (let i = 1; i < h.length; i++) if (h[i] < h[i - 1] - 0.01) q.push(+(h[i - 1] - h[i]).toFixed(1))
      return q
    })
    const comeca = q => q.length >= 3 && Math.abs(q[0] - 2) < 0.2 && Math.abs(q[1] - 3) < 0.2 && Math.abs(q[2] - 4) < 0.2
    t.verificar(quedas.slice(1).some(comeca), 'o dano cresce a cada segundo fora: 2, 3, 4...', JSON.stringify(quedas.slice(1)))
    t.verificar(comeca(quedas[0]), 'a netherite com Proteção IV não reduz o dano da borda', JSON.stringify(quedas[0]))
    t.verificar(acabou, 'a borda encerrou a luta', msgs.find(m => m.includes('GLADIADOR ⚔') && /venceu|Ninguém/.test(m)))

    await t.espera(31000) // os 30 s de saque
    fs.writeFileSync(CONFIG, original)
    await t.console('gladiador reload')
    await t.console(`${REGEN} true`)
    await t.terminar(...bots) // encerra o processo: o config e a regra já voltaram
  } finally {
    fs.writeFileSync(CONFIG, original) // se algo der errado no meio
    await t.console(`${REGEN} true`).catch(() => {})
  }
})
