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
//   !brigar   lutam com o mob hostil mais perto ali mesmo (guardiões do tesouro, mobs)
//   !evento spleef   abre o evento e os figurantes se inscrevem (spleef, correoucai, vietna,
//                    sobrevivencia); você entra se quiser (/spleef entrar) ou assiste depois
//   !comecar         começa o evento, e eles jogam: quebram a neve debaixo dos outros (spleef), não
//                    param de correr (correoucai), empurram os outros dos pilares (vietna) ou lutam
//                    com os invasores (sobrevivencia)
//   !fim             cancela o evento e eles voltam a andar com o visual de antes
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
// o evento em que os figurantes estão inscritos e, depois do !comecar, jogando
let evento = null
let jogando = false
const EVENTOS = ['spleef', 'correoucai', 'vietna', 'sobrevivencia', 'corrida', 'labirinto']
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
  while (Date.now() < fim && (lutando || jogando) && bot.entity && alvo.isValid !== false) {
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
  if ((lutando || jogando) && Math.random() < 0.25) {
    bot.setControlState('back', true); await espera(sorteio(400, 900)); bot.clearControlStates()
  }
}
const sorteio = (a, b) => a + Math.random() * (b - a)

/** Outro jogador perto (outro figurante ou você), dentro da arena. */
function outroJogador(bot, alcance = 24) {
  return bot.nearestEntity(e => e !== bot.entity && e.type === 'player' && e.position.distanceTo(bot.entity.position) < alcance
    && Math.abs(e.position.y - bot.entity.position.y) < 3)
}

/** Corre numa direção por um tempo curto, pulando de vez em quando (no Corre ou Cai, quem para cai). */
async function correr(bot, ms) {
  await bot.look(sorteio(-Math.PI, Math.PI), 0, true).catch(() => {})
  bot.setControlState('forward', true)
  bot.setControlState('sprint', Math.random() < 0.6)
  const fim = Date.now() + ms
  while (Date.now() < fim && jogando && bot.entity) {
    if (Math.random() < 0.15) { bot.setControlState('jump', true); await espera(150); bot.setControlState('jump', false) }
    await espera(150)
  }
  bot.clearControlStates()
}

/**
 * Pra onde correr no Corre ou Cai: testa 16 direções e fica com a que tem mais piso pela frente, sem
 * parede (correr numa direção qualquer prendia no canto, e quem para cai). Prefere seguir o rumo atual.
 */
function rumoLivre(bot, atual, preferido) {
  const pos = bot.entity.position
  let melhor = atual, nota = -Infinity
  for (let i = 0; i < 16; i++) {
    const yaw = (i / 16) * Math.PI * 2
    const dx = -Math.sin(yaw), dz = -Math.cos(yaw)
    let n = 0
    for (let d = 1; d <= 7; d++) {
      const x = Math.floor(pos.x + dx * d), z = Math.floor(pos.z + dz * d), y = Math.floor(pos.y)
      const pe = bot.blockAt(new Vec3(x, y, z)), cabeca = bot.blockAt(new Vec3(x, y + 1, z))
      if ((pe && pe.boundingBox === 'block') || (cabeca && cabeca.boundingBox === 'block')) { n -= (8 - d) * 3; break }
      const chao = bot.blockAt(new Vec3(x, y - 1, z))
      if (chao && chao.boundingBox === 'block') n += 8 - d
    }
    if (atual !== undefined) n += Math.cos(yaw - atual) * 3
    if (preferido !== undefined) n += Math.cos(yaw - preferido) * 14
    n += Math.random() * 2
    if (n > nota) { nota = n; melhor = yaw }
  }
  return melhor
}

/** No Corre ou Cai: corre sem parar, mudando de rumo pra onde ainda tem piso. */
async function correrSemParar(bot) {
  let rumo = rumoLivre(bot)
  bot.setControlState('forward', true)
  while (jogando && evento === 'correoucai' && bot.entity) {
    rumo = rumoLivre(bot, rumo)
    await bot.look(rumo, 0, false).catch(() => {})
    bot.setControlState('sprint', Math.random() < 0.7)
    if (Math.random() < 0.08) { bot.setControlState('jump', true); await espera(150); bot.setControlState('jump', false) }
    await espera(200)
  }
  bot.clearControlStates()
}

/**
 * Corrida Maluca: a pista vai pro norte (+z). Corre pra frente desviando de parede, pula degrau e buraco
 * (quem cai na água volta pro checkpoint sozinho, pelo plugin).
 */
