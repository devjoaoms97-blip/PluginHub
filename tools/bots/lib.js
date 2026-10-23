// Utilitários dos testes: RCON (console do servidor), bots e mineração dentro de uma mina.
// A pasta do servidor vem de MC_TESTE (padrão C:\mc-teste); porta e senha do RCON saem do
// server.properties dela, que o montar.ps1 gera com uma senha aleatória.
const fs = require('fs')
const path = require('path')
const mineflayer = require('mineflayer')
const { Rcon } = require('rcon-client')
const { Vec3 } = require('vec3')

const PASTA = process.env.MC_TESTE || 'C:\\mc-teste'
// o Mineflayer ainda não fala o protocolo do servidor: entra por uma versão anterior via ViaBackwards
const VERSAO = process.env.MC_VERSAO_BOT || '26.1'

const espera = ms => new Promise(r => setTimeout(r, ms))
const semCor = s => String(s).replace(/§./g, '')

function propriedades() {
  const p = {}
  for (const linha of fs.readFileSync(path.join(PASTA, 'server.properties'), 'utf8').split(/\r?\n/)) {
    const i = linha.indexOf('=')
    if (i > 0 && !linha.startsWith('#')) p[linha.slice(0, i).trim()] = linha.slice(i + 1).trim()
  }
  return p
}
const props = propriedades()

let rcon
/** Roda um comando no console do servidor e devolve a resposta sem cores. */
async function console_(cmd) {
  if (!rcon) rcon = await Rcon.connect({ host: 'localhost', port: Number(props['rcon.port'] || 25575), password: props['rcon.password'] })
  const r = semCor(await rcon.send(cmd)).trim()
  console.log(`  [console] ${cmd}${r ? '  →  ' + r.replace(/\n/g, ' | ') : ''}`)
  await espera(400)
  return r
}

/** Bot conectado e já no mundo. bot.mensagens guarda o chat e a barra acima do inventário. */
function criarBot(nome) {
  return new Promise((ok, falha) => {
    const bot = mineflayer.createBot({ host: 'localhost', port: Number(props['server-port'] || 25565), username: nome, version: VERSAO })
    bot.mensagens = []
    bot.on('messagestr', m => { bot.mensagens.push(m); console.log(`  [chat ${nome}] ${m}`) })
    // o Mineflayer não emite actionBar nessa versão: lê o pacote (texto em NBT) e junta os textos
    bot._client.on('action_bar', d => {
      const textos = []
      const andar = n => {
        if (!n || typeof n !== 'object') return
        if (n.type === 'string') textos.push(n.value)
        else Object.entries(n).forEach(([k, v]) => { if (k !== 'color') andar(v) })
      }
      andar(d.text)
      const m = semCor(textos.join(''))
      bot.mensagens.push('[barra] ' + m); console.log(`  [barra ${nome}] ${m}`)
    })
    bot.on('kicked', r => console.log(`  [${nome} kickado] ${JSON.stringify(r)}`))
    bot.on('error', e => console.log(`  [${nome} erro] ${e.message}`))
    bot.once('spawn', async () => { await espera(1500); ok(bot) })
    setTimeout(() => falha(new Error(`${nome} não entrou em 30 s (servidor ligado? ViaVersion e ViaBackwards instalados?)`)), 30000)
  })
}

/** Mensagens (chat e barra) que chegaram enquanto fn rodava. */
async function capturar(bot, fn, depoisMs = 1500) {
  const inicio = bot.mensagens.length
  await fn()
  await espera(depoisMs)
  return bot.mensagens.slice(inicio)
}

/** Itens e orbes de XP no chão perto do bot, como "cobblestonex1". */
function itensNoChao(bot, raio = 12) {
  return Object.values(bot.entities)
    .filter(e => e.position.distanceTo(bot.entity.position) < raio && (e.name === 'item' || e.name === 'experience_orb'))
    .map(e => {
      const i = e.name === 'item' && e.getDroppedItem?.()
      return i ? i.name + 'x' + i.count : e.name
    })
}

