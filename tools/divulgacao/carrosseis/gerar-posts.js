// Gera as imagens do Instagram tirando foto de um HTML com o Edge ou o Chrome sem janela:
//   posts/<post>/NN.png        carrosséis do feed (1080x1350), com o IP no rodapé
//   stories/<destaque>/NN.png  os mesmos tutoriais em story (1080x1920), SEM IP nem site,
//                              pra salvar nos destaques (o IP fica só na bio)
//   stories/capas/<nome>.png   as capas redondas dos destaques
// Uso: node gerar-posts.js [filtro]   (sem argumento, gera tudo; o filtro compara o começo do nome)
// Para trocar o IP nos posts do feed, mude IP abaixo e rode de novo.
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

// ---- Agenda dos eventos, lida do config do eventos-plugin ----------------------------------
// Os eventos de arena e o Quiz vêm do config (mudou lá, roda de novo). Boss, dragão e Mine-sena
// são de outros plugins e ficam fixos aqui.
const EVENTOS_INFO = {
  labirinto: ['🌑', 'Labirinto do Fauno'], corrida: ['🎢', 'Corrida Maluca'], vietna: ['🌿', 'Vietnã'],
  correoucai: ['🏃', 'Corre ou Cai'], quiz: ['✏️', 'Quiz'], spleef: ['❄️', 'Spleef'], sobrevivencia: ['⚔️', 'Sobrevivência'],
};
function agendaDoDia() {
  const cfg = fs.readFileSync(path.join(__dirname, '..', '..', '..', 'eventos-plugin', 'src', 'main', 'resources', 'config.yml'), 'utf8');
  const itens = [];
  for (const [id, [e, nome]] of Object.entries(EVENTOS_INFO)) {
    const m = cfg.match(new RegExp(`^${id}:[\\s\\S]*?^  agenda: \\[([^\\]]*)\\]`, 'm'));
    if (!m) throw new Error(`agenda de ${id} não achada no config`);
    for (const h of m[1].match(/\d\d:\d\d/g) || []) itens.push([h, e, nome]);
  }
  itens.push(['18:00', '🎟️', 'Mine-sena'], ['20:00', '🐉', 'Boss'], ['21:00', '🐲', 'Dragão do End']);
  return itens.sort((a, b) => a[0].localeCompare(b[0])).map(([h, e, nome]) => [e, h.replace(':00', 'h'), nome]);
}
const AGENDA = agendaDoDia();
const metade = Math.ceil(AGENDA.length / 2);

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
    { tipo: 'capa', topo: 'Passo a passo', titulo: 'Como entrar<br>no BrasaMC', sub: 'Leva 2 minutos. Original ou pirata.', dica: 'arrasta pro lado →' },
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
    { tipo: 'capa', topo: 'Android', titulo: 'Jogue o BrasaMC<br>no celular', sub: 'Minecraft Java no Android, de graça.<br>Sem pagar o Bedrock.', dica: 'arrasta pro lado →' },
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

  // Sem IP (pré-lançamento) e sem número de eventos (a lista vai crescer).
  '04-agenda-eventos': [
    { tipo: 'capa', topo: 'Todo dia no BrasaMC', titulo: 'Agenda<br>dos eventos', sub: 'Do café da manhã até de madrugada,<br>sempre tem alguma coisa valendo prêmio.', dica: 'arrasta pro lado →' },
    { tipo: 'grade', titulo: 'Manhã e tarde', itens: AGENDA.slice(0, metade) },
    { tipo: 'grade', titulo: 'Tarde e noite', itens: AGENDA.slice(metade) },
    { tipo: 'texto', icone: '📅', titulo: 'E no fim de semana', linhas: [
      '<b>Gladiador</b> sábado às 19h, por clã.',
      '<b>Gladiador individual</b> domingo às 19h.',
      'Domingo o boss das 20h é o <b>Wither</b>: tudo em dobro.',
      '<b>XP de skill em dobro</b> sábado e domingo.',
    ] },
    { tipo: 'texto', icone: '💰', titulo: 'Vale a pena entrar', linhas: [
      'Eventos de arena: <b>cheque de R$ 10.000</b> no jogo.',
      'Entrada <b>de graça</b>, e ninguém perde itens.',
      'Dentro do jogo, <span class="cmd">/eventos</span> mostra a agenda e o próximo.',
      'E vem mais evento por aí.',
    ] },
    { tipo: 'fim', titulo: 'Salva pra não perder 🔥', linhas: ['O servidor abre em breve', 'Link do Discord na bio'] },
  ],

  // A mesma agenda numa imagem só, pro feed.
  '05-agenda-imagem': [
    { tipo: 'agenda', titulo: 'Agenda dos eventos', sub: 'Todo dia no BrasaMC, valendo prêmio no jogo', itens: AGENDA,
      fimDeSemana: '<b>Fim de semana:</b> Gladiador por clã no sábado e individual no domingo, às 19h · Wither no domingo às 20h · XP de skill em dobro',
      nota: 'Entrada de graça · <span class="cmd">/eventos</span> no jogo · e vem mais evento por aí' },
  ],

  // Curiosidades: pra alcançar quem ainda não conhece o servidor (fecha com o gancho do BrasaMC).
  '06-curiosidades-01': [
    { tipo: 'capa', topo: 'Curiosidades', titulo: '7 coisas que você<br>não sabia sobre<br>o Minecraft', sub: 'A número 2 parece mentira,<br>mas é real.', dica: 'arrasta pro lado →' },
    { tipo: 'texto', icone: '🐷', titulo: '1. O Creeper era<br>pra ser um porco', linhas: [
      'O Notch trocou a <b>altura</b> pelo <b>comprimento</b> sem querer ao modelar um porco.',
      'Saiu aquele bicho comprido e esquisito. Ele gostou e deixou: virou o <b>Creeper</b>.',
    ] },
    { tipo: 'texto', icone: '🐱', titulo: '2. O Ghast tem<br>voz de gato', linhas: [
      'Os sons do Ghast vieram do <b>gato do C418</b>, o compositor das músicas do jogo.',
      'O gato fez um barulho estranho enquanto dormia, e ele gravou.',
    ] },
    { tipo: 'texto', icone: '⏱️', titulo: '3. Feito em<br>uma semana', linhas: [
      'A primeira versão, a <b>"Cave Game"</b>, foi feita pelo Notch em cerca de <b>uma semana</b>, em 2009.',
      'Hoje o Minecraft é o <b>jogo mais vendido da história</b>, com mais de 300 milhões de cópias.',
    ] },
    { tipo: 'texto', icone: '👻', titulo: '4. O Herobrine<br>nunca existiu', linhas: [
      'Ele nasceu de um vídeo e de uma história inventada na internet.',
      'Mesmo assim, por anos, várias atualizações traziam a piada nas notas: <b>"Removido o Herobrine"</b>.',
    ] },
    { tipo: 'texto', icone: '🌍', titulo: '5. O mundo<br>tinha um fim', linhas: [
      'Nas versões antigas, a uns <b>12,5 milhões de blocos</b> do centro, o terreno enlouquecia: eram as <b>Far Lands</b>.',
      'Paredes gigantes e buracos sem sentido, por causa de um limite de cálculo do jogo.',
    ] },
    { tipo: 'texto', icone: '🌙', titulo: '6. A lua tem<br>8 fases', linhas: [
      'Um dia no Minecraft dura <b>20 minutos</b>, e a lua muda de fase toda noite.',
      'E isso muda o jogo: na <b>lua cheia</b>, nascem mais slimes nos pântanos.',
    ] },
    { tipo: 'texto', icone: '🐉', titulo: '7. O dragão<br>quase não existiu', linhas: [
      'O <b>End</b> e o <b>Ender Dragon</b> entraram só perto do lançamento da versão 1.0, em 2011.',
      'Antes disso, o Minecraft não tinha um "chefão final".',
    ] },
    { tipo: 'texto', icone: '🔥', titulo: 'E no BrasaMC...', linhas: [
      'O <b>dragão do End renasce todo dia</b> às 21h.',
      '<b>Bosses em 3D</b> todo dia às 20h, e eventos valendo prêmio o dia inteiro.',
      'O servidor abre <b>em breve</b>.',
    ] },
    { tipo: 'fim', titulo: 'Qual você não sabia? 👇', linhas: ['Comenta o número', 'Manda pro amigo que se acha expert', 'Discord na bio'] },
  ],
};