async function correrCorrida(bot) {
  const NORTE = Math.PI // yaw do mineflayer: +z
  let rumo = NORTE
  bot.setControlState('forward', true); bot.setControlState('sprint', true)
  while (jogando && evento === 'corrida' && bot.entity) {
    rumo = rumoLivre(bot, rumo, NORTE)
    await bot.look(rumo, 0, true).catch(() => {})
    const pos = bot.entity.position, dx = -Math.sin(rumo), dz = -Math.cos(rumo), y = Math.floor(pos.y)
    const em = d => new Vec3(Math.floor(pos.x + dx * d), y, Math.floor(pos.z + dz * d))
    const frente = bot.blockAt(em(1)), chao1 = bot.blockAt(em(1).offset(0, -1, 0)), chao2 = bot.blockAt(em(2).offset(0, -1, 0))
    const degrau = frente && frente.boundingBox === 'block'
    const buraco = bot.entity.onGround && (!chao1 || chao1.boundingBox !== 'block') && (!chao2 || chao2.boundingBox !== 'block')
    bot.setControlState('jump', degrau || buraco || Math.random() < 0.05)
    await espera(100)
  }
  bot.clearControlStates()
}

/**
 * Labirinto do Fauno: anda pelos corredores (desviando das paredes) e, vendo alguém perto, briga de
 * verdade (no Labirinto o PvP vale). Foge do Fauno se ele chegar perto.
 */
async function labirinto(bot) {
  const fauno = bot.nearestEntity(e => e.type !== 'player' && e !== bot.entity && e.position.distanceTo(bot.entity.position) < 7
    && /husk|fauno/i.test((e.name || '') + (e.displayName || '') + (e.username || '')))
  const alvo = outroJogador(bot, 7)
  let rumo = bot.entity.yaw
  if (fauno) rumo = Math.atan2(fauno.position.x - bot.entity.position.x, fauno.position.z - bot.entity.position.z) // de costas pra ele
  if (alvo && !fauno) {
    const fim = Date.now() + sorteio(1500, 3000)
    while (Date.now() < fim && jogando && bot.entity && alvo.isValid !== false) {
      const d = bot.entity.position.distanceTo(alvo.position)
      await bot.lookAt(alvo.position.offset(0, 1.4, 0), true).catch(() => {})
      bot.setControlState('forward', d > 2.4); bot.setControlState('sprint', d > 4)
      if (d < 3.4 && Date.now() > (bot.proximoGolpe || 0)) { bot.attack(alvo); bot.proximoGolpe = Date.now() + sorteio(500, 850) }
      await espera(150)
    }
    bot.clearControlStates()
    return
  }
  bot.setControlState('forward', true); bot.setControlState('sprint', !!fauno)
  const fim = Date.now() + sorteio(1200, 2500)
  while (Date.now() < fim && jogando && bot.entity) {
    rumo = rumoLivre(bot, rumo)
    await bot.look(rumo, 0, true).catch(() => {})
    await espera(200)
  }
  bot.clearControlStates()
}

/** Um lance de cada evento. */
async function jogarEvento(bot) {
  if (evento === 'correoucai') return correrSemParar(bot)
  if (evento === 'corrida') return correrCorrida(bot)
  if (evento === 'labirinto') return labirinto(bot)
  if (evento === 'sobrevivencia') {
    // o kit vem no inventário: pega a espada antes de lutar
    if (!/sword/.test(bot.heldItem?.name || '')) {
      const espada = bot.inventory.items().find(i => /sword/.test(i.name))
      if (espada) await bot.equip(espada, 'hand').catch(() => {})
    }
    return lutar(bot)
  }
  if (evento === 'spleef') return spleef(bot)
  const alvo = outroJogador(bot, 34)
  if (!alvo) { await espera(500); return }
  return jogarPilares(bot, alvo)
}

/** O alvo de cada figurante no Spleef: fica com o mesmo um tempo (senão todos vão no mais perto e se amontoam). */
const alvos = new Map()
function alvoSpleef(bot) {
  const atual = alvos.get(bot.username)
  const vivo = atual && bot.players[atual.nome]?.entity
  if (vivo && Date.now() < atual.ate && Math.abs(vivo.position.y - bot.entity.position.y) < 3) return vivo
  const candidatos = Object.values(bot.players).map(p => p.entity)
    .filter(e => e && e !== bot.entity && Math.abs(e.position.y - bot.entity.position.y) < 3 && e.position.distanceTo(bot.entity.position) < 30)
  if (!candidatos.length) return null
  // você aparece mais (é quem a câmera segue)
  const dono_ = candidatos.find(e => e.username === dono)
  const e = dono_ && Math.random() < 0.65 ? dono_ : candidatos[Math.floor(Math.random() * candidatos.length)]
  alvos.set(bot.username, { nome: e.username, ate: Date.now() + sorteio(4000, 9000) })
  return e
}