/** Inventário somado por item: { cobblestone: 9, coal: 3 }. */
function inventario(bot) {
  const t = {}
  for (const i of bot.inventory.items()) t[i.name] = (t[i.name] || 0) + i.count
  return t
}

/** Caixa e spawn de uma mina, lidos do minas.yml do RankupPlugin. */
function mina(nome) {
  const yml = fs.readFileSync(path.join(PASTA, 'plugins', 'RankupPlugin', 'minas.yml'), 'utf8')
  const bloco = yml.split(/\r?\n(?=  \S)/).find(b => b.trimStart().startsWith(nome + ':'))
  if (!bloco) throw new Error(`mina ${nome} não existe no minas.yml`)
  const campo = c => bloco.match(new RegExp(`\\n\\s+${c}: (.+)`))[1].split(',').map(Number)
  const [min, max, spawn] = [campo('min'), campo('max'), campo('spawn')]
  return { nome, min, max, spawn }
}

/** Quebra até n blocos dentro da mina, sempre o mais perto ao alcance. Devolve quantos quebrou. */
async function minerar(bot, caixa, n) {
  let quebrados = 0
  for (let tentativa = 0; quebrados < n && tentativa < n * 4; tentativa++) {
    const olho = bot.entity.position.offset(0, bot.entity.eyeHeight ?? 1.62, 0)
    let melhor = null, dist = 1e9
    for (let x = caixa.min[0]; x <= caixa.max[0]; x++)
      for (let y = caixa.min[1]; y <= caixa.max[1]; y++)
        for (let z = caixa.min[2]; z <= caixa.max[2]; z++) {
          const b = bot.blockAt(new Vec3(x, y, z))
          if (!b || b.name === 'air' || b.name === 'cave_air') continue
          const d = olho.distanceTo(b.position.offset(0.5, 0.5, 0.5))
          if (d < 4.3 && d < dist) { melhor = b; dist = d }
        }
    if (!melhor) { await espera(500); continue }
    try { await bot.dig(melhor, true); quebrados++; await espera(150) } catch (e) { await espera(300) }
  }
  await espera(800)
  return quebrados
}

/** Leva o bot ao spawn da mina, depois de resetar ela e limpar os itens soltos. */
async function irParaMina(nomeBot, m) {
  await console_('minecraft:kill @e[type=item]')
  await console_(`mina admin resetar ${m.nome}`)
  await console_(`tp ${nomeBot} ${m.spawn[0]} ${m.spawn[1]} ${m.spawn[2]}`)
  await espera(2000)
}

/** Nome de jogador novo a cada execução (até 16 caracteres), pra testes que só valem uma vez. */
const nomeNovo = prefixo => (prefixo + Date.now().toString(36)).slice(0, 16)

let falhas = 0
/** Marca ✔ ou ✘; o processo sai com código 1 se algum ✘. */
function verificar(ok, descricao, detalhe = '') {
  if (!ok) falhas++
  console.log(`${ok ? '✔' : '✘'} ${descricao}${detalhe ? '  (' + detalhe + ')' : ''}`)
}

/** Desconecta tudo e encerra com o resultado das verificações. */
async function terminar(...bots) {
  bots.forEach(b => b.quit())
  if (rcon) await rcon.end()
  console.log(falhas ? `\n${falhas} verificação(ões) falharam.` : '\nTudo certo.')
  process.exit(falhas ? 1 : 0)
}

/** Roda o teste e encerra com erro se ele lançar uma exceção. */
function rodar(teste) {
  teste().catch(e => { console.error('FALHOU', e); process.exit(1) })
}

module.exports = {
  espera, semCor, console: console_, criarBot, capturar, itensNoChao, inventario, mina, minerar, irParaMina, nomeNovo,
  verificar, terminar, rodar,
}
