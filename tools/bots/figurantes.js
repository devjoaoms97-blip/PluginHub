// Figurantes pra gravar vídeo: bots que andam à toa perto de um jogador, como gente de verdade
// (andam, param, olham em volta, pulam, correm, agacham, batem no ar e olham pra quem chega perto).
// SÓ NO SERVIDOR DE TESTE.
//
//   node figurantes.js <seu nick> [quantos=6] [raio=12]
//
// Eles nascem em volta de você e ficam andando dentro do raio a partir do ponto onde você estava.
// No chat do jogo (o seu), dá pra mandar:
//   !aqui     os figurantes vêm pra perto de você e o centro passa a ser onde você está
//   !parem    todos param e olham pra você (bom pra cena parada)
//   !andem    voltam a andar
//   !raio 20  muda o raio
//   !lutar    vão pra arena do boss e lutam com ele (!andem ou !parem pra sair da luta)
// E a câmera em trilho (camera.js):
//   !cena t1      escolhe a cena (os pontos ficam salvos por cena em cenas-camera.json)
//   !ponto        marca onde você está e pra onde olha
//   !desfazer     apaga o último ponto · !limpar zera a cena · !cenas lista
//   !gravar 12    a câmera passa pelos pontos em 12 s (contagem de 3 antes) · !parar interrompe
// Ctrl+C tira todos do servidor.
const { Vec3 } = require('vec3')
const { espera, console: cmd, criarBot } = require('./lib')
const camera = require('./camera')

const [, , dono, quantosArg, raioArg] = process.argv
if (!dono) {
  console.log('Uso: node figurantes.js <seu nick> [quantos=6] [raio=12]')
  process.exit(1)
}
const QUANTOS = Math.min(Number(quantosArg) || 6, 9)
let raio = Number(raioArg) || 12

// Nicks com cara de jogador brasileiro.
const NICKS = ['Kaique_2011', 'biazinhaa', 'zMatheusBR', 'Juuh_Gamer', 'DarkSlayer77', 'PedroOPvP', 'Luanzinho_', 'xNicolas', 'Sofia_Craft']
// Cada um com um visual diferente (armadura e item na mão), pra não parecerem clones.
const VISUAIS = [
  { cabeca: 'netherite_helmet', peito: 'netherite_chestplate', pernas: 'netherite_leggings', pes: 'netherite_boots', mao: 'netherite_sword' },
  { peito: 'diamond_chestplate', pes: 'diamond_boots', mao: 'diamond_pickaxe' },
  { cabeca: 'iron_helmet', peito: 'iron_chestplate', pernas: 'iron_leggings', pes: 'iron_boots', mao: 'iron_axe' },
  { mao: 'fishing_rod' },
  { cabeca: 'diamond_helmet', peito: 'diamond_chestplate', pernas: 'diamond_leggings', pes: 'diamond_boots', mao: 'bow' },
  { peito: 'leather_chestplate', mao: 'bread' },
  { cabeca: 'golden_helmet', peito: 'netherite_chestplate', mao: 'netherite_pickaxe' },
  { pernas: 'chainmail_leggings', pes: 'chainmail_boots', mao: 'shield' },
  { peito: 'elytra', mao: 'firework_rocket' },
]
const SLOTS = { cabeca: 'armor.head', peito: 'armor.chest', pernas: 'armor.legs', pes: 'armor.feet', mao: 'weapon.mainhand' }

let centro = null
let parados = false
let lutando = false
const bots = []
// a arena do boss (o /boss setlocal): onde os figurantes vão pro !lutar
const ARENA = { mundo: 'minecraft:spawn', x: 0.5, y: 33, z: 195.5 }
// quem segura vara, pão, escudo ou foguete troca por uma arma pra lutar
const ARMA_DE_LUTA = ['iron_sword', 'diamond_sword', 'netherite_axe', 'iron_axe', 'diamond_axe', 'netherite_sword']

/** O mob hostil mais perto (o boss, na arena). */
function alvoDaLuta(bot) {
  return bot.nearestEntity(e => e !== bot.entity && e.type === 'hostile' && e.position.distanceTo(bot.entity.position) < 48)
}

