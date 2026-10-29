// Mapa funcional do rankup: o spawn (com as barracas), as minas e as arenas definitivas.
// O spawn é o "spring" do EstruturasPlugin, construído pelo console: fechado, com a única saída
// encostando na avenida das minas, e ele mesmo cadastra as 32 barracas (área, NPC e holograma).
// O resto é só a parte que faz o servidor funcionar (pedra, caminhos, hologramas). Dá pra rodar de novo.
// O mundo fica salvo: só rode de novo pra refazer alguma parte.
//
//   node montar-mapa.js construir [partes...]   (servidor LIGADO) sem partes = minas spawn boss dicas arenas marcar
//     minas      os 12 poços (cheios de pedra) e a Avenida das Minas (EstruturasPlugin) em volta, com as oficinas
//     loja       só a Loja Oficial (portão leste do spawn): prédio, 7 vendedores, placas e quadros
//     mobs       a /warp mobs (portão oeste do spawn): abre o portão "MOBS" num spawn que já existe e
//                constrói o salão de estátuas, que cadastra os spawners no SpawnersPlugin
//     banco      só o Banco no fim da avenida (folha de cheque, depósito, extrato e Mine-sena), com o NPC,
//                e o pedaço do spawn onde ficava a barraquinha antiga dele, como era
//     oficinas   só as barraquinhas de serviço da avenida (OficinasPlugin: Ferreiro, Bruxa... e Cambista,
//                Banco e Compactador no fundo)
//     boss       a arena do boss ("toxica"), ao sul do spawn, colada no portão "BOSS"
//     dicas      os 12 NPCs de dicas pelo spawn (DicasPlugin), com as falas
//     tutorial   o painel "Como começar", ao lado do portão oeste do spawn (já vem junto com o spawn)
//     spawn      o spawn inteiro (EstruturasPlugin "spring"), as barracas dele e os hologramas da fonte
//     barracas   só as 32 barracas do spawn (redesenha e cadastra as que faltam; aluguéis e NPCs ficam)
//     arenas     saguão, X1 ("axepvp") e Gladiador ("coliseu"), bem longe um do outro, e limpa o que
//                sobrou das versões anteriores no mundo das arenas
//     marcar     spawn dos jogadores, warps e local do boss (com o bot da staff)
//     limpar-antigo   apaga o mapa antigo (praça, mercado, loja, portais e as barracas n1..n8, e1, e2).
//                     Só uma vez, ao trocar o mapa antigo por este, e ANTES do spawn: fora isso, apagaria
//                     as barracas e1 e e2 novas.
//   node montar-mapa.js cadastrar   (servidor DESLIGADO) arenas e configs nos arquivos
//   node montar-mapa.js cadastrar --minas                também as 12 minas, com os blocos planejados
//
// Os poços das minas ficam cheios de pedra, sem mina cadastrada: a staff marca com /mina admin pos1,
// pos2 e criar. Com "cadastrar --minas", elas já entram prontas e se enchem ao ligar (resetar-ao-ligar).
//
//          AVENIDA DAS MINAS (12 poços, norte, z -21 a -185)
//                             ║
//            portão "MINAS" (z -20, colado na avenida)
//               [SPAWN fechado, centro em z 43]
//        fonte no meio, barracas ao longo das avenidas
//            portão "BOSS" (z 106, colado na descida)
//     [ARENA DO BOSS, enterrada: piso em y 32, centro em z 195]
const fs = require('fs')
const path = require('path')
const t = require('./lib')

const PASTA = process.env.MC_TESTE || 'C:\\mc-teste'
const REPO = path.resolve(__dirname, '..', '..')
const CHAO = 64 // os pés ficam em y = 65

// ---------------------------------------------------------------------------
// Medidas
// ---------------------------------------------------------------------------
const MINA = { lado: 21, fundo: 15 } // poço de 21 x 21, 15 de profundidade (y 50..64)
// as minas, em ordem de rank, alternando oeste/leste ao longo da avenida norte
function posicaoDaMina(i) {
  const linha = Math.floor(i / 2), oeste = i % 2 === 0
  const zMax = -30 - 25 * linha, zMin = zMax - (MINA.lado - 1)
  const xMin = oeste ? -29 : 9, xMax = xMin + MINA.lado - 1
  const zc = (zMin + zMax) / 2
  return {
    // a pedra vai até o chão (y 50..64): por cima, a mina fica no nível da avenida
    min: [xMin, CHAO - MINA.fundo + 1, zMin], max: [xMax, CHAO, zMax],
    // ponto de teleporte na calçada, colado na borda do poço (a mão alcança o minério), olhando pra mina
    spawn: [oeste ? -6.5 : 7.5, CHAO + 1, zc + 0.5, oeste ? 90 : -90, 30],
    placaAntiga: [oeste ? -4 : 4, CHAO + 1, zc], // versões antigas punham uma placa aqui
  }
}

// Spawn (estrutura "spring"): ele acaba na face de fora dos portões, 63 blocos ao norte e ao sul do
// centro. Com o centro em z = 43, o portão norte fica em z = -20, colado na avenida das minas (z = -21).
const SPAWN = { x: 0, z: 43 }
// barracas que o spawn cadastra (b1..b28 normais, e1..e4 especiais) e as do mercado antigo
const BARRACAS_SPAWN = [...Array.from({ length: 28 }, (_, i) => `b${i + 1}`), 'e1', 'e2', 'e3', 'e4']
const BARRACAS_ANTIGAS = ['e1', 'e2', 'n1', 'n2', 'n3', 'n4', 'n5', 'n6', 'n7', 'n8']

