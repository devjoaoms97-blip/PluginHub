// Gera os carrosséis do Instagram (1080x1350) em divulgacao/posts/<post>/NN.png,
// tirando foto de um HTML com o Edge ou o Chrome sem janela.
// Uso: node gerar-posts.js [post]   (sem argumento, gera todos)
// Para trocar o IP em todos os posts, mude IP abaixo e rode de novo.
const { execFileSync } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');

const IP = 'stammer-fix.tun.ply.gg';
const VERSAO = '26.2';
const INSTA = '@brasa_mc';

const navegador = [
  process.env.NAVEGADOR,
  'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
  'C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe',
  'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
].filter(Boolean).find((c) => fs.existsSync(c));
if (!navegador) throw new Error('Não achei o Edge nem o Chrome. Passe o caminho em NAVEGADOR.');

const chama = fs.readFileSync(path.join(__dirname, '..', '..', '..', 'site', 'publico', 'brasa.svg'), 'utf8')
  .replace(/<!--.*?-->/gs, '');

// ---- Conteúdo dos posts -------------------------------------------------------------------
// Cada slide: { tipo: 'capa' | 'texto' | 'grade' | 'passos' | 'fim', ... }
const POSTS = {
  '01-bem-vindo': [
    { tipo: 'capa', topo: 'Servidor de Minecraft BR', titulo: 'BrasaMC', sub: 'Rankup, skills, clãs e boss todo dia.<br>No PC e no celular, de graça.', dica: 'arrasta pro lado →' },
    { tipo: 'grade', titulo: 'O que tem lá dentro', itens: [
      ['⛏️', 'Rankup', '12 minas + a Mina PvP'],
      ['📈', '21 skills', 'combate e profissão'],
      ['🐉', 'Boss diário', 'todo dia às 20h'],
      ['🛡️', 'Clãs', 'banco e ranking'],
      ['🌾', 'Fazendas', 'dinheiro parado'],
      ['🎣', 'Pesca', 'peixes raros e porto'],
      ['🗺️', 'Tesouros', 'mapas e câmaras'],
      ['🎁', 'Caixas', 'chaves por voto'],
    ] },
    { tipo: 'texto', icone: '🐉', titulo: 'Um boss todo dia às 20h', linhas: [
      'Modelo próprio em 3D e ataques especiais.',
      'Os 3 que mais batem levam <b>R$ 15 mil, 10 mil e 5 mil</b> + Chave do Boss.',
      'Domingo é dia de <b>Wither</b>: tudo em dobro.',
      'E às vezes ele vem <b>✦ ELITE ✦</b>.',
    ] },
    { tipo: 'texto', icone: '⚔️', titulo: 'PvP pra quem quer', linhas: [
      '<b>Mina PvP</b>: rende mais, mas quem morre deixa tudo no chão.',
      '<b>X1</b> com aposta e <b>Gladiador</b> com plateia.',
      'Spleef, Vietnã, Corre ou Cai e recompensa por cabeça.',
      'Spawn, lojas e terrenos são <b>seguros</b>.',
    ] },
    { tipo: 'passos', titulo: 'Como entrar', passos: [
      `Minecraft Java <b>${VERSAO}</b>`,
      'Multijogador → <b>Adicionar servidor</b>',
      `IP: <span class="ip">${IP}</span>`,
      'Aceite o pacote de texturas',
    ], nota: 'Original ou pirata: os dois entram.' },
    { tipo: 'fim', titulo: 'Te vejo lá dentro 🔥', linhas: ['Link do Discord na bio', 'Salva esse post e chama os amigos'] },
  ],

  '02-como-entrar': [
    { tipo: 'capa', topo: 'Passo a passo', titulo: 'Como entrar<br>na BrasaMC', sub: 'Leva 2 minutos. Original ou pirata.', dica: 'arrasta pro lado →' },
    { tipo: 'passos', titulo: '1. Abra o Minecraft', passos: [
      `Minecraft <b>Java</b>, versão <b>${VERSAO}</b>`,
      'Serve o launcher oficial, o TLauncher ou outro',
      'O Bedrock (da Play Store) não entra',
    ] },
    { tipo: 'passos', titulo: '2. Adicione o servidor', passos: [
      'Multijogador → <b>Adicionar servidor</b>',
      `Endereço: <span class="ip">${IP}</span>`,
      'Entre e aceite o <b>pacote de texturas</b>',
    ], nota: 'O pacote mostra as máquinas, os bosses e os itens novos.' },
    { tipo: 'passos', titulo: '3. Crie sua senha', passos: [
      'Na primeira vez, digite:',
      '<span class="cmd">/register senha senha</span>',
      'Das próximas: <span class="cmd">/login senha</span>',
    ], nota: 'Use uma senha só sua, não a do e-mail.' },
    { tipo: 'passos', titulo: '4. Primeiros passos', passos: [
      '<span class="cmd">/tutorial</span> te guia e dá um Mapa do Tesouro',
      '<span class="cmd">/kit iniciante</span> dá ferramentas e comida',
      '<span class="cmd">/mina</span> e comece a minerar',
    ] },
    { tipo: 'fim', titulo: 'Travou em algum passo?', linhas: ['Chama no Discord (link na bio)', 'Tem tutorial de tudo lá'] },
  ],

  '03-celular': [
    { tipo: 'capa', topo: 'Android', titulo: 'Jogue a BrasaMC<br>no celular', sub: 'Minecraft Java no Android, de graça.<br>Sem pagar o Bedrock.', dica: 'arrasta pro lado →' },
    { tipo: 'texto', icone: '📱', titulo: 'Antes de começar', linhas: [
      'Só <b>Android</b> (no iPhone não dá).',
      '<b>6 GB de RAM</b> ou mais é o ideal.',
      'Uns <b>2 GB livres</b> no celular.',
      '<b>Wi-Fi</b> na primeira vez: ele baixa o jogo inteiro.',
    ] },
    { tipo: 'passos', titulo: '1. Baixe o launcher', passos: [
      '<b>Fold Craft Launcher (FCL)</b>',
      'Só pelo GitHub oficial do FCL',
      'Pegue o APK <b>arm64-v8a</b>',
    ], nota: 'Site de APK por aí tem cópia com vírus. Link certo no nosso Discord.' },
    { tipo: 'passos', titulo: '2. Instale o modpack', passos: [
      'Contas → conta Microsoft ou <b>offline</b> com seu nick',
      'Download → Modpack → <b>Baixar da internet</b>',
      'Cole o link do modpack (está no Discord)',
    ], nota: 'Ele já instala a versão certa e põe o servidor na lista.' },
    { tipo: 'passos', titulo: '3. Entre e jogue', passos: [
      'Opções → Vídeo → <b>shaders desligados</b>',
      'Multijogador → <b>BrasaMC</b>',
      '<span class="cmd">/register senha senha</span>',
    ], nota: 'Lento? Distância de renderização em 6 ou 8.' },
    { tipo: 'fim', titulo: 'Bora jogar junto 🔥', linhas: ['Passo a passo completo no Discord', 'Manda pro amigo que só tem celular'] },
  ],
};