const SEM_IP = new Set(['04-agenda-eventos', '05-agenda-imagem', '06-curiosidades-01']);

// Os destaques reaproveitam os posts, trocando o IP por "na bio do perfil" e o texto de feed pelo de story.
function paraStory(slides) {
  const trocas = [
    [`<span class="ip">${IP}</span>`, '<b>na bio do perfil</b>'],
    ['arrasta pro lado →', 'toca pra passar →'],
    ['Salva esse post e chama os amigos', 'Chama os amigos pra jogar junto'],
  ];
  const trocar = (v) => typeof v === 'string' ? trocas.reduce((t, [a, b]) => t.split(a).join(b), v)
    : Array.isArray(v) ? v.map(trocar)
    : v && typeof v === 'object' ? Object.fromEntries(Object.entries(v).map(([k, x]) => [k, trocar(x)]))
    : v;
  return slides.map(trocar);
}
const DESTAQUES = {
  'como-entrar': paraStory(POSTS['02-como-entrar']),
  'celular': paraStory(POSTS['03-celular']),
  'o-servidor': paraStory(POSTS['01-bem-vindo']),

  // Só destaque (nada de IP nem site). Dados dos tutoriais 07, 08, 19 e do regras.md.
  'eventos': [
    { tipo: 'capa', topo: 'Agenda', titulo: 'Eventos<br>todo dia', sub: 'Boss, arenas, quiz e Gladiador.<br>Prêmio em dinheiro de verdade do jogo.', dica: 'toca pra passar →' },
    { tipo: 'texto', icone: '🐉', titulo: 'Boss às 20h', linhas: [
      'De segunda a sábado, um boss sorteado.',
      'Domingo é o <b>Wither</b>: tudo em dobro.',
      'Top 3 de dano: <b>R$ 15 mil, 10 mil e 5 mil</b> + Chave do Boss.',
      'Às vezes ele vem <b>✦ ELITE ✦</b>.',
    ] },
    { tipo: 'grade', titulo: 'Arenas todo dia', itens: [
      ['☘️', 'Vietnã', '10h e 16h'],
      ['❄️', 'Spleef', '13h e 18h'],
      ['🏃', 'Corre ou Cai', '11h e 17h'],
      ['🏁', 'Corrida Maluca', '9h e 14h'],
      ['🌙', 'Labirinto', '8h e 23h'],
      ['🛡️', 'Sobrevivência', '22h'],
    ] },
    { tipo: 'texto', icone: '🎒', titulo: 'Ninguém perde nada', linhas: [
      'Nas arenas todo mundo recebe o <b>mesmo kit</b>.',
      'Entra de inventário vazio e sai com tudo.',
      'O vencedor leva um <b>cheque de R$ 10 mil</b>.',
      'Tudo em <span class="cmd">/warp eventos</span>',
    ] },
    { tipo: 'texto', icone: '⚔️', titulo: 'E ainda', linhas: [
      '<b>Gladiador</b>: por clã sábado e individual domingo, às 19h.',
      '<b>Quiz</b> no chat às 12h e 15h: R$ 5 mil.',
      '<b>Mine-sena</b>: sorteio todo dia às 18h.',
      '<b>Dragão do End</b> às 21h.',
    ] },
    { tipo: 'fim', titulo: 'Bora pro evento 🔥', linhas: ['<span class="cmd">/eventos</span> mostra a agenda', 'Avisos no Discord (link na bio)', 'XP de skill em dobro no fim de semana'] },
  ],

  'vip': [
    { tipo: 'capa', topo: 'VIP', titulo: 'Seja VIP<br>no BrasaMC', sub: '4 tiers, 30 dias cada.<br>Kit todo dia, montaria e bônus em tudo.', dica: 'toca pra passar →' },
    { tipo: 'grade', titulo: 'Os 4 tiers', itens: [
      ['🥉', 'Bronze', '100 créditos · R$ 10'],
      ['🥈', 'Prata', '200 créditos · R$ 20'],
      ['🥇', 'Ouro', '350 créditos · R$ 35'],
      ['💎', 'Diamante', '500 créditos · R$ 50'],
    ] },
    { tipo: 'texto', icone: '🎁', titulo: 'Três kits', linhas: [
      '<b>Diário</b>: comida, maçãs douradas e XP.',
      '<b>Semanal</b>: diamantes, totem e maçãs.',
      '<b>Mensal</b>: equipamento encantado, <b>máquina</b> e <b>mapas do tesouro</b>.',
      'No Diamante vem até élitro e tridente.',
    ] },
    { tipo: 'texto', icone: '📈', titulo: 'Bônus em tudo', linhas: [
      'XP nas skills: <b>+10% a +50%</b>.',
      'Vende mais e paga menos: <b>3% a 12%</b>.',
      '<span class="cmd">/diaria</span> rende até <b>+100%</b>.',
      'Baú pessoal de até <b>54</b> espaços e até <b>3 terrenos</b>.',
    ] },
    { tipo: 'texto', icone: '🐎', titulo: 'E mais', linhas: [
      '<span class="cmd">/montaria</span>: um cavalo rápido na hora.',
      'Voo, partículas e warp VIP, conforme o tier.',
      'Dá pra gerar um <b>código de presente</b> e dar pra um amigo.',
      'Cosméticos: rastros, cor do nome e efeito de abate.',
    ] },
    { tipo: 'fim', titulo: 'Como pegar o seu', linhas: ['Créditos no nosso Discord (link na bio)', 'No jogo: <span class="cmd">/creditos</span>', 'Compare com <span class="cmd">/vip tiers</span>'] },
  ],

  'regras': [
    { tipo: 'capa', topo: 'Leia antes de jogar', titulo: 'Regras', sub: 'Valem no servidor e no Discord.<br>Não conhecer não livra da punição.', dica: 'toca pra passar →' },
    { tipo: 'texto', icone: '🤝', titulo: 'Respeito', linhas: [
      'Sem ofensa, preconceito, assédio ou ameaça.',
      'Sem conteúdo adulto ou chocante, nem em skin, placa ou construção.',
      'Sem divulgar outros servidores ou dados de ninguém.',
    ] },
    { tipo: 'texto', icone: '🎮', titulo: 'Jogo limpo', linhas: [
      'Proibido hack, cliente com vantagem e macro.',
      'Achou bug ou dupe? <b>Reporte</b>. Usar dá ban.',
      'Nada de farmar com amigo ou conta fake.',
      'Uma conta por pessoa nos eventos com prêmio.',
    ] },
    { tipo: 'texto', icone: '💰', titulo: 'Comércio', linhas: [
      'Golpe é proibido: combinou, cumpriu.',
      'Use o <b>Pix do jogo</b> e as <b>barracas</b>, que deixam registro.',
      'Nada de vender coins, itens ou contas por dinheiro de verdade.',
    ] },
    { tipo: 'texto', icone: '🏗️', titulo: 'Construção e chat', linhas: [
      'Respeite o terreno dos outros.',
      'Construção ofensiva ou que dá lag é removida.',
      'Sem spam, flood ou caps lock.',
      'Suporte é por ticket no Discord, não por DM.',
    ] },
    { tipo: 'texto', icone: '⚖️', titulo: 'Punições', linhas: [
      'Advertência, mute ou banimento, conforme a gravidade.',
      'Achou injusto? Abra um ticket de <b>Apelação</b>.',
      'Regras completas no Discord (link na bio).',
    ] },
  ],
};
// Capas: o Instagram recorta um círculo no meio da imagem.
const CAPAS = { 'como-entrar': '🎮', 'celular': '📱', 'o-servidor': 'chama', 'eventos': '⚔️', 'vip': '💎', 'regras': '📜' };