// Arenas do EstruturasPlugin. No mundo arenas, o X1 ("axepvp") e o Gladiador ("coliseu") ficam a
// 1200 blocos um do outro (a plateia de um não vê o outro), e o boss ("toxica") fica no mundo spawn,
// colado no portão sul dele. A plateia entra por warp (plateia-x1, plateia-gladiador) e sai com /spawn.
// X1: piso de 43 x 27 (x -21..21 em volta do centro); cada lutador nasce no nicho fechado de uma
// parede curta (x -23 e 23), olhando pro outro; a plateia fica na galeria em cima (y 80).
const AXE = { nome: 'axe', centro: [-600, 0], pos1: [-622.5, 0.5, -90], pos2: [-576.5, 0.5, 90], plateia: [-599.5, 80, -14.5, 0] }
// Gladiador: o coliseu tem 213 x 189 de estrutura.
// A área (pos1/pos2, até logo abaixo do teto de vidro) e os 16 pontos de entrada são os do README
// do EstruturasPlugin, relativos ao centro.
const GLAD = {
  centro: [600, 0], min: [-76, 1, -64], max: [76, 10, 64], plateia: [600.5, 77, -65.5, 0], // plateia no deck de vidro
  pontos: [[38, 0], [19, 28], [0, 32], [-27, 23], [-38, 0], [-27, -23], [0, -32], [27, -23],
    [46, 22], [18, 42], [-14, 43], [-46, 22], [-46, -22], [-27, -39], [14, -43], [46, -22]],
}
// a arena do boss fica enterrada (piso 32 abaixo do chão, pra não aparecer por cima do spawn): a escadaria
// dela termina 88 blocos ao norte do centro, colada no portão sul do spawn (z = SPAWN.z + 63)
const BOSS = { centro: [SPAWN.x, SPAWN.z + 64 + 88], chao: CHAO - 32 }
const MOBS_PISO = CHAO - 16 // o salão da warp mobs fica meio enterrado: piso em y 48, teto de vidro em y 84
// o que sobrou das versões anteriores no mundo arenas (as 3 caixas de X1, a axepvp em (0, -80), os
// coliseus em (0, 100) e (0, 200) e a tóxica em (-135, 0)): sai tudo, só o saguão (x/z -7..7) fica
const ARENAS_ANTIGAS = [
  { min: [86, -14], max: [114, 94], altura: 12 },
  { min: [-34, -114], max: [34, -46], altura: 40 },
  { min: [-110, 54], max: [110, 298], altura: 44 },
  { min: [-196, -62], max: [-74, 62], altura: 64 },
]

// ---------------------------------------------------------------------------
// Fase 1: construir (servidor ligado)
// ---------------------------------------------------------------------------
const em = mundo => `execute in minecraft:${mundo} run`

/** fill em pedaços (o comando tem limite de blocos), com o chunk carregado. */
async function encher(mundo, [x1, y1, z1], [x2, y2, z2], bloco, modo = '') {
  const [ax, bx] = [Math.min(x1, x2), Math.max(x1, x2)], [ay, by] = [Math.min(y1, y2), Math.max(y1, y2)], [az, bz] = [Math.min(z1, z2), Math.max(z1, z2)]
  // o fill aceita até 32768 blocos: pedaços de 32 x 32, em fatias de até 32 de altura
  const passo = 32
  for (let x = ax; x <= bx; x += passo) {
    for (let z = az; z <= bz; z += passo) {
      const [ex, ez] = [Math.min(bx, x + passo - 1), Math.min(bz, z + passo - 1)]
      await t.console(`${em(mundo)} forceload add ${x} ${z} ${ex} ${ez}`)
      for (let y = ay; y <= by; y += passo) {
        const ey = Math.min(by, y + passo - 1)
        for (let tentativa = 0; tentativa < 20; tentativa++) {
          const r = await t.console(`${em(mundo)} fill ${x} ${y} ${z} ${ex} ${ey} ${ez} minecraft:${bloco} ${modo}`.trim())
          if (!/not loaded/.test(r)) break
          await t.espera(500)
        }
      }
      await t.console(`${em(mundo)} forceload remove ${x} ${z} ${ex} ${ez}`)
    }
  }
}

/** Caixa oca de parede (só o contorno), do chão até a altura. */
async function muro(mundo, [x1, z1], [x2, z2], y1, y2, bloco) {
  await encher(mundo, [x1, y1, z1], [x2, y2, z1], bloco)
  await encher(mundo, [x1, y1, z2], [x2, y2, z2], bloco)
  await encher(mundo, [x1, y1, z1], [x1, y2, z2], bloco)
  await encher(mundo, [x2, y1, z1], [x2, y2, z2], bloco)
}

const texto = (s, cor) => cor ? `{text:"${s}",color:"${cor}"}` : `"${s}"`
/**
 * Holograma (text_display com a tag "mapa") em cima do bloco, com as linhas [texto, cor]. Antes,
 * tira o holograma e a placa que estiverem ali (de uma montagem anterior), pra dar pra refazer.
 */
async function holograma(mundo, [x, y, z], linhas) {
  const partes = linhas.filter(([s]) => s).map(([s, c]) => texto(s, c)).join(',"\\n",')
  await t.console(`${em(mundo)} forceload add ${x} ${z}`)
  await t.console(`${em(mundo)} minecraft:kill @e[type=text_display,tag=mapa,x=${x + 0.5},y=${y},z=${z + 0.5},distance=..2]`)
  await t.console(`${em(mundo)} fill ${x} ${y} ${z} ${x} ${y} ${z} minecraft:air replace #minecraft:all_signs`)
  await t.console(`${em(mundo)} summon minecraft:text_display ${x + 0.5} ${y + 1.2} ${z + 0.5} {Tags:["mapa"],billboard:"center",shadow:1b,text:[${partes}]}`)
  await t.console(`${em(mundo)} forceload remove ${x} ${z}`)
}

/** Tira a placa que uma montagem antiga deixou nesse bloco (sem pôr nada no lugar). */
async function tirarPlaca(mundo, [x, y, z]) {
  await t.console(`${em(mundo)} forceload add ${x} ${z}`)
  await t.console(`${em(mundo)} fill ${x} ${y} ${z} ${x} ${y} ${z} minecraft:air replace #minecraft:all_signs`)
  await t.console(`${em(mundo)} forceload remove ${x} ${z}`)
}

/** Portal: moldura colorida, placa de pressão na frente e bloco de comando embaixo do chão. */
async function portal(mundo, [x, z], cor, comando, linhas) {
  await encher(mundo, [x - 2, CHAO + 1, z - 2], [x - 2, CHAO + 4, z + 2], `${cor}_concrete`)
  await encher(mundo, [x - 2, CHAO + 1, z - 1], [x - 2, CHAO + 3, z + 1], `${cor}_stained_glass`)
  await t.console(`${em(mundo)} forceload add ${x} ${z}`)
  await t.console(`${em(mundo)} setblock ${x} ${CHAO - 1} ${z} minecraft:command_block{Command:"${comando.replace(/"/g, '\\"')}"} replace`)
  await t.console(`${em(mundo)} setblock ${x} ${CHAO} ${z} minecraft:polished_andesite replace`)
  await t.console(`${em(mundo)} setblock ${x} ${CHAO + 1} ${z} minecraft:stone_pressure_plate replace`)
  await t.console(`${em(mundo)} forceload remove ${x} ${z}`)
  await holograma(mundo, [x + 2, CHAO + 1, z], linhas)
}