/**
 * Um "lance" de luta: corre até o boss, dá uns golpes com pulo, de vez em quando anda de lado ou
 * recua um pouco, como gente de verdade (eles têm Resistência 255, então não morrem).
 */
async function lutar(bot) {
  const alvo = alvoDaLuta(bot)
  if (!alvo) { await espera(500); return }
  const fim = Date.now() + sorteio(1500, 3500)
  const lado = Math.random() < 0.5 ? 'left' : 'right'
  const deLado = Math.random() < 0.35
  while (Date.now() < fim && lutando && bot.entity && alvo.isValid !== false) {
    const d = bot.entity.position.distanceTo(alvo.position)
    const altura = alvo.height || 2
    await bot.lookAt(alvo.position.offset(0, altura * 0.6, 0), true).catch(() => {})
    bot.setControlState('forward', d > 2.6)
    bot.setControlState('sprint', d > 5)
    bot.setControlState(lado, deLado && d < 6)
    if (Math.random() < 0.08) bot.setControlState('jump', true)
    if (d < 3.6 && Date.now() > (bot.proximoGolpe || 0)) {
      bot.attack(alvo)
      bot.proximoGolpe = Date.now() + sorteio(450, 800)
    }
    await espera(150)
    bot.setControlState('jump', false)
  }
  bot.clearControlStates()
  // às vezes recua um pouco antes de voltar
  if (lutando && Math.random() < 0.25) {
    bot.setControlState('back', true); await espera(sorteio(400, 900)); bot.clearControlStates()
  }
}
const sorteio = (a, b) => a + Math.random() * (b - a)

/** O bloco nessa posição não é água. */
function seco(bot, x, y, z) {
  const b = bot.blockAt(new Vec3(Math.floor(x), Math.floor(y), Math.floor(z)))
  return !b || !['water', 'bubble_column', 'seagrass', 'kelp', 'kelp_plant'].includes(b.name)
}

/** Um bloco sólido e seco perto do centro, com 2 de ar em cima (onde o figurante cabe em pé). */
function chaoSeco(bot) {
  const solido = b => b && b.boundingBox === 'block' && seco(bot, b.position.x, b.position.y, b.position.z)
  const livre = b => !b || (b.boundingBox === 'empty' && seco(bot, b.position.x, b.position.y, b.position.z))
  const base = bot.entity.position
  for (let t = 0; t < 40; t++) {
    const x = Math.floor(centro.x + sorteio(-raio, raio)), z = Math.floor(centro.z + sorteio(-raio, raio))
    for (let y = Math.floor(base.y) + 4; y > base.y - 12; y--) {
      const b = bot.blockAt(new Vec3(x, y, z))
      if (!b) break
      if (solido(b) && livre(bot.blockAt(new Vec3(x, y + 1, z))) && livre(bot.blockAt(new Vec3(x, y + 2, z)))) {
        return { x: x + 0.5, y: y + 1, z: z + 0.5 }
      }
      if (b.boundingBox === 'block') break // achou chão molhado ou teto: tenta outro ponto
    }
  }
  return null
}

function jogadorDono(bot) {
  return Object.values(bot.players).find(p => p.username === dono)?.entity
}

