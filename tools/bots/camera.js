// Câmera em trilho pra gravar vídeo (usada pelo figurantes.js; SÓ NO SERVIDOR DE TESTE).
// O jogador marca pontos (posição + pra onde olha) e a câmera desliza por eles numa curva suave
// (Catmull-Rom, velocidade constante, começo e fim suaves). A câmera é um item_display invisível
// com teleport_duration, que o cliente interpola entre os tiques; o jogador fica "espectando" ele.
// Os pontos ficam em cenas-camera.json, por cena, pra dá pra regravar a mesma tomada.
const fs = require('fs')
const path = require('path')
const { Rcon } = require('rcon-client')

const PASTA = process.env.MC_TESTE || 'C:\\mc-teste'
const ARQUIVO = path.join(__dirname, 'cenas-camera.json')
const OLHO = 1.62
const TAG = 'camera_brasa'

const props = Object.fromEntries(fs.readFileSync(path.join(PASTA, 'server.properties'), 'utf8').split(/\r?\n/)
  .filter(l => l.includes('=') && !l.startsWith('#')).map(l => [l.slice(0, l.indexOf('=')).trim(), l.slice(l.indexOf('=') + 1).trim()]))

let rcon
async function rc(cmd) {
  if (!rcon) rcon = await Rcon.connect({ host: 'localhost', port: Number(props['rcon.port'] || 25575), password: props['rcon.password'] })
  return String(await rcon.send(cmd)).replace(/§./g, '')
}
const numeros = s => (s.split('data:')[1] || '').match(/-?\d+(?:\.\d+)?(?:E-?\d+)?/gi)?.map(Number) || []
const espera = ms => new Promise(r => setTimeout(r, ms))

let cenas = {}
try { cenas = JSON.parse(fs.readFileSync(ARQUIVO, 'utf8')) } catch {}
let cena = 'cena'
let gravando = false
const salvar = () => fs.writeFileSync(ARQUIVO, JSON.stringify(cenas, null, 1))

// Catmull-Rom de um valor entre p1 e p2.
function cr(p0, p1, p2, p3, t) {
  const t2 = t * t, t3 = t2 * t
  return 0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3)
}

/** Pontos com o yaw "desenrolado" (sem pular de 179 pra -179). */
function preparar(pontos) {
  const r = pontos.map(p => ({ ...p }))
  for (let i = 1; i < r.length; i++) {
    let d = r[i].yaw - r[i - 1].yaw
    while (d > 180) d -= 360
    while (d < -180) d += 360
    r[i].yaw = r[i - 1].yaw + d
  }
  return r
}

/** Posição e olhar em s (0..1 do caminho todo), com velocidade parecida em todos os trechos. */
function amostrador(pontos) {
  const p = preparar(pontos)
  const trechos = []
  // o tempo de cada trecho conta a distância e também o giro (6 graus valem 1 bloco): assim uma
  // virada grande num trecho curto não sai como um chicote
  for (let i = 0; i < p.length - 1; i++) {
    const dist = Math.hypot(p[i + 1].x - p[i].x, p[i + 1].y - p[i].y, p[i + 1].z - p[i].z)
    const giro = Math.hypot(p[i + 1].yaw - p[i].yaw, p[i + 1].pitch - p[i].pitch) / 6
    trechos.push(Math.max(0.5, Math.hypot(dist, giro)))
  }
  const total = trechos.reduce((a, b) => a + b, 0)
  return (s) => {
    let d = s * total, i = 0
    while (i < trechos.length - 1 && d > trechos[i]) { d -= trechos[i]; i++ }
    const t = Math.min(1, d / trechos[i])
    const a = p[Math.max(0, i - 1)], b = p[i], c = p[i + 1], e = p[Math.min(p.length - 1, i + 2)]
    const v = k => cr(a[k], b[k], c[k], e[k], t)
    return { x: v('x'), y: v('y'), z: v('z'), yaw: v('yaw'), pitch: v('pitch') }
  }
}

const suave = s => s * s * (3 - 2 * s) // começa e termina devagar

async function onde(dono) {
  const [x, y, z] = numeros(await rc(`data get entity ${dono} Pos`))
  const [yaw, pitch] = numeros(await rc(`data get entity ${dono} Rotation`))
  const dim = (await rc(`data get entity ${dono} Dimension`)).match(/"([^"]+)"/)?.[1] || 'minecraft:overworld'
  return { x, y, z, yaw, pitch, dim }
}

async function avisar(dono, texto) {
  await rc(`tellraw ${dono} {"text":"[câmera] ${texto.replace(/"/g, "'")}","color":"gold"}`)
}