const FORMATOS = {
  post: { altura: 1350, padding: '90px 90px 70px', ip: true },
  story: { altura: 1920, padding: '250px 90px 260px', ip: false },
};

// ---- Visual ---------------------------------------------------------------------------------
const CSS = `
*{box-sizing:border-box;margin:0;padding:0}
html,body{width:1080px;overflow:hidden}
body{font-family:Inter,'Segoe UI',sans-serif;color:#f5ece6;
  background:radial-gradient(ellipse 120% 70% at 50% 108%,#6b2409 0%,#2a0f06 45%,#120b08 78%);
  display:flex;flex-direction:column;position:relative}
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
.ag{display:grid;grid-template-columns:1fr 1fr;gap:18px}
.ag-col{background:rgba(44,34,29,.85);border:2px solid #3a2c25;border-radius:22px;padding:10px 18px}
.ag-lin{display:flex;align-items:center;gap:12px;padding:13px 0;border-bottom:2px solid rgba(255,255,255,.07)}
.ag-lin:last-child{border-bottom:0}
.ag-h{font-weight:900;font-size:32px;color:#ffb347;width:68px}
.ag-e{font-size:32px;width:42px;text-align:center}
.ag-n{font-size:28px;font-weight:700;color:#fff;white-space:nowrap}
.ag-fds{margin-top:26px;background:rgba(242,107,29,.12);border:2px solid #f26b1d;border-radius:22px;padding:20px 26px;font-size:30px;line-height:1.4}
.ag-nota{margin-top:22px;font-size:28px;color:#b8a69b;text-align:center}
.ag-nota .cmd{font-size:26px}
.capa-destaque{align-items:center;justify-content:center;padding:0!important}
.circulo{width:760px;height:760px;border-radius:50%;display:flex;align-items:center;justify-content:center;
  background:radial-gradient(circle at 50% 55%,#5a1d08 0%,#2a0f06 60%,#120b08 100%);
  box-shadow:0 0 120px rgba(242,107,29,.35)}
.circulo .e{font-size:330px;line-height:1}
.circulo svg{width:400px;height:400px;filter:drop-shadow(0 0 50px rgba(242,107,29,.6))}
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
    case 'agenda': {
      const meio = Math.ceil(s.itens.length / 2);
      const coluna = (lista) => `<div class="ag-col">${lista.map(([e, h, n]) =>
        `<div class="ag-lin"><span class="ag-h">${h}</span><span class="ag-e">${e}</span><span class="ag-n">${n}</span></div>`).join('')}</div>`;
      return `<h1 class="pixel" style="font-size:80px;margin-bottom:10px">${s.titulo}</h1>
        <p class="sub" style="font-size:36px;color:#ffd166;margin-bottom:34px">${s.sub}</p>
        <div class="ag">${coluna(s.itens.slice(0, meio))}${coluna(s.itens.slice(meio))}</div>
        <div class="ag-fds">📅 ${s.fimDeSemana}</div>
        <p class="ag-nota">${s.nota}</p>`;
    }
    case 'fim':
      return `<div class="capa-chama">${chama}</div><h1 class="pixel">${s.titulo}</h1>
        <ul class="fim">${s.linhas.map((l) => `<li>${l}</li>`).join('')}</ul>`;
    default:
      throw new Error('tipo de slide desconhecido: ' + s.tipo);
  }
}

function documento(f, corpoHtml, classe = '') {
  return `<!doctype html><html><head><meta charset="utf-8">