/** Uma "intenção" por vez: andar até um ponto, parar olhando em volta, ou pular/bater. */
async function viver(bot) {
  while (bot.entity) {
    if (lutando) { await lutar(bot); continue }
    if (parados || !centro) {
      bot.clearControlStates()
      const d = jogadorDono(bot)
      if (d) await bot.lookAt(d.position.offset(0, 1.6, 0), true).catch(() => {})
      await espera(500)
      continue
    }
    const pos = bot.entity.position
    if (bot.entity.isInWater) {
      // sai da água: procura chão seco em volta e nada pra lá; se não sair, vai pra perto do dono
      bot.naAgua = (bot.naAgua || 0) + 1
      if (bot.naAgua > 4) {
        // não conseguiu sair nadando: vai pra um chão seco perto do centro (nunca pra perto do dono,
        // que pode estar voando)
        const chao = chaoSeco(bot)
        if (chao) await cmd(`execute at ${bot.username} run minecraft:tp ${bot.username} ${chao.x} ${chao.y} ${chao.z}`)
        bot.naAgua = 0
        await espera(1000)
        continue
      }
      const saida = [0, 1, 2, 3, 4, 5, 6, 7].map(k => k * Math.PI / 4)
        .map(a => pos.offset(Math.cos(a) * 2.5, 0, Math.sin(a) * 2.5))
        .find(p => seco(bot, p.x, pos.y, p.z) && seco(bot, p.x, pos.y + 1, p.z))
      const rumo = saida || centro
      await bot.lookAt(new Vec3(rumo.x, pos.y + 1.6, rumo.z), true).catch(() => {})
      bot.setControlState('jump', true); bot.setControlState('forward', true)
      await espera(1200)
      bot.clearControlStates()
      continue
    }
    bot.naAgua = 0
    const sorte = Math.random()
    if (sorte < 0.55) {
      // andar até um ponto do círculo, sem água (se estiver longe do centro, volta pra ele)
      const longe = pos.distanceTo(centro) > raio
      // longe do centro: volta pra um chão seco perto dele (o centro pode ser a fonte)
      const volta = longe && chaoSeco(bot)
      let alvo = volta ? new Vec3(volta.x, volta.y, volta.z) : centro.clone()
      if (!longe) {
        for (let t = 0; t < 10; t++) {
          const p = centro.offset(sorteio(-raio, raio), 0, sorteio(-raio, raio))
          if (seco(bot, p.x, pos.y, p.z) && seco(bot, p.x, pos.y - 1, p.z)) { alvo = p; break }
        }
      }
      await bot.lookAt(new Vec3(alvo.x, pos.y + 1.6, alvo.z), true).catch(() => {})
      bot.setControlState('forward', true)
      bot.setControlState('sprint', Math.random() < 0.25)
      const limite = Date.now() + sorteio(1500, 5000)
      let ultimo = pos.clone()
      let travado = 0
      while (Date.now() < limite && bot.entity && !parados) {
        await espera(250)
        const agora = bot.entity.position
        if (agora.distanceTo(alvo) < 1.5) break
        if (bot.entity.isInWater) { bot.setControlState('jump', true); travado = 0 }
        else if (agora.distanceTo(ultimo) < 0.1) {
          // travou num degrau ou bloco: pula; se continuar travado, desiste desse ponto
          bot.setControlState('jump', true); await espera(300); bot.setControlState('jump', false)
          if (++travado > 4) break
        } else { bot.setControlState('jump', false); travado = 0 }
        ultimo = agora.clone()
      }
      bot.clearControlStates()
    } else if (sorte < 0.8) {
      // parado, olhando em volta (às vezes pro jogador mais perto)
      const perto = bot.nearestEntity(e => e.type === 'player' && e.position.distanceTo(bot.entity.position) < 8)
      if (perto && Math.random() < 0.6) await bot.lookAt(perto.position.offset(0, 1.6, 0), true).catch(() => {})
      else await bot.look(sorteio(-Math.PI, Math.PI), sorteio(-0.3, 0.2), true).catch(() => {})
      await espera(sorteio(1000, 3500))
    } else if (sorte < 0.9) {
      // bate no ar umas vezes
      for (let i = 0; i < 2 + Math.floor(Math.random() * 3); i++) { bot.swingArm(); await espera(sorteio(250, 450)) }
    } else if (sorte < 0.95) {
      bot.setControlState('jump', true); await espera(sorteio(400, 1200)); bot.setControlState('jump', false)
    } else {
      bot.setControlState('sneak', true); await espera(sorteio(500, 1500)); bot.setControlState('sneak', false)
    }
    await espera(sorteio(100, 600))
  }
}

