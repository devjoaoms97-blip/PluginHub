// Mapa de alturas de uma área (pelo console, SÓ NO SERVIDOR DE TESTE), pra planejar a câmera em trilho
// sem atravessar nada. Imprime uma grade: cada célula é o bloco sólido mais alto (menos 64), "~" é
// água na superfície e "." é chão no nível 64-65.
//   node mapa-altura.js <mundo> <x1> <z1> <x2> <z2> [passo=2] [yMax=100]
//   ex.: node mapa-altura.js minecraft:spawn 520 -40 580 40
const fs = require('fs')
const { Rcon } = require('rcon-client')

const [, , mundo, x1, z1, x2, z2, passoArg, yMaxArg] = process.argv
if (!z2) { console.log('Uso: node mapa-altura.js <mundo> <x1> <z1> <x2> <z2> [passo=2] [yMax=100]'); process.exit(1) }
const passo = Number(passoArg) || 2, yMax = Number(yMaxArg) || 100
const PASTA = process.env.MC_TESTE || 'C:\\mc-teste'
const p = Object.fromEntries(fs.readFileSync(PASTA + '/server.properties', 'utf8').split(/\r?\n/)
  .filter(l => l.includes('=') && !l.startsWith('#')).map(l => [l.slice(0, l.indexOf('=')), l.slice(l.indexOf('=') + 1)]))

;(async () => {
  const r = await Rcon.connect({ host: 'localhost', port: +p['rcon.port'], password: p['rcon.password'] })
  const teste = async (x, y, z, b) => /passed/i.test(await r.send(`execute in ${mundo} if block ${x} ${y} ${z} ${b}`))
  // longe de qualquer jogador os chunks estão descarregados (e o "if block" falha): carrega a área e
  // espera o servidor ler do disco; no fim tira o carregamento forçado
  const area = `${Math.min(+x1, +x2)} ${Math.min(+z1, +z2)} ${Math.max(+x1, +x2)} ${Math.max(+z1, +z2)}`
  await r.send(`execute in ${mundo} run forceload add ${area}`)
  await new Promise(ok => setTimeout(ok, 4000))
  const xs = [], zs = []
  for (let x = Math.min(+x1, +x2); x <= Math.max(+x1, +x2); x += passo) xs.push(x)
  for (let z = Math.min(+z1, +z2); z <= Math.max(+z1, +z2); z += passo) zs.push(z)
  console.log(`${mundo}  x ${xs[0]}..${xs.at(-1)} (colunas)  z ${zs[0]}..${zs.at(-1)} (linhas)  passo ${passo}`)
  console.log('      ' + xs.map(x => String(x).slice(-3).padStart(3)).join(''))
  for (const z of zs) {
    let linha = String(z).padStart(5) + ' '
    for (const x of xs) {
      let y = yMax
      while (y > 40 && await teste(x, y, z, '#minecraft:replaceable')) y--
      const agua = await teste(x, y + 1, z, 'minecraft:water')
      const c = agua ? '~' : (y <= 65 ? '.' : String(Math.min(99, y - 64)))
      linha += c.padStart(3)
    }
    console.log(linha)
  }
  await r.send(`execute in ${mundo} run forceload remove ${area}`)
  r.end()
})()