/** Um lance do Spleef: chega a uns 3 blocos do alvo (sem grudar), quebra a neve debaixo ou na frente dele e se mexe. */
async function spleef(bot) {
  const eu = bot.entity.position
  // alguém grudado: sai de lado
  const grudado = bot.nearestEntity(e => e !== bot.entity && e.type === 'player' && e.position.distanceTo(eu) < 1.6)
  if (grudado) {
    await bot.lookAt(grudado.position.offset(0, 1.6, 0), true).catch(() => {})
    bot.setControlState('back', true); bot.setControlState(Math.random() < 0.5 ? 'left' : 'right', true)
    await espera(sorteio(350, 600)); bot.clearControlStates()
    return
  }
  const alvo = alvoSpleef(bot)
  if (!alvo) return correr(bot, sorteio(500, 900))
  const d = eu.distanceTo(alvo.position)
  if (d > 4) {
    await bot.lookAt(alvo.position.offset(0, 1.6, 0), true).catch(() => {})
    bot.setControlState('forward', true); bot.setControlState('sprint', d > 7)
    await espera(sorteio(250, 500)); bot.clearControlStates()
    return
  }
  // a neve em volta do alvo, que não seja a minha nem a do lado da minha
  const p = alvo.position, meu = eu.floored()
  const blocos = [[0, 0], [1, 0], [-1, 0], [0, 1], [0, -1], [1, 1], [-1, -1], [1, -1], [-1, 1]]
    .map(([dx, dz]) => bot.blockAt(new Vec3(Math.floor(p.x) + dx, Math.floor(p.y) - 1, Math.floor(p.z) + dz)))
    .filter(b => b && b.name.includes('snow') && b.position.distanceTo(eu) < 4.5
      && Math.max(Math.abs(b.position.x - meu.x), Math.abs(b.position.z - meu.z)) > 1)
  for (const b of blocos.slice(0, 1 + Math.floor(Math.random() * 3))) {
    await bot.lookAt(b.position.offset(0.5, 1, 0.5), true).catch(() => {})
    await bot.dig(b, true).catch(() => {})
    await espera(sorteio(80, 200))
  }
  // não fica parado no mesmo lugar: um passo de lado
  bot.setControlState(Math.random() < 0.5 ? 'left' : 'right', true)
  if (Math.random() < 0.3) bot.setControlState('back', true)
  await espera(sorteio(200, 450)); bot.clearControlStates()
}

/**
 * Mira um projétil (bola de neve: 1.5 blocos/tique e gravidade 0.03; flecha com o arco puxado: 3.0 e
 * 0.05) no peito do alvo: simula a trajetória pra cada inclinação e fica com a que passa mais perto.
 * Adianta o alvo pelo tempo de voo, se ele estiver andando.
 */
async function mirar(bot, alvo, v0, g) {
  const olho = bot.entity.position.offset(0, 1.62, 0)
  let destino = alvo.position.offset(0, 1.1, 0)
  let melhor = 0
  for (let volta = 0; volta < 2; volta++) {
    const dx = destino.x - olho.x, dz = destino.z - olho.z, h = Math.hypot(dx, dz), dy = destino.y - olho.y
    let erro = Infinity, tiques = 0
    for (let pitch = -0.5; pitch <= 0.9; pitch += 0.005) {
      let vx = v0 * Math.cos(pitch), vy = v0 * Math.sin(pitch), x = 0, y = 0, t = 0
      while (x < h && t < 100) { x += vx; y += vy; vx *= 0.99; vy = vy * 0.99 - g; t++ }
      if (Math.abs(y - dy) < erro) { erro = Math.abs(y - dy); melhor = pitch; tiques = t }
    }
    const vel = alvo.velocity || { x: 0, z: 0 }
    destino = alvo.position.offset(vel.x * tiques, 1.1, vel.z * tiques)
  }
  const yaw = Math.atan2(-(destino.x - olho.x), -(destino.z - olho.z))
  await bot.look(yaw, melhor, true).catch(() => {})
}