async function principal() {
  const online = await cmd('list')
  if (!online.includes(dono)) console.log(`  aviso: ${dono} não parece estar online; os figurantes nascem onde der`)
  for (let i = 0; i < QUANTOS; i++) {
    const nome = NICKS[i]
    try {
      const bot = await criarBot(nome)
      bots.push(bot)
      await cmd(`gamemode survival ${nome}`)
      await cmd(`effect give ${nome} minecraft:resistance infinite 255 true`)
      await cmd(`effect give ${nome} minecraft:saturation infinite 255 true`)
      for (const [parte, item] of Object.entries(VISUAIS[i % VISUAIS.length])) await cmd(`minecraft:item replace entity ${nome} ${SLOTS[parte]} with minecraft:${item}`)
      // espalha em volta do dono
      await cmd(`execute at ${dono} run minecraft:tp ${nome} ~${Math.round(sorteio(-4, 4))} ~ ~${Math.round(sorteio(-4, 4))}`)
      await espera(600)
    } catch (e) {
      console.log(`  ${nome} não entrou: ${e.message}`)
    }
  }
  if (!bots.length) { console.log('Nenhum figurante entrou.'); process.exit(1) }
  const primeiro = bots[0]
  const d = jogadorDono(primeiro)
  centro = (d ? d.position : primeiro.entity.position).clone()
  console.log(`\n${bots.length} figurantes andando num raio de ${raio} blocos. No chat: !aqui, !parem, !andem, !raio N. Ctrl+C encerra.\n`)

  // comandos do dono pelo chat
  // O chat do servidor tem tags antes do nome ("[L] [Rei] [DoD] Viagrinha: !aqui"), então lê a linha crua.
  // O servidor manda o chat com o nome à parte: quem mandou vem pelo UUID.
  primeiro.on('message', async (json, posicao, remetente) => {
    const msg = json.toString().trim()
    if (!msg.startsWith('!')) return
    const uuid = primeiro.players[dono]?.uuid
    console.log(`  [comando] "${msg}" de ${remetente} (${dono} = ${uuid})`)
    if (!uuid || remetente !== uuid) return
    if (await camera.comando(dono, msg).catch(e => { console.log(`  [câmera] ${e.message}`); return true })) return
    if (msg === '!lutar') {
      // vão pra arena, em volta do boss, com uma arma na mão
      lutando = true; parados = false
      for (const [i, b] of bots.entries()) {
        const a = (i / bots.length) * Math.PI * 2
        await cmd(`execute in ${ARENA.mundo} run minecraft:tp ${b.username} ${(ARENA.x + Math.cos(a) * 9).toFixed(1)} ${ARENA.y + 1} ${(ARENA.z + Math.sin(a) * 9).toFixed(1)}`)
        const vis = VISUAIS[i % VISUAIS.length]
        if (!/sword|axe/.test(vis.mao || '')) await cmd(`minecraft:item replace entity ${b.username} weapon.mainhand with minecraft:${ARMA_DE_LUTA[i % ARMA_DE_LUTA.length]}`)
      }
      console.log('  lutando')
    }
    if (msg === '!andem' || msg === '!parem') lutando = false
    if (msg === '!parem') { parados = true; console.log('  parados') }
    if (msg === '!andem') { parados = false; console.log('  andando') }
    if (msg.startsWith('!raio')) { raio = Number(msg.split(' ')[1]) || raio; console.log(`  raio ${raio}`) }
    if (msg === '!aqui') {
      const e = jogadorDono(primeiro)
      if (e) centro = e.position.clone()
      for (const b of bots) await cmd(`execute at ${dono} run minecraft:tp ${b.username} ~${Math.round(sorteio(-4, 4))} ~ ~${Math.round(sorteio(-4, 4))}`)
      console.log('  vieram pra perto')
    }
  })
  bots.forEach(b => viver(b).catch(e => console.log(`  ${b.username}: ${e.message}`)))
}

async function sair() {
  console.log('\nTirando os figurantes...')
  await camera.fechar()
  for (const b of bots) { try { b.quit() } catch {} }
  await espera(1000)
  process.exit(0)
}
process.on('SIGINT', sair)
principal().catch(e => { console.error(e); sair() })
