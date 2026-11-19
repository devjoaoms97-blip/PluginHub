// Os textos do site que não vêm do servidor: as páginas de cada parte do servidor (os cards da página
// inicial levam pra elas). `ranking` liga a página à aba certa do /rankings.

const RECURSOS = [
  {
    id: 'rankup', tutoriais: ['rankup-e-minas', 'ranks-minas-e-novidades'], icone: '⛏️', titulo: 'Rankup com 12 minas',
    resumo: 'Minere, venda e suba de rank até o prestígio. Cada rank abre uma mina melhor, e a mina PvP rende mais pra quem tem coragem.',
    paragrafos: [
      'Você começa como Camponês e sobe rank a rank até Rei: Fazendeiro, Lenhador, Ferreiro, Mercador, Escudeiro, Clérigo, Cavaleiro, Barão, Conde, Duque e Rei. Cada rank abre uma mina nova na Avenida das Minas, com minérios que valem mais.',
      'O que você minera vende sozinho. Chegou no Rei? O prestígio recomeça a jornada com um ✦ na sua tag e bônus de venda pra sempre.',
      'No fundo da avenida fica a Mina PvP: qualquer rank entra, rende no nível das minas do meio e tem uma hora de venda em dobro todo dia, mas quem morre lá dentro deixa tudo no chão.',
    ],
    lista: ['12 ranks e prestígio sem fim', 'Venda automática do que você minera', 'Fragmentos Arcanos raros que viram encantamentos acima do máximo no Cambista', 'Mina PvP com hora de bônus'],
    comandos: [['/rankup', 'sobe de rank'], ['/ranks', 'mostra todos os ranks e preços'], ['/mina', 'vai pra sua mina'], ['/prestigio', 'recomeça com bônus']],
    ranking: 'rank',
  },
  {
    id: 'bosses', icone: '🐉', titulo: 'Bosses',
    resumo: 'Um boss todo dia às 20h, com modelo próprio e ataques especiais. O ranking de dano paga em coins e em Chaves do Boss.',
    paragrafos: [
      'Na arena enterrada ao sul do spawn aparecem o Titã Sombrio, o Carrasco Ósseo, a Rainha Aracnídea, o Açougueiro Infernal, o Estilhaço Verde, a Feiticeira Maldita e o Flagelo das Trevas, cada um com o próprio modelo em 3D e ataques diferentes.',
      'Todo mundo que bate ganha, mas o ranking de dano decide quem leva mais: os três primeiros recebem Chaves do Boss e o campeão ganha VIP de evento. A arena tem PvP ligado, então cuidado com quem está do lado.',
    ],
    lista: ['Agenda fixa, com aviso no chat e no Discord', 'Versão Elite, mais forte e com loot melhor', 'Pontos pro seu clã'],
    comandos: [['/boss', 'mostra o próximo boss'], ['/warp boss', 'vai pra arena']],
    ranking: 'clas',
  },
  {
    id: 'fazendas', tutoriais: ['fazendas', 'terrenos-maquinas-spawners'], icone: '🌾', titulo: 'Terrenos e fazendas',
    resumo: 'Compre um terreno, construa até 8 fazendas e deixe produzindo. Cada uma tem um achado raro que vale ouro.',
    paragrafos: [
      'Os terrenos são seus: compre com coins, construa o que quiser e chame os amigos. O imposto é semanal, e com o /terreno autopagar ele se paga sozinho.',
      'Dentro do terreno cabem até 8 fazendas, entre 12 tipos: horta, galinheiro, pomar, pocilga, trigal, curral, ovelhas, apiário, abóboras, cogumelos, verrugas e chorus. Elas produzem sozinhas e, de vez em quando, acham um item raro (como o Ovo Dourado) que vende caro no Corretor Rural.',
    ],
    lista: ['12 fazendas, até 8 por terreno', 'Achados raros com textura própria', 'Venda no Corretor Rural com o bônus do rank'],
    comandos: [['/terreno', 'compra e cuida do seu terreno'], ['/fazendas', 'suas fazendas e a produção']],
    ranking: 'dinheiro',
  },
  {
    id: 'clas', tutoriais: ['clas-e-pvp', 'x1-e-duelos'], icone: '🛡️', titulo: 'Clãs',
    resumo: 'Monte seu clã, divida o banco, dispute o ranking de abates e de bosses.',
    paragrafos: [
      'Um clã tem tag no chat e na tab, banco próprio com extrato, cargos (líder, oficial e membro) e chat só de vocês.',
      'Os pontos vêm de abates no PvP e de dano nos bosses. O clã campeão do boss ganha destaque, e o ranking fica aqui no site.',
    ],
    lista: ['Banco do clã com extrato', 'Fogo amigo opcional', 'Ranking por pontos'],
    comandos: [['/cla criar <tag> <nome>', 'cria um clã'], ['/cla convidar <jogador>', 'chama alguém'], ['/cc <mensagem>', 'chat do clã'], ['/cla top', 'ranking no jogo']],
    ranking: 'clas',
  },
  {
    id: 'eventos', tutoriais: ['eventos', 'gladiador', 'x1-e-duelos', 'dragao-do-end'], icone: '⚔️', titulo: 'Eventos e PvP',
    resumo: 'Gladiador, X1 com aposta, Spleef, Vietnã, Corre ou Cai e recompensa por cabeça.',
    paragrafos: [
      'O Gladiador é o PvP em massa: todos contra todos numa arena com arquibancada, e o último vivo leva o prêmio. No X1, você desafia alguém e os dois apostam coins.',
      'Os eventos de minigame (Spleef, Vietnã, Corre ou Cai, Labirinto, Quiz e mais) aparecem com um botão [ENTRAR] no chat. E se alguém te irritar, coloque uma recompensa pela cabeça dele.',
    ],
    lista: ['Gladiador com prêmio em coins', 'X1 com aposta e plateia', 'Minigames com botão no chat', 'Recompensa por cabeça'],
    comandos: [['/gladiador entrar', 'entra no Gladiador'], ['/duelo <jogador> [valor]', 'desafia pro X1'], ['/eventos', 'eventos abertos'], ['/bounty', 'recompensas por cabeça']],
  },
  {
    id: 'skills', tutoriais: ['skills-e-combate', 'skills-guia-completo', 'skills-de-defesa', 'skills-de-profissao', 'skills-de-profissao-2'], icone: '📈', titulo: '21 skills',
    resumo: 'Treze de combate e oito de profissão: Minerador, Pescador, Caçador, Ferreiro... cada uma com bônus de verdade.',
    paragrafos: [
      'As skills de combate sobem com o que você usa: Espadas, Machado, Arqueiro, Tridente, Lanceiro, Marreteiro, Porradeiro, Esquiva, Defesa, Bloqueio, Crítico, Regeneração e Agilidade.',
      'As de profissão sobem trabalhando: Minerador, Agricultor, Pescador, Lenhador, Caçador, Escavador, Alquimista e Ferreiro. No nível 50, cada uma dá um bônus que muda o jogo (venda a mais, drop extra, desconto, relíquias...), e o prestígio soma ainda mais.',
    ],
    lista: ['Nível 1 ao 50 e prestígio', 'Bônus de combate e de profissão', 'Ranking por skill'],
    comandos: [['/skills', 'seu progresso'], ['/skills top', 'ranking no jogo']],
    ranking: 'skills',
  },
  {
    id: 'caixas', tutoriais: ['caixas-e-votos'], icone: '🎁', titulo: 'Caixas e votos',
    resumo: 'Vote todo dia e ganhe chaves. Abra caixas na Praça das Caixas, com spawners, máquinas e itens raros.',
    paragrafos: [
      'Na Praça das Caixas, no spawn, ficam a Caixa de Voto, a da Mina, a do Boss e a do Tesouro. Clique com o botão esquerdo pra ver os prêmios e as chances; com a chave na mão, o direito gira a roleta.',
      'Cada voto dá uma Chave de Voto. Votando dias seguidos, o bônus cresce, e no 7º dia vem uma chave extra. A cada 50 votos do servidor, todo mundo online ganha uma, e os 3 que mais votam no mês levam Chaves do Boss.',
    ],
    lista: ['4 caixas com chances abertas', 'Sequência diária de votos', 'Festa do Voto a cada 50 votos', 'Ranking de votos do mês'],
    comandos: [['/votar', 'links pra votar e sua sequência'], ['/caixas', 'suas chaves']],
    ranking: 'votos',
  },
  {
    id: 'pesca', tutoriais: ['pesca-e-porto', 'tesouros-01-mapas', 'tesouros-02-camara', 'tesouros-03-recompensas', 'mundos-de-recursos'], icone: '🎣', titulo: 'Pesca e tesouros',
    resumo: 'Um porto com barracas de pesca e mapas do tesouro que levam a câmaras com guardiões.',
    paragrafos: [
      'O porto da pesca fica longe de tudo: alugue uma barraca e pesque até parado, com o estoque guardando o que vem. Os peixes raros vendem caro.',
      'Os mapas do tesouro levam a um X no mundo dos tesouros. Cave lá e abra uma câmara com guardiões e um baú, que pode ter até uma Chave do Tesouro.',
    ],
    lista: ['Barracas de pesca com estoque', 'Peixes raros', 'Mapas do tesouro de várias raridades'],
    comandos: [['/warp pesca', 'vai pro porto'], ['/pesca', 'sua barraca'], ['/peixes', 'peixes raros'], ['/warp tesouros', 'mundo dos tesouros']],
  },
];