/** Vietnã: empurra quem está perto. */
async function jogarPilares(bot, alvo) {
  if (evento === 'vietna') {
    // soco não empurra no Vietnã: joga bola de neve (e às vezes atira com o arco) em quem está nos
    // outros pilares, mirando um pouco acima conforme a distância. Não sai do pilar (cairia na água).
    const bola = bot.inventory.items().find(i => i.name === 'snowball')
    const arco = bot.inventory.items().find(i => i.name === 'bow')
    const vento = bot.inventory.items().find(i => i.name === 'wind_charge')
    const d = bot.entity.position.distanceTo(alvo.position)
    // sem pressa: com todo mundo mirando certo a partida acabava em segundos
    await espera(sorteio(1200, 3000))
    if (vento && d < 14 && Math.random() < 0.4) {
      // carga de vento: voa reto e explode empurrando (o empurrão é do próprio jogo)
      await bot.equip(vento, 'hand').catch(() => {})
      await mirar(bot, alvo, 1.5, 0)
      bot.activateItem()
      await espera(sorteio(400, 700))
    } else if (arco && (!bola || Math.random() < 0.7)) {
      await bot.equip(arco, 'hand').catch(() => {})
      bot.activateItem(); await espera(sorteio(1050, 1250))
      await mirar(bot, alvo, 3.0, 0.05)
      bot.deactivateItem()
      await espera(sorteio(300, 600))
    } else if (bola) {
      await bot.equip(bola, 'hand').catch(() => {})
      for (let i = 0; i < 1 + Math.floor(Math.random() * 2); i++) {
        await mirar(bot, alvo, 1.5, 0.03)
        bot.activateItem(); await espera(sorteio(250, 450))
      }
      await espera(sorteio(300, 700))
    } else {
      await espera(sorteio(500, 900))
    }
    // um passinho no pilar, pra não ficar parado (o pilar é 2x2: passo curto)
    if (Math.random() < 0.3) { bot.setControlState('sneak', true); bot.setControlState(Math.random() < 0.5 ? 'left' : 'right', true); await espera(250); bot.clearControlStates() }
  }
}

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
    if (jogando) { await jogarEvento(bot); continue }
    if (evento) { bot.clearControlStates(); await espera(500); continue } // inscrito, esperando no saguão
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
      // Empurrão do servidor (Vietnã, carga de vento). Nesta versão o pacote já vem em blocos por tique,
      // mas o mineflayer ainda divide por 8000 (o formato antigo) e o figurante quase não se mexe:
      // aplica o valor como veio.
      bot._client.on('entity_velocity', p => {
        if (p.entityId !== bot.entity?.id || !p.velocity) return
        const v = p.velocity
        if (Math.abs(v.x) < 20 && Math.abs(v.y) < 20 && Math.abs(v.z) < 20) bot.entity.velocity.set(v.x, v.y, v.z)
      })
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

  // o evento acabou sozinho (alguém venceu): param de jogar e voltam a andar à toa, com a roupa de volta
  setInterval(async () => {
    if (!evento || !jogando) return
    const estado = await cmd(evento).catch(() => '')
    if (!/Nenhum aberto/.test(estado)) return
    jogando = false; evento = null
    for (const [i, b] of bots.entries()) {
      for (const [parte, item] of Object.entries(VISUAIS[i % VISUAIS.length])) await cmd(`minecraft:item replace entity ${b.username} ${SLOTS[parte]} with minecraft:${item}`)
    }
    console.log('  o evento acabou; figurantes à toa de novo')
  }, 5000)

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
    if (msg === '!brigar') {
      // lutam com o mob hostil mais perto ali mesmo (guardiões da câmara do tesouro, mobs), sem ir pra arena
      lutando = true; parados = false
      for (const [i, b] of bots.entries()) {
        const vis = VISUAIS[i % VISUAIS.length]
        if (!/sword|axe/.test(vis.mao || '')) await cmd(`minecraft:item replace entity ${b.username} weapon.mainhand with minecraft:${ARMA_DE_LUTA[i % ARMA_DE_LUTA.length]}`)
      }
      console.log('  brigando onde estão')
    }
    if (msg.startsWith('!evento')) {
      const id = msg.split(' ')[1]
      if (!EVENTOS.includes(id)) { console.log(`  evento? ${EVENTOS.join(', ')}`); return }
      evento = id; jogando = false; lutando = false
      console.log('  ' + await cmd(`${id} abrir`))
      for (const b of bots) {
        await cmd(`lp user ${b.username} permission set eventos.ip.ignorar true`)
        await cmd(`minecraft:clear ${b.username}`)
        b.chat(`/${id} entrar`)
        await espera(400)
      }
      console.log(`  inscritos no ${id}`)
    }
    if (msg === '!comecar' && evento) {
      console.log('  ' + await cmd(`${evento} iniciar`))
      // a preparação (contagem) leva uns segundos: eles começam a jogar logo, mas sem pressa
      setTimeout(() => { if (evento) jogando = true }, 3000)
    }
    if (msg === '!fim' && evento) {
      await cmd(`${evento} cancelar`)
      jogando = false; evento = null
      for (const [i, b] of bots.entries()) {
        for (const [parte, item] of Object.entries(VISUAIS[i % VISUAIS.length])) await cmd(`minecraft:item replace entity ${b.username} ${SLOTS[parte]} with minecraft:${item}`)
      }
      console.log('  evento encerrado')
    }
    if (msg === '!andem' || msg === '!parem') { lutando = false; jogando = false }
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
