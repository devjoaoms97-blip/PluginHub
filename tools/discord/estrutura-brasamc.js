// Como o Discord da BrasaMC deve ficar. O estrutura.js compara isto com o servidor e cria ou
// ajusta o que faltar. Cada item tem uma "chave" fixa: renomear aqui renomeia lá, sem duplicar.
//
// acesso dos canais e categorias:
//   publico  — todos veem e escrevem
//   leitura  — todos veem, só a staff (e o bot) escreve
//   staff    — só a staff vê
//   direcao  — só Diretor e Sub-diretor veem
// "adotar" lista nomes de canais/cargos que já existem e passam a ser este item (mantém o histórico).
// "substituir" lista cargos antigos que são apagados e recriados do zero (some de quem tinha).

const STAFF = ['diretor', 'subdiretor', 'moderador', 'ajudante'];
const DIRECAO = ['diretor', 'subdiretor'];

module.exports = {
  servidor: {
    nome: 'BrasaMC',
    // Só menções notificam (com 80+ membros, "todas as mensagens" vira barulho).
    notificacoesPadrao: 1,
    // Requisitos do modo Comunidade: e-mail verificado e filtro de mídia pra todos.
    verificacao: 1,
    filtroConteudo: 2,
    comunidade: true,
    canalRegras: 'regras',
    canalAvisosDiscord: 'staff', // avisos do próprio Discord pra moderação
    canalSistema: 'geral', // "fulano entrou" do Discord: no #boas-vindas empurraria a mensagem fixa
    canalAfk: 'afk-voz',
  },

  // De cima pra baixo (a ordem vale abaixo do cargo do bot).
  cargos: [
    { chave: 'diretor', nome: '👑 Diretor', adotar: ['👑 Diretor'], manterPermissoes: true },
    { chave: 'subdiretor', nome: '⚜️ Sub-diretor', adotar: ['⚜️ Sub-diretor'], manterPermissoes: true },
    {
      chave: 'moderador', nome: '🛡️ Moderador', cor: '#e67e22', separado: true,
      permissoes: ['KICK_MEMBERS', 'BAN_MEMBERS', 'MANAGE_MESSAGES', 'MODERATE_MEMBERS', 'MANAGE_THREADS',
        'MANAGE_NICKNAMES', 'MOVE_MEMBERS', 'MUTE_MEMBERS', 'DEAFEN_MEMBERS', 'VIEW_AUDIT_LOG'],
    },
    {
      chave: 'ajudante', nome: '🤝 Ajudante', cor: '#2ecc71', separado: true,
      permissoes: ['MODERATE_MEMBERS', 'MANAGE_THREADS', 'MANAGE_MESSAGES'],
    },
    { chave: 'booster', nome: '🌟 Server Booster', adotar: ['🌟 Server Booster'], gerenciado: true },
    { chave: 'vip-diamante', nome: '💎 VIP Diamante', cor: '#3498db', separado: true, substituir: ['💎VIP Diamante'] },
    { chave: 'vip-ouro', nome: '🥇 VIP Ouro', cor: '#ffa028', separado: true, substituir: ['🥇 VIP Ouro'] },
    { chave: 'vip-prata', nome: '🥈 VIP Prata', cor: '#9aa7b0', separado: true, substituir: ['🥈VIP Prata'] },
    { chave: 'vip-bronze', nome: '🥉 VIP Bronze', cor: '#a84300', separado: true, substituir: ['🥉VIP Bronze'] },
    { chave: 'dev', nome: '👾 Dev', adotar: ['👾 Dev'], tirarMencaoGeral: true },
    { chave: 'membro', nome: '🎮 Membro', adotar: ['🎮 Membro'], tirarMencaoGeral: true },
    { chave: 'vinculado', nome: '🔗 Vinculado', cor: '#f39c12' },
    { chave: 'ping-novidades', nome: '🔔 Novidades' },
    { chave: 'ping-boss', nome: '🔔 Boss' },
    { chave: 'ping-gladiador', nome: '🔔 Gladiador' },
    { chave: 'ping-leiloes', nome: '🔔 Leilões' },
  ],

  // @everyone deixa de poder marcar @everyone/@here.
  everyoneSemMencaoGeral: true,

  // Na ordem em que aparecem. Categorias que não estão aqui ficam como estão, depois destas.
  categorias: [
    {
      chave: 'inicio', nome: '📌 INÍCIO', acesso: 'leitura',
      canais: [
        { chave: 'boas-vindas', nome: '👋・boas-vindas', topico: 'Bem-vindo à BrasaMC! Comece por aqui.' },
        { chave: 'regras', nome: '📜・regras', topico: 'Regras do Discord e do servidor.' },
        { chave: 'anuncios', nome: '📢・anuncios', tipo: 'anuncios', topico: 'Novidades e avisos oficiais da BrasaMC.' },
        { chave: 'changelog', nome: '📝・changelog', topico: 'O que mudou no servidor, atualização por atualização.' },
        { chave: 'status', nome: '🟢・status', topico: 'Servidor online, jogadores e próximos eventos (atualizado pelo bot).' },
      ],
    },
    {
      chave: 'conta', nome: '🔗 CONTA E LOJA', acesso: 'leitura',
      canais: [
        { chave: 'vincular-conta', nome: '🔗・vincular-conta', topico: 'Vincule sua conta do Minecraft e ganhe o cargo e os avisos.' },
        { chave: 'loja-vip', nome: '🛒・loja-vip', topico: 'Compre créditos com Pix ou cartão. Entrega automática na conta vinculada.' },
      ],
    },
    {
      chave: 'tutoriais', nome: '📚 TUTORIAIS', acesso: 'leitura',
      canais: [
        { chave: 'tutoriais', nome: '📚・tutoriais', tipo: 'forum', topico: 'Como funciona cada parte do servidor. Um post por assunto.' },
      ],
    },
    {
      chave: 'feed', nome: '🎮 NO SERVIDOR', acesso: 'leitura',
      canais: [
        { chave: 'eventos', nome: '🐉・eventos', topico: 'Boss, Gladiador, XP em dobro e alta no mercado, ao vivo.' },
        { chave: 'mercado', nome: '🏛️・mercado', topico: 'Leilões de terrenos e barracas, aluguéis e vagas.' },
        { chave: 'bounty', nome: '🎯・bounty', topico: 'Recompensas pela cabeça de jogadores e clãs.' },
        { chave: 'conquistas', nome: '🏆・conquistas', topico: 'Novos Reis, prestígios e VIPs.' },
      ],
    },
    {
      chave: 'comunidade', nome: '💬 COMUNIDADE', acesso: 'publico',
      canais: [
        { chave: 'geral', nome: '💬・geral', adotar: ['💬•chat'], topico: 'Papo livre.' },
        { chave: 'prints', nome: '📸・prints', topico: 'Prints e vídeos do servidor.' },
        { chave: 'comercio', nome: '💰・comercio', topico: 'Compra, venda e troca entre jogadores (só coins do jogo).' },
        { chave: 'recrutamento-clas', nome: '🛡️・recrutamento-clas', tipo: 'forum', topico: 'Clãs recrutando e jogadores procurando clã. Um post por clã.' },
        { chave: 'sugestoes', nome: '💡・sugestoes', tipo: 'forum', topico: 'Ideias para o servidor. Um post por sugestão.' },
        { chave: 'musica', nome: '🎶・musica', adotar: ['🎶•musica'] },
      ],
    },
    {
      chave: 'suporte', nome: '🎫 SUPORTE', acesso: 'leitura',
      canais: [
        { chave: 'abrir-ticket', nome: '🎫・abrir-ticket', topico: 'Dúvida, bug, denúncia, pagamento ou apelação: abra um ticket.' },
      ],
    },
    { chave: 'tickets', nome: '📂 TICKETS ABERTOS', acesso: 'staff', canais: [] },
    {
      chave: 'voz', nome: '🔊 VOZ', acesso: 'publico',
      canais: [
        { chave: 'voz-geral', nome: '🔊 Geral', tipo: 'voz' },
        { chave: 'voz-clas-1', nome: '⚔️ Clãs 1', tipo: 'voz' },
        { chave: 'voz-clas-2', nome: '⚔️ Clãs 2', tipo: 'voz' },
        { chave: 'voz-suporte', nome: '🎧 Suporte', tipo: 'voz' },
      ],
    },
    // Os canais de outros jogos continuam todos aqui, só muda o nome da categoria.
    { chave: 'outros-jogos', nome: '🎲 OUTROS JOGOS', adotar: ['▁▂▃▄▅▆▇•jogos•▇▆▅▄▃▂▁'], acesso: 'manter', canais: [] },
    { chave: 'afk', nome: '💤 AFK', adotar: ['▁▂▃▄▅▆▇•afk•▇▆▅▄▃▂▁'], acesso: 'manter', canais: [
      { chave: 'afk-voz', nome: '🛌 • afk', adotar: ['🛌 • afk'], tipo: 'voz', acesso: 'manter' },
    ] },
    {
      chave: 'staff', nome: '🛡️ STAFF', adotar: ['▁▂▃▄▅▆▇ Administração ▇▆▅▄▃▂▁'], acesso: 'staff',
      canais: [
        { chave: 'staff', nome: '🛡️・staff', topico: 'Conversa da equipe.' },
        { chave: 'alertas-pix', nome: '🚨・alertas-pix', acesso: 'direcao', topico: 'Pix alto ou suspeito no servidor.' },
        { chave: 'alertas-economia', nome: '📉・alertas-economia', topico: 'Alertas do painel da economia.' },
        { chave: 'log-vendas', nome: '🧾・log-vendas', acesso: 'direcao', topico: 'Vendas da loja de créditos (Mercado Pago) e entregas com problema.' },
        { chave: 'log-vinculos', nome: '🔗・log-vinculos', topico: 'Contas vinculadas e desvinculadas.' },
        { chave: 'log-tickets', nome: '🎫・log-tickets', topico: 'Transcrição dos tickets fechados.' },
      ],
    },
  ],

  // Canais que existiam e saem (pela chave do estado). O #chat-do-jogo era da ponte de chat, que não vai ter.
  apagarCanais: ['chat-do-jogo'],

  // Categoria do DiscordPlugin → canal. O estrutura.js cria um webhook por categoria e grava as URLs.
  webhooks: {
    boss: 'eventos',
    gladiador: 'eventos',
    minesena: 'eventos',
    skills: 'eventos',
    loja: 'eventos',
    recursos: 'eventos',
    leiloes: 'mercado',
    barracas: 'mercado',
    vip: 'conquistas',
    conquistas: 'conquistas',
    bounty: 'bounty',
    status: 'status',
    pix: 'alertas-pix',
    economia: 'alertas-economia',
  },

  // Categoria do DiscordPlugin → cargo 🔔 que ela marca (quando o texto bate com mencionar-se).
  mencoes: {
    boss: 'ping-boss',
    gladiador: 'ping-gladiador',
    leiloes: 'ping-leiloes',
  },

  STAFF,
  DIRECAO,
};