/** Trata um comando de chat da câmera. Devolve true se era da câmera. */
async function comando(dono, msg) {
  const [nome, arg] = msg.split(/\s+/)
  // relê o arquivo: dá pra preparar cenas fora do jogo (editando o cenas-camera.json)
  if (nome === '!cena' || nome === '!gravar' || nome === '!cenas') { try { cenas = JSON.parse(fs.readFileSync(ARQUIVO, 'utf8')) } catch {} }
  const pontos = () => (cenas[cena] = cenas[cena] || [])
  switch (nome) {
    case '!cena':
      cena = arg || 'cena'
      await avisar(dono, `cena "${cena}": ${pontos().length} pontos`)
      return true
    case '!ponto': {
      const p = await onde(dono)
      pontos().push(p); salvar()
      await avisar(dono, `ponto ${pontos().length} da cena "${cena}" marcado`)
      return true
    }
    case '!desfazer':
      pontos().pop(); salvar()
      await avisar(dono, `cena "${cena}": ${pontos().length} pontos`)
      return true
    case '!limpar':
      cenas[cena] = []; salvar()
      await avisar(dono, `cena "${cena}" zerada`)
      return true
    case '!cenas':
      await avisar(dono, Object.entries(cenas).map(([n, l]) => `${n} (${l.length})`).join(', ') || 'nenhuma')
      return true
    case '!parar':
      // sem a câmera, as funções que ainda estão agendadas não movem nada
      gravando = false
      await rc(`minecraft:kill @e[tag=${TAG}]`)
      return true
    case '!gravar': {
      if (gravando) return true
      const lista = pontos()
      if (lista.length < 2) { await avisar(dono, 'marque pelo menos 2 pontos com !ponto'); return true }
      await gravar(dono, lista, Math.max(2, Number(arg) || 10))
      return true
    }
  }
  return false
}

/**
 * Grava o percurso num datapack: uma função por tique, cada uma move a câmera e agenda a próxima
 * pra 1 tique depois. Assim o servidor anda a câmera exatamente uma vez por tique (pelo console,
 * o relógio do script e o do servidor não batiam e a câmera dava trancos).
 */
async function prepararPercurso(em, tiques, dim) {
  const pack = path.join(PASTA, props['level-name'] || 'world', 'datapacks', 'camera_brasa')
  const pasta = path.join(pack, 'data', 'camera_brasa', 'function', 't')
  fs.rmSync(pasta, { recursive: true, force: true })
  fs.mkdirSync(pasta, { recursive: true })
  fs.writeFileSync(path.join(pack, 'pack.mcmeta'), JSON.stringify({
    pack: { description: 'Câmera em trilho do BrasaMC (tools/bots/camera.js)', min_format: [107, 0], max_format: [107, 1] },
  }))
  for (let i = 0; i <= tiques; i++) {
    const p = em(suave(i / tiques))
    const linhas = [`execute in ${dim} run tp @e[tag=${TAG},limit=1] ${p.x.toFixed(3)} ${(p.y + OLHO).toFixed(3)} ${p.z.toFixed(3)} ${p.yaw.toFixed(2)} ${p.pitch.toFixed(2)}`]
    if (i < tiques) linhas.push(`schedule function camera_brasa:t/${i + 1} 1t`)
    fs.writeFileSync(path.join(pasta, `${i}.mcfunction`), linhas.join('\n') + '\n')
  }
  await rc('minecraft:reload')
  // espera o datapack aparecer (o reload termina depois da resposta)
  for (let t = 0; t < 30; t++) {
    await espera(500)
    if ((await rc('datapack list enabled')).includes('camera_brasa')) return true
  }
  return false
}

async function gravar(dono, lista, segundos) {
  gravando = true
  const em = amostrador(lista)
  const inicio = em(0)
  const dim = lista[0].dim || 'minecraft:overworld'
  // o console roda no mundo padrão: tudo vai com "execute in <mundo da cena>"
  const em_ = `execute in ${dim} run`
  const tiques = Math.round(segundos * 20)
  await avisar(dono, 'preparando o percurso...')
  if (!(await prepararPercurso(em, tiques, dim))) { gravando = false; await avisar(dono, 'o datapack da câmera não carregou'); return }
  await rc(`minecraft:kill @e[tag=${TAG}]`)
  await rc(`gamemode spectator ${dono}`)
  await rc(`${em_} minecraft:tp ${dono} ${inicio.x} ${inicio.y} ${inicio.z} ${inicio.yaw} ${inicio.pitch}`)
  await espera(500)
  await rc(`${em_} minecraft:summon item_display ${inicio.x} ${inicio.y + OLHO} ${inicio.z} {Tags:["${TAG}"],teleport_duration:10,Rotation:[${inicio.yaw}f,${inicio.pitch}f]}`)
  await espera(300)
  const r = await rc(`minecraft:spectate @e[tag=${TAG},limit=1] ${dono}`)
  if (/no entity|não encontrad/i.test(r)) { gravando = false; await avisar(dono, 'não consegui criar a câmera: ' + r); return }
  await avisar(dono, `contagem: 3`); await espera(1000)
  await avisar(dono, `2`); await espera(1000)
  await avisar(dono, `1`); await espera(1000)
  await rc('minecraft:function camera_brasa:t/0')
  const fimEm = Date.now() + tiques * 50 + 300
  while (gravando && Date.now() < fimEm) await espera(100)
  await espera(2000) // segura o último quadro (e deixa a suavização de 10 tiques chegar no fim)
  await rc(`execute as ${dono} run minecraft:spectate`)
  const fim = em(1)
  await rc(`${em_} minecraft:tp ${dono} ${fim.x} ${fim.y} ${fim.z} ${fim.yaw} ${fim.pitch}`)
  await rc(`minecraft:kill @e[tag=${TAG}]`)
  gravando = false
  await avisar(dono, 'pronto! (!gravar de novo repete a mesma tomada)')
}

async function fechar() {
  try { await rc(`minecraft:kill @e[tag=${TAG}]`); await rcon?.end() } catch {}
}

module.exports = { comando, fechar }