<link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;600;700;800;900&display=swap" rel="stylesheet">
<style>${CSS}html,body{height:${f.altura}px}body{padding:${f.padding}}</style></head>
<body class="${classe}">${corpoHtml}</body></html>`;
}

let postAtual = '';
function pagina(s, i, total, f) {
  const capa = s.tipo === 'capa';
  return documento(f, `${capa ? '' : cabecalho()}
<main>${corpo(s)}</main>
<div class="rodape"><span>${INSTA}</span>${f.ip && !SEM_IP.has(postAtual) ? `<span class="ip">${IP}</span>` : ''}${total > 1 ? `<span class="pag">${i + 1}/${total}</span>` : '<span></span>'}</div>`);
}

const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'brasa-posts-'));
const escolhido = process.argv[2];
let n = 0;
function foto(html, png, altura) {
  const arquivo = path.join(tmp, `${n++}.html`);
  fs.writeFileSync(arquivo, html);
  fs.mkdirSync(path.dirname(png), { recursive: true });
  execFileSync(navegador, [
    '--headless=new', '--disable-gpu', '--hide-scrollbars', '--force-device-scale-factor=1',
    '--virtual-time-budget=6000', `--window-size=1080,${altura}`, `--screenshot=${png}`,
    'file:///' + arquivo.split(path.sep).join('/'),
  ], { stdio: 'ignore' });
}
function gerar(conjunto, pasta, f) {
  for (const [nome, slides] of Object.entries(conjunto)) {
    if (escolhido && !nome.startsWith(escolhido) && !pasta.startsWith(escolhido)) continue;
    postAtual = nome;
    slides.forEach((s, i) => foto(pagina(s, i, slides.length, f),
      path.join(__dirname, pasta, nome, `${String(i + 1).padStart(2, '0')}.png`), f.altura));
    console.log(`${pasta}/${nome}: ${slides.length} slides`);
  }
}
gerar(POSTS, 'posts', FORMATOS.post);
gerar(DESTAQUES, 'stories', FORMATOS.story);
if (!escolhido || 'stories'.startsWith(escolhido) || escolhido === 'capas') {
  for (const [nome, icone] of Object.entries(CAPAS)) {
    const dentro = icone === 'chama' ? chama : `<span class="e">${icone}</span>`;
    foto(documento(FORMATOS.story, `<div class="circulo">${dentro}</div>`, 'capa-destaque'),
      path.join(__dirname, 'stories', 'capas', `${nome}.png`), FORMATOS.story.altura);
  }
  console.log(`stories/capas: ${Object.keys(CAPAS).length} capas`);
}
fs.rmSync(tmp, { recursive: true, force: true });