/** O mapa antigo: praça, mercado, loja e portais, e as barracas dele. Só na troca pelo mapa novo. */
async function limparAntigo() {
  const W = 'spawn'
  console.log('\n== Limpando o spawn antigo (praça, mercado, loja e portais) ==')
  for (const id of BARRACAS_ANTIGAS) await t.console(`barraca remover ${id}`)
  await t.console(`${em(W)} minecraft:kill @e[type=text_display,tag=mapa]`)
  // tudo ao sul da avenida das minas (z >= -20), inclusive os blocos de comando dos portais (y 63)
  await encher(W, [-61, CHAO - 1, -20], [80, CHAO + 6, 45], 'air')
}

async function construirMinas() {
  const W = 'spawn'
  console.log('\n== Poços das minas (norte) ==')
  const planejadas = minasPlanejadas()
  for (let i = 0; i < planejadas.length; i++) {
    const p = posicaoDaMina(i)
    // poço cheio de pedra com borda de tijolo: a staff cria a mina em cima (ou o "cadastrar --minas")
    await encher(W, [p.min[0] - 1, p.min[1] - 1, p.min[2] - 1], [p.max[0] + 1, CHAO, p.max[2] + 1], 'stone_bricks')
    await encher(W, p.min, p.max, 'stone')
    await tirarPlaca(W, p.placaAntiga) // o holograma da mina é do Rankup, quando ela é criada
  }
  // o recinto em volta (EstruturasPlugin "minas"): muralha, portão colado no caminho do spawn,
  // avenida, praças e calçadas. Ele nunca escreve nos poços nem na borda deles.
  console.log('\n== Avenida das Minas (EstruturasPlugin: minas) ==')
  await estrutura('minas', W, [0, -21])
  await construirOficinas()
}

/**
 * As barraquinhas de serviço da avenida (EstruturasPlugin "oficinas"): 4 na praça de entrada e 2 no
 * fundo, com os NPCs do OficinasPlugin e a mesa da bruxa embaixo, e o Banco no meio do fundo, entre o
 * Compactador e o Cambista (estrutura "banco"). Sempre depois da avenida, que passa por cima. Os guias
 * que ficavam na entrada de cada mina saíram (o /mina e o holograma já dizem tudo).
 */
async function construirOficinas() {
  console.log('\n== Oficinas da mina (Ferreiro, Picareteiro, Bruxa, Sábio, Cambista, Compactador) ==')
  await t.console('execute in minecraft:spawn run forceload add -30 -185 30 -20')
  await t.espera(1500)
  await estrutura('oficinas', 'spawn', [0, -21])
  await construirBanco()
  for (const mina of minasPlanejadas()) await t.console(`oficina remover mina-${mina.id}`) // os guias antigos
  await t.console('execute in minecraft:spawn run forceload remove -30 -185 30 -20')
}

/**
 * Constrói uma estrutura do EstruturasPlugin com os pés no centro dado (o piso fica no CHAO) e
 * espera terminar.
 */
async function estrutura(id, mundo, [x, z], chao = CHAO) {
  const r = await t.console(`estrutura construir ${id} ${mundo} ${x} ${chao + 1} ${z}`)
  console.log(`  ${r}`)
  for (let i = 0; i < 240; i++) { // o spawn inteiro tem uns 300 mil blocos a 8 mil por tick: menos de 1 min
    await t.espera(1000)
    if (/Nenhuma/.test(await t.console('estrutura status'))) break
  }
}

/** Uma estrutura no centro do spawn, conferindo as barracas cadastradas depois. */
async function estruturaNoSpawn(id) {
  await estrutura(id, 'spawn', [SPAWN.x, SPAWN.z])
  const lista = await t.console('barraca lista')
  console.log(`  barracas: ${BARRACAS_SPAWN.filter(id => new RegExp(`\\b${id}\\b`).test(lista)).length} de ${BARRACAS_SPAWN.length}`)
}

// Painel "Como começar": um holograma grande só, no fim da avenida oeste (à esquerda de quem nasce
// olhando pro norte), do lado sul do portão "MOBS" (no meio ele taparia o túnel) e sempre virado pra
// quem olha.
const TUTORIAL = { pos: [-41, 10], altura: 2.2, escala: 1.3, linhas: [
  ['✦ COMO COMEÇAR ✦', 'gold', true],
  ['', null],
  ['1. Minere: portão MINAS, ao norte  (/mina)', 'white'],
  ['    Na entrada: Ferreiro, Bruxa, Picareteiro e Sábio', 'gray'],
  ['2. Na mina, o que você quebra já vira dinheiro', 'white'],
  ['    Loja Oficial: portão LESTE  (/warp loja)', 'gray'],
  ['3. Suba de rank: /rankup  (/ranks mostra todos)', 'white'],
  ['4. Todo dia: /diaria e /missoes', 'white'],
  ['5. Comércio: barracas pelo spawn, clique no vendedor', 'white'],
  ['6. Mobs (drops, XP e skills): portão OESTE  (/warp mobs)', 'white'],
  ['7. Boss: portão BOSS, ao sul  (/boss proximo)', 'white'],
  ['8. PvP: /duelo e /gladiador', 'white'],
  ['9. Seu terreno: /terreno auto  ·  Recursos: /recursos', 'white'],
  ['10. Benefícios: /vip tiers', 'white'],
  ['', null],
  ['Os NPCs pelo spawn dão mais dicas: clique neles!', 'aqua'],
] }