// As novidades (/novidades), da mais nova pra mais antiga. Pra anunciar uma atualização, é só pôr uma
// entrada no começo. `link` leva pra página da parte do servidor, se tiver.
const NOVIDADES = [
  {
    data: '2026-11-18', titulo: 'Caixas, votos e mais quatro profissões',
    itens: [
      ['🎁', 'Praça das Caixas no spawn: Caixa de Voto, da Mina, do Boss e do Tesouro, com roleta e as chances à mostra.', 'caixas'],
      ['🗳️', 'Votar dá Chave de Voto, sequência diária com bônus, Festa do Voto a cada 50 votos e ranking do mês.', 'caixas'],
      ['📈', 'Skills novas: Caçador (cabeças de mob), Escavador (relíquias), Alquimista (poções mais longas) e Ferreiro (desconto nas oficinas).', 'skills'],
      ['🌾', 'Os 12 achados raros das fazendas ganharam textura própria.', 'fazendas'],
      ['🎣', 'O porto da pesca mudou pra longe do spawn: agora só pela /warp pesca.', 'pesca'],
      ['🌐', 'Saiu o site, com rankings e o perfil de cada jogador.'],
    ],
  },
  {
    data: '2026-11-17', titulo: 'Mina PvP, porto da pesca e bosses em 3D',
    itens: [
      ['⚔️', 'Mina PvP no fundo da Avenida das Minas, com uma hora de venda em dobro todo dia.', 'rankup'],
      ['🎣', 'Porto da pesca com 16 barracas pra alugar, em que a vara puxa sozinha.', 'pesca'],
      ['🐉', 'Os bosses ganharam modelo próprio em 3D, com animação de andar e de ataque.', 'bosses'],
      ['🌾', 'Fazendas nos terrenos: 12 tipos, até 8 por terreno, e um achado raro em cada.', 'fazendas'],
    ],
  },
  {
    data: '2026-11-10', titulo: 'Pesca com loot próprio e spawners nos terrenos',
    itens: [
      ['🐟', 'Peixes raros, o Baú do Mar e a Vara do Pescador.', 'pesca'],
      ['🧱', 'Um spawner de cada tipo por terreno, e as máquinas trabalham pela metade com você offline.'],
    ],
  },
  {
    data: '2026-11-05', titulo: 'Kits do VIP, modpack e Discord oficial',
    itens: [
      ['👑', 'Kits do VIP diário, semanal e mensal, com máquinas e mapas do tesouro.'],
      ['📦', 'Modpack oficial opcional: Sodium, shader e o servidor já na lista.'],
      ['💬', 'O Discord oficial abriu, com vínculo de conta, tickets e status ao vivo.'],
    ],
  },
  {
    data: '2026-11-02', titulo: 'Eventos e as primeiras profissões',
    itens: [
      ['🎉', 'Eventos novos: Vietnã, Spleef, Corre ou Cai, Sobrevivência, Corrida Maluca, Labirinto do Fauno e Quiz.', 'eventos'],
      ['⛏️', 'Skills de profissão: Minerador, Agricultor, Pescador e Lenhador.', 'skills'],
    ],
  },
];

module.exports = { RECURSOS, NOVIDADES };