// ---- Visual ---------------------------------------------------------------------------------
const CSS = `
*{box-sizing:border-box;margin:0;padding:0}
html,body{width:1080px;height:1350px;overflow:hidden}
body{font-family:Inter,'Segoe UI',sans-serif;color:#f5ece6;
  background:radial-gradient(ellipse 120% 70% at 50% 108%,#6b2409 0%,#2a0f06 45%,#120b08 78%);
  display:flex;flex-direction:column;padding:90px 90px 70px;position:relative}
body::before{content:'';position:absolute;inset:0;opacity:.07;
  background-image:linear-gradient(#fff 1px,transparent 1px),linear-gradient(90deg,#fff 1px,transparent 1px);
  background-size:54px 54px;pointer-events:none}
.pixel{font-family:Inter,'Segoe UI',sans-serif;font-weight:900;letter-spacing:-1.5px}
.topo{display:flex;align-items:center;gap:18px;font-size:30px;color:#ffb347;font-weight:700;letter-spacing:.5px}
.topo svg{width:54px;height:54px}
main{flex:1;display:flex;flex-direction:column;justify-content:center;position:relative}
h1{font-size:84px;line-height:1.05;margin-bottom:46px;color:#fff;text-shadow:0 6px 0 #7c2d12}
h1.grande{font-size:150px}
.sub{font-size:44px;line-height:1.35;color:#f5ece6}
.capa-chama{width:260px;height:260px;margin-bottom:40px;filter:drop-shadow(0 0 50px rgba(242,107,29,.6))}
.capa-topo{font-size:38px;color:#ffd166;font-weight:700;text-transform:uppercase;letter-spacing:3px;margin-bottom:18px}
.icone{font-size:120px;margin-bottom:26px}
ul{list-style:none}
li{font-size:44px;line-height:1.35;padding:22px 0 22px 50px;position:relative;border-bottom:2px solid rgba(255,255,255,.08)}
li:last-child{border-bottom:0}
li::before{content:'';position:absolute;left:0;top:41px;width:22px;height:22px;background:#f26b1d;box-shadow:0 0 18px #f26b1d}
ol{list-style:none;counter-reset:p}
ol li{padding-left:96px;counter-increment:p}
ol li::before{content:counter(p);width:64px;height:64px;top:22px;background:#f26b1d;color:#120b08;font:900 40px/64px Inter,sans-serif;text-align:center;box-shadow:0 6px 0 #7c2d12}
b{color:#ffd166}
.ip,.cmd{font-family:Consolas,monospace;background:#2c221d;border:2px solid #f26b1d;padding:2px 14px;border-radius:10px;color:#fff;white-space:nowrap}
.ip{font-size:40px}
.nota{margin-top:40px;font-size:36px;color:#b8a69b;border-left:8px solid #ffb347;padding-left:26px;line-height:1.35}
.grade{display:grid;grid-template-columns:1fr 1fr;gap:20px}
.cel{background:rgba(44,34,29,.85);border:2px solid #3a2c25;border-radius:22px;padding:20px 28px}
.cel .e{font-size:48px}
.cel .t{font-size:40px;font-weight:800;margin:8px 0 4px;color:#fff}
.cel .d{font-size:30px;color:#b8a69b}
.dica{margin-top:70px;font-size:34px;color:#ffb347}
.fim li{font-size:46px}
.rodape{display:flex;justify-content:space-between;align-items:center;font-size:30px;color:#b8a69b;position:relative}
.rodape .ip{font-size:28px}
.pag{font-weight:700;color:#ffb347}
`;