async function construirTutorial() {
  const W = 'spawn'
  console.log('\n== Painel "Como começar" ==')
  // os hologramas soltos das versões antigas (em volta da fonte) e o painel antigo saem
  await t.console(`${em(W)} forceload add ${SPAWN.x - 48} ${SPAWN.z - 16} ${SPAWN.x + 16} ${SPAWN.z + 16}`)
  await t.espera(1500)
  await t.console(`execute in minecraft:${W} positioned ${SPAWN.x} ${CHAO + 1} ${SPAWN.z} run minecraft:kill @e[type=text_display,tag=mapa,distance=..16]`)
  // o painel antigo (no meio da avenida, onde agora é o portão) e o atual, do lado dele
  await t.console(`execute in minecraft:${W} positioned ${SPAWN.x - 42} ${CHAO + 2} ${SPAWN.z} run minecraft:kill @e[type=text_display,tag=mapa,distance=..14]`)
  const [dx, dz] = TUTORIAL.pos
  const partes = TUTORIAL.linhas.map(([s, cor, negrito]) =>
    `{text:"${s}"${cor ? `,color:"${cor}"` : ''}${negrito ? ',bold:1b' : ''}}`).join(',"\\n",')
  await t.console(`${em(W)} summon minecraft:text_display ${SPAWN.x + dx + 0.5} ${CHAO + TUTORIAL.altura} ${SPAWN.z + dz + 0.5} `
    + `{Tags:["mapa"],billboard:"vertical",shadow:1b,line_width:400,background:${0xB0101018 | 0},`
    + `transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[${TUTORIAL.escala}f,${TUTORIAL.escala}f,${TUTORIAL.escala}f]},`
    + `text:[${partes}]}`)
  await t.console(`${em(W)} forceload remove ${SPAWN.x - 48} ${SPAWN.z - 16} ${SPAWN.x + 16} ${SPAWN.z + 16}`)
}

async function construirBarracas() {
  console.log('\n== Barracas do spawn (EstruturasPlugin: spring-barracas) ==')
  await estruturaNoSpawn('spring-barracas')
}

async function construirSpawn() {
  const W = 'spawn'
  console.log('\n== Spawn (EstruturasPlugin: spring) ==')
  await estruturaNoSpawn('spring')
  // a loja e a warp mobs encostam nos portões leste e oeste do spawn: refaz depois dele
  await construirLoja()
  await construirSalaoMobs()

  await construirTutorial()
}

/**
 * O Banco (EstruturasPlugin "banco") no fim da Avenida das Minas, entre o Compactador e o Cambista,
 * com o Bancário do OficinasPlugin. Antes ele era uma barraquinha no quadrante nordeste do spawn: o
 * "spring-banco" devolve aquele pedaço do spawn como era (só mexe em x 26..34, z -18..-10 a partir do
 * centro). Chamado pelas oficinas (precisa da área do fundo carregada).
 */
async function construirBanco() {
  console.log('\n== Banco (EstruturasPlugin: banco) e o spawn sem a barraquinha antiga ==')
  await t.console(`execute in minecraft:spawn run forceload add ${SPAWN.x + 26} ${SPAWN.z - 18} ${SPAWN.x + 34} ${SPAWN.z - 10}`)
  await t.console('execute in minecraft:spawn run forceload add -10 -186 10 -171')
  await t.espera(1500)
  await estrutura('spring-banco', 'spawn', [SPAWN.x, SPAWN.z])
  await estrutura('banco', 'spawn', [0, -21])
  await t.console(`execute in minecraft:spawn run forceload remove ${SPAWN.x + 26} ${SPAWN.z - 18} ${SPAWN.x + 34} ${SPAWN.z - 10}`)
  await t.console('execute in minecraft:spawn run forceload remove -10 -186 10 -171')
}

/**
 * A Loja Oficial (EstruturasPlugin "loja"), do lado de fora do portão leste do spawn: o prédio, os 7
 * vendedores do EconomyShopPlugin, as placas dos balcões e os quadros.
 */
async function construirLoja() {
  console.log('\n== Loja Oficial (EstruturasPlugin: loja) ==')
  await t.console('execute in minecraft:spawn run forceload add 60 10 128 76')
  await t.espera(2000)
  await estrutura('loja', 'spawn', [SPAWN.x, SPAWN.z])
  await t.console('execute in minecraft:spawn run forceload remove 60 10 128 76')
}

/**
 * A /warp mobs: o portão "MOBS" no oeste do spawn ("spring-oeste", só aquele pedaço, pra não mexer no
 * resto de um spawn que já existe), o salão de estátuas ("mobs") colado nele e o painel "Como começar"
 * do lado do portão. O salão cadastra a área e os spawners das estátuas no SpawnersPlugin.
 */
async function construirMobs() {
  console.log('\n== Portão oeste do spawn (EstruturasPlugin: spring-oeste) ==')
  await t.console('execute in minecraft:spawn run forceload add -64 24 -34 62')
  await t.espera(1500)
  await estrutura('spring-oeste', 'spawn', [SPAWN.x, SPAWN.z])
  await t.console('execute in minecraft:spawn run forceload remove -64 24 -34 62')
  await construirSalaoMobs()
  await construirTutorial()
}

async function construirSalaoMobs() {
  console.log('\n== Warp mobs (EstruturasPlugin: mobs) ==')
  await t.console('execute in minecraft:spawn run forceload add -233 -5 -62 91')
  await t.espera(3000)
  // os mobs, os hologramas e o letreiro do salão (de qualquer versão antiga dele) saem antes
  await t.console('execute in minecraft:spawn run minecraft:kill @e[type=!player,x=-234,y=20,z=0,dx=172,dy=90,dz=86]')
  await estrutura('mobs', 'spawn', [SPAWN.x, SPAWN.z])
  // letreiro no vestíbulo (lá embaixo, depois da escadaria), por cima da passagem pro salão
  const [x, y, z] = [-90, MOBS_PISO + 7, SPAWN.z]
  await t.console(`execute in minecraft:spawn positioned ${x} ${y} ${z} run minecraft:kill @e[type=text_display,tag=mapa,distance=..4]`)
  const linhas = [['⚔ WARP MOBS ⚔', 'gold', true], ['Mate os mobs das estátuas: drops, XP e skills', 'white'],
    ['Os mobs não passam da linha vermelha', 'gray']]
  const partes = linhas.map(([s, cor, negrito]) => `{text:"${s}",color:"${cor}"${negrito ? ',bold:1b' : ''}}`).join(',"\\n",')
  await t.console(`execute in minecraft:spawn run summon minecraft:text_display ${x + 0.5} ${y} ${z + 0.5} `
    + `{Tags:["mapa"],billboard:"vertical",shadow:1b,line_width:300,`
    + `transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[1.5f,1.5f,1.5f]},`
    + `text:[${partes}]}`)
  await t.console('execute in minecraft:spawn run forceload remove -233 -5 -62 91')
}

async function construirArenas() {
  const W = 'arenas'
  console.log('\n== Saguão das arenas ==')
  await encher(W, [-7, CHAO, -7], [7, CHAO, 7], 'stone_bricks')
  await muro(W, [-7, -7], [7, 7], CHAO + 1, CHAO + 1, 'stone_brick_wall')
  await portal(W, [0, 5], 'light_blue', `execute as @p[distance=..2] in minecraft:spawn run minecraft:tp @s ${SPAWN.x + 0.5} 65 ${SPAWN.z + 0.5} 180 0`,
    [['Voltar', 'aqua'], ['pro spawn', null], ['', null], ['', null]])
  await holograma(W, [-3, CHAO + 1, -5], [['/duelo', 'aqua'], ['<jogador> [valor]', null], ['/duelo assistir', 'gray'], ['', null]])
  await holograma(W, [3, CHAO + 1, -5], [['/gladiador', 'aqua'], ['entrar quando', null], ['abrir', null], ['', null]])

  console.log('\n== Limpando as arenas antigas ==')
  for (const a of ARENAS_ANTIGAS) await encher(W, [a.min[0], CHAO - 1, a.min[1]], [a.max[0], CHAO + a.altura, a.max[1]], 'air')

  console.log('\n== X1: axepvp ==')
  await estrutura('axepvp', W, AXE.centro)
  console.log('\n== Gladiador: coliseu ==')
  await estrutura('coliseu', W, GLAD.centro)
}