function cabecalho() {
  return `<div class="topo">${chama}<span class="pixel">BrasaMC</span></div>`;
}

function corpo(s) {
  switch (s.tipo) {
    case 'capa':
      return `<div class="capa-chama">${chama}</div>
        <div class="capa-topo">${s.topo}</div>
        <h1 class="pixel ${s.titulo === 'BrasaMC' ? 'grande' : ''}">${s.titulo}</h1>
        <p class="sub">${s.sub}</p><p class="dica">${s.dica}</p>`;
    case 'texto':
      return `<div class="icone">${s.icone}</div><h1 class="pixel">${s.titulo}</h1>
        <ul>${s.linhas.map((l) => `<li>${l}</li>`).join('')}</ul>`;
    case 'grade':
      return `<h1 class="pixel" style="margin-bottom:30px">${s.titulo}</h1><div class="grade">${s.itens.map(([e, t, d]) =>
        `<div class="cel"><div class="e">${e}</div><div class="t">${t}</div><div class="d">${d}</div></div>`).join('')}</div>`;
    case 'passos':
      return `<h1 class="pixel">${s.titulo}</h1><ol>${s.passos.map((p) => `<li>${p}</li>`).join('')}</ol>
        ${s.nota ? `<p class="nota">${s.nota}</p>` : ''}`;
    case 'fim':
      return `<div class="capa-chama">${chama}</div><h1 class="pixel">${s.titulo}</h1>
        <ul class="fim">${s.linhas.map((l) => `<li>${l}</li>`).join('')}</ul>`;
    default:
      throw new Error('tipo de slide desconhecido: ' + s.tipo);
  }
}

function pagina(s, i, total) {
  const capa = s.tipo === 'capa';
  return `<!doctype html><html><head><meta charset="utf-8">
<link href="https://fonts.googleapis.com/css2?family=Pixelify+Sans:wght@500;700&family=Inter:wght@400;600;700;800;900&display=swap" rel="stylesheet">
<style>${CSS}</style></head><body>
${capa ? '' : cabecalho()}
<main>${corpo(s)}</main>
<div class="rodape"><span>${INSTA}</span><span class="ip">${IP}</span><span class="pag">${i + 1}/${total}</span></div>
</body></html>`;
}

const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'brasa-posts-'));
const escolhido = process.argv[2];
for (const [nome, slides] of Object.entries(POSTS)) {
  if (escolhido && !nome.startsWith(escolhido)) continue;
  const pasta = path.join(__dirname, 'posts', nome);
  fs.mkdirSync(pasta, { recursive: true });
  slides.forEach((s, i) => {
    const html = path.join(tmp, `${nome}-${i}.html`);
    fs.writeFileSync(html, pagina(s, i, slides.length));
    const png = path.join(pasta, `${String(i + 1).padStart(2, '0')}.png`);
    execFileSync(navegador, [
      '--headless=new', '--disable-gpu', '--hide-scrollbars', '--force-device-scale-factor=1',
      '--virtual-time-budget=6000', '--window-size=1080,1350', `--screenshot=${png}`,
      'file:///' + html.replace(/\\/g, '/'),
    ], { stdio: 'ignore' });
  });
  console.log(`${nome}: ${slides.length} slides`);
}
fs.rmSync(tmp, { recursive: true, force: true });