// NPCs de dicas (DicasPlugin). Os 4 primeiros ficam em volta da fonte, nas diagonais entre as avenidas,
// virados pra fora (o do norte pro lado das minas, o do sul pro lado do boss); os outros ficam perto
// dos portões, no fim das avenidas e nos jardins, virados pra fonte. Posição relativa ao centro do
// spawn; falas com cores do Minecraft (&).
const DICAS = [
  { id: 'minerador', skin: 'd267bf0162a31605043c68901412f94eb3e76bc2d97bb55fb9167d958d84cd13', nome: '&6Velho Minerador', pos: [-6, -6], falas: [
    '&fAs minas ficam depois do portão &bMINAS&f, ao norte. Use &e/mina&f pra ver as que o seu rank libera.',
    '&fNa mina, tudo o que você quebra vira dinheiro na hora! O resto se vende na &6Loja Oficial&f (&e/warp loja&f). E &e/rankup&f te leva pro próximo rank. Eu levei 40 anos pra deixar de ser Camponês...',
    '&fCuidado na mina de &bdiamante&f: meu sobrinho entrou lá com uma picareta de madeira e saiu com uma de diamante. E sem a dignidade.',
    '&fA mina se enche de novo sozinha de tempos em tempos. Tá vazia? Espere um pouco, ou vá incomodar outra mina.',
    '&fDe vez em quando a picareta acha um &dFragmento Arcano&f. Guarde! O &dCambista&f, no fundo da avenida, troca por encantamento acima do máximo.',
    '&fMuito minério ocupando a mochila? O &7Compactador&f, no fundo da avenida, transforma 9 em 1 bloco. De graça, e ainda desfaz.',
  ] },
  { id: 'mercadora', skin: 'c57948ce86408ba0ceff3f104b03e6567b0ab07f52c8d261c9a6893d5d92a366', nome: '&dDona Cida, a Mercadora', pos: [6, -6], falas: [
    '&fAs barracas do spawn são de vocês! Alugue uma com &e/barraca alugar&f e um vendedor com a SUA cara trabalha por você.',
    '&fCom o item na mão: &e/barraca vender 64 150&f. Pronto: ele vende 64 por R$ 150 enquanto você minera.',
    '&fAs barracas com &6★&f vão a leilão. Da última vez, dois clãs brigaram tanto que o vencedor pagou o preço de uma casa por uma barraca.',
    '&fPra mandar dinheiro pra alguém, é no &e/pix&f. Eu aceito gorjeta, viu?',
  ] },
  { id: 'veterano', skin: '8ca973d24e8b40de38ee4d7bb21fee88c3f9672f0ac30ae5bd7dcfa2a265781f', nome: '&cVeterano Bartolomeu', pos: [-6, 6], falas: [
    '&fMuito cuidado ao enfrentar o boss... o primo do cunhado de um conhecido meu foi morto pela &dFeiticeira Maldita&f.',
    '&fO portão &cBOSS&f, ao sul, desce até a arena. Lá embaixo tem um degrau alto: desceu, só volta com &e/spawn&f!',
    '&fQuem dá o último golpe no boss vira campeão e leva o melhor prêmio. Quem só assiste leva... boas lembranças.',
    '&fTodo &cdomingo&f vem o &8Flagelo das Trevas&f. Quem derrubar ele leva até um VIP de presente... se sobreviver.',
    '&fÀs vezes o boss vem &6✦ ELITE ✦&f: o dobro de vida, o dobro de dano e o dobro de prêmio. Eu, nesses dias, fico em casa.',
  ] },
  { id: 'guia', skin: 'eeaf43c747497e20fe0667d67810aa1f3cb0aeb96fcc94b7ce18f5d165c8284d', nome: '&aZé, o Guia', pos: [6, 6], falas: [
    '&fBem-vindo! Pegue sua recompensa de todo dia com &e/diaria&f. Quanto mais dias seguidos, melhor.',
    '&fQuer um X1? &e/duelo <jogador> <valor>&f. Quem perde, perde os itens. Quem ganha, ganha o meu respeito.',
    '&fNo &6Gladiador&f é todo mundo contra todo mundo. Meu conselho: no fim, a borda fecha no centro. Não seja o último a perceber.',
    '&fQuer um cantinho seu? &e/terreno&f. Madeira e bicho você acha no mundo de &e/recursos&f.',
    '&fMorreu e não achou suas coisas? Olhe o &e/achados&f: o que a limpeza recolheu do chão fica guardado por 3 dias, por uma taxinha.',
    '&fVIP tem &e/montaria&f: um cavalo na hora, bem mais rápido que a pé. Só não entra nas minas nem na arena do boss.',
  ] },
  { id: 'guarda-minas', skin: '6ee1acb2b58e2db54aa7668be2eda0f57d6b81e186d1ee87a530354a2f2a3ef8', nome: '&7Guarda do Portão', pos: [6, -38], virado: 'centro', falas: [
    '&fDaqui pra frente é a &bAvenida das Minas&f. Cada mina tem um holograma com o rank, o que tem nela e quando enche de novo.',
    '&fSó minera quem tem o rank da mina. Não adianta me olhar assim, a regra não é minha.',
    '&f&e/mina&f abre o menu das minas: clicou, foi. Bem mais rápido que andar, mas você perde a paisagem.',
    '&fNas minas, o que você quebra é vendido na hora. VIP ainda ganha de 3% a 12% a mais em toda venda.',
  ] },
  { id: 'ranzinza', skin: 'd19038bc9e56e655f1ba2e47b27e758791dbcca17ea2026e5525becb81f86d5c', nome: '&8Dona Neusa, a Ranzinza', pos: [-6, -38], virado: 'centro', falas: [
    '&fNo meu tempo não tinha &e/rankup max&f. A gente subia um rank por vez e ainda agradecia!',
    '&fChegou no &6Rei&f? Tem o &e/prestigio&f: você volta pro começo, mas com uma estrelinha. Eu acho uma bobagem. Tenho três.',
    '&fNão empurra! As minas se enchem sozinhas. Quem estiver dentro na hora vai pro ponto de teleporte, inteirinho.',
    '&fEsse povo de hoje quer tudo de graça. Faz as &e/missoes&f, menino, que paga bem.',
    '&fPicareta gasta? No meu tempo a gente jogava fora. Hoje tem o &7Ferreiro&f, na avenida, que conserta. Moleza.',
  ] },
  { id: 'sentinela-boss', skin: '2241cc1466e4bde22f0756b88f79fa4def38e1dcc66b7522ab81558534c36b0a', nome: '&cSentinela do Portão', pos: [-6, 38], virado: 'centro', falas: [
    '&fTem boss &ctodo dia às 20h&f. No &cdomingo&f é o &8Flagelo das Trevas&f. Dez minutos antes, o chat avisa. &e/boss proximo&f diz quando.',
    '&fQuem dá o último golpe vira &6campeão&f e ganha a tag. Quem bate mais aparece no ranking de dano.',
    '&fO boss só espera &c1 hora&f. Se ninguém derrotar, ele vai embora e o mal reina: XP de skill &c-10%&f até alguém derrubar o próximo.',
    '&fQuem morre na arena deixa os itens lá. Não leve nada que você não aceita perder.',
    '&fA arena tem cara de tóxica, mas pode pisar à vontade. O que mata mesmo é o boss. E o orgulho.',
    '&fAtenção: a arena do boss é o único lugar do spawn com &cPvP&f. Lá dentro, o colega do lado também pode te bater.',
  ] },
  { id: 'leiloeiro', skin: '78a757b18d9121820b77937c2bd2dab78652941881cf7115cf9f8c96687f8e04', nome: '&6Leiloeiro Matias', pos: [38, -6], virado: 'centro', falas: [
    '&fAs barracas &6★&f vão a leilão quando ficam livres: &e/barraca lance <id> <valor>&f. Cada lance precisa ser 5% maior.',
    '&fLance nos últimos 30 segundos estende o leilão. Aqui ninguém ganha no susto!',
    '&fFoi superado? Seu dinheiro volta na hora. Eu sou leiloeiro, não ladrão.',
    '&fBarraca normal pode renovar sozinha: &e/barraca autorenovar&f, enquanto tiver saldo na conta.',
  ] },
  { id: 'mestre-armas', skin: '3f230b7b1b09b471491a61675a3efa3ba65522cc4d004090d28ee6f3d133b822', nome: '&4Mestre de Armas Duda', pos: [-36, -6], virado: 'centro', falas: [
    '&fAqui o combate é à moda antiga: sem barra de recarga, todo golpe sai com o dano inteiro. E quem alterna as duas mãos bate o dobro.',
    '&fBloquear com a espada tira metade do dano. Mas com escudo na outra mão, a espada não bloqueia: escolha.',
    '&fDá pra lutar com uma arma em cada mão, e cada mão treina a skill da própria arma. Veja em &e/skills&f.',
    '&fTem recompensa pela sua cabeça? Confira em &e/bounty lista&f. Se não tiver, é porque você é bonzinho demais.',
    '&fLevante o escudo bem na hora do golpe: o &6Escudo Perfeito&f anula o dano e deixa o outro tonto por um segundo.',
    '&fSkill no 50? &e/skills prestigio <skill>&f volta pro 1, mas cada prestígio deixa ela mais forte. E no sábado e no domingo o XP de skill vale o dobro!',
  ] },
  { id: 'banqueira', skin: '2dcabf4751f4ad60c6e92d0fc9724fe252474de90c5d4ed8f62b15633455f42d', nome: '&2Banqueira Rosa', pos: [25, -9], virado: 'centro', falas: [
    '&fPra pagar alguém: &e/pix pagar <jogador> <valor>&f. Rápido, seguro e sem fila.',
    '&fConta que se repete? &e/pix agendar <jogador> <valor> <dias>&f paga agora e de novo a cada tantos dias.',
    '&fQuer dar dinheiro de presente? Compre uma folha de cheque com o &6Bancário&f, no Banco do fim da Avenida das Minas, e use &e/cheque <valor>&f. Quem ganhar deposita com ele.',
    '&fO preço da loja muda com a oferta. Olhe o &e/shop&f antes de vender tudo de uma vez.',
    '&fQuer saber pra onde foi seu dinheiro? O &6Bancário&f te dá o extrato num livro, com tudo o que entrou e saiu.',
    '&fJá jogou na &6Mine-sena&f? Bilhete de R$ 500 no Bancário, sorteio todo dia às 18h, e o pote inteiro vai pra quem acertar.',
  ] },
  { id: 'escriba', skin: 'a6dd9b9c598e6f51ea5f5ad5c74ba05388c72ea3da996e3e34a34d5aba46ee68', nome: '&9Escriba dos Clãs', pos: [-30, 12], virado: 'centro', falas: [
    '&fJunte os amigos: &e/cla criar&f funda um clã, e &e/cla convidar&f chama a turma.',
    '&fO clã tem banco em comum (&e/cla banco&f) e chat próprio: &e/cc <mensagem>&f.',
    '&fNo &6Gladiador por clã&f, ninguém do mesmo clã se machuca. Já a briga pelo prêmio no grupo... aí é com vocês.',
    '&fO &e/cla top&f mostra os clãs mais fortes. O meu está em último, mas por escolha própria.',
  ] },
  { id: 'juca', skin: '7a8ab2267ae443e83b05e3a8d67b8b760adfaad335c9047338c0e1774a65c372', nome: '&aSeu Juca do Terreno', pos: [12, 30], virado: 'centro', falas: [
    '&fQuer um lugar só seu? &e/terreno auto&f compra o primeiro terreno livre.',
    '&fTerreno paga imposto! Ligue o &e/terreno autopagar&f e esqueça. Se atrasar muito, o terreno vai a leilão.',
    '&fChame os amigos pra construir junto com &e/terreno amigo&f.',
    '&fMadeira, bicho e peixe, tem tudo no mundo de &e/recursos&f. Ele é refeito de tempos em tempos, então não construa casa lá!',
  ] },
]

async function construirDicas() {
  console.log('\n== NPCs de dicas (DicasPlugin) ==')
  for (const npc of DICAS) {
    const [dx, dz] = npc.pos
    // de costas pra fonte, ou (virado: 'centro') de frente pra ela
    const yaw = Math.round((npc.virado === 'centro' ? Math.atan2(dx, -dz) : Math.atan2(-dx, dz)) * 180 / Math.PI)
    await t.console(`dica remover ${npc.id}`) // refaz igual
    await t.console(`dica criar ${npc.id} em spawn ${SPAWN.x + dx + 0.5} ${CHAO + 1} ${SPAWN.z + dz + 0.5} ${yaw} ${npc.nome}`)
    for (const fala of npc.falas) await t.console(`dica fala ${npc.id} ${fala}`)
    if (npc.skin) await t.console(`dica skin ${npc.id} textura:${npc.skin}`)
  }
}

async function construirBoss() {
  console.log('\n== Boss: toxica (ao sul do spawn) ==')
  await estrutura('toxica', 'spawn', BOSS.centro, BOSS.chao) // ela mesma roda o /boss setlocal no centro
}

/** O bot da staff (op por alguns segundos) marca o que os comandos pedem de posição. */
async function marcarComBot() {
  console.log('\n== Spawn, warps e boss ==')
  // jogador novo nasce na fonte do spawn (no 26.2 o setworldspawn vale pro servidor todo)
  await t.console(`${em('spawn')} setworldspawn ${SPAWN.x} ${CHAO + 1} ${SPAWN.z}`)
  await t.console('lp group default permission set essentials.spawn true')
  await t.console('lp group default permission set essentials.warp true')
  await t.console('lp group default permission set essentials.warp.list true')
  const staff = await t.criarBot('BotStaff')
  await t.console('op BotStaff')
  await t.console('gamemode creative BotStaff')
  const ir = async (mundo, x, y, z, yaw) => {
    await t.console(`${em(mundo)} minecraft:tp BotStaff ${x} ${y} ${z} ${yaw} 0`)
    await t.espera(1200)
  }
  const comando = async (texto) => {
    const msgs = await t.capturar(staff, () => staff.chat(texto))
    console.log(`  ${texto}  →  ${msgs.join(' | ')}`)
  }
  await ir('spawn', SPAWN.x + 0.5, CHAO + 1, SPAWN.z + 0.5, 180)
  await comando('/setspawn')
  await comando('/setspawn newbies')
  for (const [nome, mundo, x, z, yaw, y = CHAO + 1] of [
    // mercado: na avenida leste do spawn, entre as barracas
    ['minas', 'spawn', 0.5, -24.5, 180], ['mercado', 'spawn', SPAWN.x + 16.5, SPAWN.z + 0.5, -90],
    ['arenas', 'arenas', 0.5, 0.5, 180], ['terrenos', 'terrenos', 34.5, 34.5, 0],
    ['loja', 'spawn', 67.5, SPAWN.z + 0.5, -90], // logo depois da porta da Loja Oficial, olhando pro salão
    ['mobs', 'spawn', -85.5, SPAWN.z + 0.5, 90, MOBS_PISO + 1], // no vestíbulo da warp mobs (pé da escadaria), olhando pras estátuas
    ['boss', 'spawn', BOSS.centro[0] + 0.5, BOSS.centro[1] - 42.5, 0, BOSS.chao + 1], // logo depois do túnel da tóxica, olhando pro centro
    ['plateia-x1', 'arenas', AXE.plateia[0], AXE.plateia[2], AXE.plateia[3], AXE.plateia[1]],
    ['plateia-gladiador', 'arenas', GLAD.plateia[0], GLAD.plateia[2], GLAD.plateia[3], GLAD.plateia[1]],
  ]) {
    await ir(mundo, x, y, z, yaw)
    await comando(`/setwarp ${nome}`)
  }
  // os portais do spawn antigo não existem mais
  await t.console('delwarp portais')
  await t.console('deop BotStaff')
  await t.console('gamemode survival BotStaff')
  await ir('spawn', SPAWN.x + 0.5, CHAO + 1, SPAWN.z + 0.5, 180)
  staff.quit()
}

// ---------------------------------------------------------------------------
// Fase 2: cadastrar (servidor desligado)
// ---------------------------------------------------------------------------
/** As minas do tools/economia/minas-planejadas.yml (id, rank, blocos), em ordem. */
function minasPlanejadas() {
  const texto = fs.readFileSync(path.join(REPO, 'tools', 'economia', 'minas-planejadas.yml'), 'utf8')
  const nomes = rankNomes()
  const minas = []
  for (const m of texto.matchAll(/^  (\w+):\s*\n\s+rank: (\w+)\s*\n\s+blocos: \{([^}]*)\}/gm)) {
    const blocos = Object.fromEntries(m[3].split(',').map(p => p.split(':').map(s => s.trim())).map(([b, w]) => [b.toUpperCase(), Number(w)]))
    minas.push({ id: m[1], rank: m[2], nomeRank: nomes[m[2]] || m[2], blocos })
  }
  if (minas.length !== 12) throw new Error(`esperava 12 minas em minas-planejadas.yml, achei ${minas.length}`)
  return minas
}

function rankNomes() {
  const cfg = fs.readFileSync(path.join(REPO, 'rankup-plugin', 'src', 'main', 'resources', 'config.yml'), 'utf8')
  return Object.fromEntries([...cfg.matchAll(/- id: (\w+)\s*\n\s+nome: "([^"]+)"/g)].map(m => [m[1], m[2]]))
}

const local = (mundo, [x, y, z], yaw = 0, pitch = 0, recuo = '    ') =>
  `${recuo}==: org.bukkit.Location\n${recuo}world_key: minecraft:${mundo}\n${recuo}x: ${x}\n${recuo}y: ${y}\n${recuo}z: ${z}\n${recuo}pitch: ${pitch}\n${recuo}yaw: ${yaw}\n`

/** Troca (ou acrescenta) uma linha "chave: valor" dentro de uma seção de primeiro nível do YAML. */
function trocarNoYaml(arquivo, secao, chave, valor) {
  const original = fs.readFileSync(arquivo, 'utf8')
  const crlf = original.includes('\r\n')
  let s = original.replace(/\r\n/g, '\n') // o "." da regex não pega o \r das linhas do Windows
  const re = new RegExp(`(^${secao}:\\s*\\n(?:[ \\t].*\\n|\\s*\\n)*?[ \\t]+${chave}:)[^\\n]*`, 'm')
  if (!re.test(s)) throw new Error(`não achei ${secao}.${chave} em ${arquivo}`)
  s = s.replace(re, `$1 ${valor}`)
  fs.writeFileSync(arquivo, crlf ? s.replace(/\n/g, '\r\n') : s)
}

function cadastrar(comMinas) {
  const P = path.join(PASTA, 'plugins')
  let y
  if (comMinas) {
    console.log('== minas.yml (12 minas no mundo spawn; as antigas saem) ==')
    y = 'minas:\n'
    minasPlanejadas().forEach((m, i) => {
      const p = posicaoDaMina(i)
      y += `  ${m.id}:\n    mundo: spawn\n    min: ${p.min.join(',')}\n    max: ${p.max.join(',')}\n    rank: ${m.rank}\n`
      y += `    intervalo-minutos: 15\n    reset-percentual: 80.0\n    spawn: ${p.spawn.map(n => n.toFixed(2)).join(',')}\n    composicao:\n`
      for (const [bloco, peso] of Object.entries(m.blocos)) y += `      ${bloco}: ${peso.toFixed(1)}\n`
    })
    fs.writeFileSync(path.join(P, 'RankupPlugin', 'minas.yml'), y)
  }

  // as barracas não entram aqui: quem cadastra é o próprio spawn, no "construir"
  console.log('== Duelo: a arena de X1 (axepvp) ==')
  y = 'arenas:\n'
  const [a1x, a1z, a1yaw] = AXE.pos1, [a2x, a2z, a2yaw] = AXE.pos2
  y += `  ${AXE.nome}:\n    pos1:\n${local('arenas', [a1x, CHAO + 1, a1z], a1yaw, 0, '      ')}    pos2:\n${local('arenas', [a2x, CHAO + 1, a2z], a2yaw, 0, '      ')}`
  fs.writeFileSync(path.join(P, 'DueloPlugin', 'arenas.yml'), y)

  console.log(`== Gladiador: arena e ${GLAD.pontos.length} pontos de entrada ==`)
  const [gcx, gcz] = GLAD.centro
  y = `pos1:\n${local('arenas', [gcx + GLAD.min[0] + 0.5, CHAO + GLAD.min[1], gcz + GLAD.min[2] + 0.5], 0, 0, '  ')}`
  y += `pos2:\n${local('arenas', [gcx + GLAD.max[0] + 0.5, CHAO + GLAD.max[1], gcz + GLAD.max[2] + 0.5], 0, 0, '  ')}spawns:\n`
  for (const [dx, dz] of GLAD.pontos) {
    const yaw = Math.round(Math.atan2(dx, -dz) * 180 / Math.PI) // olhando pro centro
    y += '- ' + local('arenas', [gcx + dx + 0.5, CHAO + 1, gcz + dz + 0.5], yaw, 0, '  ').slice(2)
  }
  fs.writeFileSync(path.join(P, 'GladiadorPlugin', 'arena.yml'), y)

  console.log('== configs: proteção do spawn, saída do mundo de recursos e warp VIP ==')
  trocarNoYaml(path.join(P, 'RankupPlugin', 'config.yml'), 'protecao', 'mundos', '[spawn]')
  trocarNoYaml(path.join(P, 'RecursosPlugin', 'config.yml'), 'saida', 'mundo', '"spawn"')
  const vip = path.join(P, 'VipPlugin', 'config.yml')
  // a área VIP fica na avenida sul do spawn
  for (const [k, v] of [['mundo', 'spawn'], ['x', SPAWN.x], ['y', CHAO + 1], ['z', SPAWN.z + 20]]) trocarNoYaml(vip, 'warp-vip', k, v)
  console.log(comMinas ? 'Pronto. Ligue o servidor: as minas se enchem sozinhas.' : 'Pronto. As minas ficam pra staff criar (/mina admin).')
}

// ---------------------------------------------------------------------------
const fase = process.argv[2]
if (fase === 'cadastrar') {
  cadastrar(process.argv.includes('--minas'))
} else if (fase === 'construir' || fase === 'marcar') {
  const partes = {
    'limpar-antigo': limparAntigo, minas: construirMinas, spawn: construirSpawn, barracas: construirBarracas,
    boss: construirBoss, oficinas: construirOficinas, loja: construirLoja, banco: construirBanco, mobs: construirMobs, dicas: construirDicas, tutorial: construirTutorial, arenas: construirArenas, marcar: marcarComBot,
  }
  // "marcar" sozinho era o atalho antigo pra só a parte do bot
  const pedidas = fase === 'marcar' ? ['marcar'] : process.argv.slice(3)
  const ordem = pedidas.length ? pedidas : ['minas', 'spawn', 'boss', 'dicas', 'arenas', 'marcar']
  const desconhecidas = ordem.filter(p => !partes[p])
  if (desconhecidas.length) {
    console.log(`Parte desconhecida: ${desconhecidas.join(', ')}. Use: ${Object.keys(partes).join(', ')}`)
    process.exit(1)
  }
  t.rodar(async () => {
    for (const parte of ordem) await partes[parte]()
    await t.terminar()
  })
} else {
  console.log('Uso: node montar-mapa.js construir [minas|oficinas|loja|banco|mobs|spawn|barracas|boss|dicas|tutorial|arenas|marcar|limpar-antigo...]   (servidor ligado)'
    + '\n     node montar-mapa.js cadastrar [--minas]   (servidor desligado)')
}
