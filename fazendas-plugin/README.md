# FazendasPlugin

Fazendas nos terrenos que rendem **dinheiro** (não dão itens). Uma **Escritura** vira uma construção 7x7 com um fazendeiro parado na abertura da cerca. Cada safra põe dinheiro no cofre, gastando **Ração** (fazendas de animais) ou **Adubo** (plantações). O dono passa no fazendeiro e coleta.

Estado: **fase 4** (o funcionamento, o visual de cada fazenda, o balcão na Loja Oficial e o achado raro). Veja o `ROADMAP.md`.

## Como funciona

- **Compra:** no balcão do **Corretor Rural**, na Loja Oficial (`/warp loja`, o último do lado norte), que abre o `/fazendas loja`. Cada fazenda libera num rank. Lá também se compram a Ração e o Adubo (R$ 25 cada).
- **Escritura:** é um item comum, que dá para guardar, vender na barraca ou trocar. Ela guarda o tipo e os níveis das melhorias.
- **Colocar:** com a Escritura na mão, clique direito no chão do terreno. O bloco clicado é o centro.
  - O 1º clique mostra o contorno da área 7x7: **verde** se pode construir; **vermelho** com o motivo se não pode.
  - O 2º clique no mesmo bloco, em até 30 s, constrói.
  - **Condições da área:** ficar dentro de um terreno onde o jogador constrói (dono ou amigo com "construir"), no mundo dos terrenos (`mundos`); chão plano e firme (sem buraco, baú ou bloco especial); 5 blocos livres por cima (ar, grama, flor; `altura-livre`); não encostar em outra fazenda.
  - **Limite:** até 4 fazendas por terreno (`limite-por-terreno`), somando todos os tipos e donos, separado do limite das máquinas.
  - Quem estiver dentro da área na hora vai para fora da cerca.
- **Construção:** antes de construir, cada bloco da área é guardado; ao recolher, tudo volta como era. **Ninguém mexe na construção**, nem o dono nem a staff. Fica protegida contra:
  - quebrar e colocar blocos, balde, e usar item ou clicar nos blocos;
  - pisotear;
  - explosão, pistão, água e lava escorrendo, fogo, ejetor, esponja;
  - enderman, árvore crescendo, grama espalhando, plantação crescendo ou secando.

  A única forma de tirar é **recolher a Escritura** no fazendeiro. A staff pode usar o `/fazendas remover` olhando para a fazenda: a Escritura vai para quem removeu, e o cofre é descartado.
- **Visual de cada fazenda** (`Planos.java`): todas têm a moldura com o material de cada uma (chão da borda, cerca ou muro, postes nos cantos com lanterna). A frente, com a abertura onde fica o fazendeiro, é o lado de quem colocou: o desenho gira junto, com os blocos que têm direção.

  | Fazenda | Construção | Bichos |
  |---|---|---|
  | Horta | Canteiros de cenoura, batata e beterraba, rego d'água, composteira e espantalho | |
  | Galinheiro | Casinha de bétula com telhado de feno, ninhos de feno e flores | 4 galinhas |
  | Pomar | Duas macieiras (uma florida) e cestos de colheita | maçãs penduradas |
  | Pocilga | Chão de lama, cocho e feno | 2 porcos |
  | Trigal | Trigo maduro, rego d'água, feno e o mastro do moinho com as pás | |
  | Curral | Pasto, feno empilhado e bebedouro | 2 vacas |
  | Criação de Ovelhas | Pasto e fardos de lã colorida | 3 ovelhas (branca, cinza e rosa) |
  | Apiário | Colmeias cheias de mel e jardim de flores | 3 abelhas |
  | Abóboras e Melancias | Abóboras de um lado, melancias do outro, caules ligados e rego | |
  | Cogumelos Gigantes | Cogumelo vermelho gigante sobre micélio e cogumelos pequenos | |
  | Verrugas do Nether | Verrugas maduras na areia das almas, fogueira das almas, cerca do Nether | |
  | Estufa de Chorus | Hastes de chorus florindo sobre pedra do End, muro de pedra do End e bastões do End | |

  Dentro da área não há física: a plantação não cresce nem seca, nada cai, e a água do rego não escorre.
- **Fazendeiro, bichos e holograma:** o NPC (um Mannequin, com o item do tipo na mão: `mao`, e a skin opcional: `skin`), os bichos (sem IA: não andam, não botam ovo, não tomam dano; clicar num deles abre o menu, como no fazendeiro) e o holograma (dinheiro no cofre, insumo e estado) não ficam salvos no mundo. Aparecem quando o chunk carrega e somem quando ele descarrega, por isso nunca duplicam.
- **Itens:** a Escritura, a Ração e o Adubo têm textura própria no pacote do servidor (`tools/pacote/texturas.py`).
- **Produção:** segue o relógio, mesmo com o dono offline ou o terreno descarregado. Com o dono offline, anda na metade da velocidade (`producao-offline`). Cada safra põe a renda do tipo no cofre e gasta `insumo-por-safra`. Para quando o insumo acaba ou o cofre enche (96 safras, mais 64 por nível).
- **Menu (clique no fazendeiro, só o dono):**
  - **Coletar:** o cofre vai para o saldo, com o bônus do rank e do VIP, menos 2% de imposto.
  - **Abastecer:** põe a Ração ou o Adubo do inventário na despensa. Atalho: clicar no fazendeiro com o insumo na mão.
  - **Melhorias**, até o nível 5, cada nível custando 30% do preço × (nível + 1):
    - **Produtividade:** -5% de tempo por nível;
    - **Cofre:** +64 safras;
    - **Despensa:** +64 safras de insumo.
  - **Achados raros:** pega os itens raros que a fazenda guardou.
  - **Recolher a Escritura:** pede confirmação. Coleta o cofre, devolve o insumo que sobrou, os achados e a Escritura com os níveis, e a construção some.
- **Achado raro:** a cada safra, a fazenda tem uma chance de achar o item raro dela, que fica guardado até o dono pegar no menu (o holograma mostra quantos; o dono online é avisado no chat).
  - **Frequência:** a chance sai pra dar ~1 achado a cada 48 h de produção (`raro.horas-online`), no ritmo do nível 0; com a Produtividade, um pouco mais.
  - **Valor:** 1,5 h da renda da fazenda (`raro.horas-de-renda`). Vende no Corretor Rural (o botão "Vender achados raros" vende todos de uma vez), com o bônus do rank e do VIP, menos o imposto, como a coleta. Soma ~3% na renda.
  - **O item:** papel com o visual de um item do Minecraft e brilho (`raro.modelo` de cada tipo): não se come, não se planta e não entra em receita. A staff dá com `/fazendas dar <jogador> raro <tipo> [qtd]`.

  | Fazenda | Achado | Fazenda | Achado |
  |---|---|---|---|
  | Horta | Cenoura Premiada | Curral | Leite Premiado |
  | Galinheiro | Ovo Dourado | Criação de Ovelhas | Lã de Ouro |
  | Pomar | Maçã Reluzente | Apiário | Favo Real |
  | Pocilga | Trufa Negra | Abóboras e Melancias | Abóbora Premiada |
  | Trigal | Espiga de Ouro | Cogumelos Gigantes | Cogumelo Luminoso |
  | Verrugas do Nether | Verruga Ancestral | Estufa de Chorus | Fruta do Vazio |
- **Rank:** só coloca e mexe na fazenda quem tem o rank dela, ou quem já fez prestígio. Uma Escritura acima do rank pode ser guardada e negociada.
- **A fazenda é do terreno** (conferido a cada 20 s, em todas, e a cada clique):
  - **Leilão:** com o terreno em leilão (imposto atrasado), ninguém mexe na fazenda, nem o antigo dono; ela continua produzindo. O Leiloeiro conta as fazendas do terreno junto com as máquinas e os spawners ("E tem mais: 1 fazenda, pronta pra trabalhar!").
  - **Novo dono:** se quem tem a fazenda não constrói mais no terreno (perdeu no leilão, deixou de ser amigo) e o terreno tem dono, a fazenda passa pro dono do terreno, com o cofre e o insumo. Quem arremata leva tudo, e é avisado.
  - **À venda:** terreno abandonado ou leilão sem lance volta à venda e o TerrenosPlugin apaga tudo o que tinha dentro; a fazenda é desfeita junto, sem devolver a Escritura.
  - **Construção apagada por fora** (a limpeza do terreno, WorldEdit): se os quatro postes dos cantos sumiram, a fazenda sai do registro.

## Fazendas

Calibradas pelo `tools/economia/fazendas.py` (2026-11-17): cada uma lucra ~7% da mina do rank em que libera (renda menos insumo, sem bônus) e se paga em ~20 h de produção online. Com 4 fazendas no máximo num terreno, dá ~37% da mina.

| Fazenda | Insumo | Rank | Preço | Lucro/h |
|---|---|---|---|---|
| Horta | Adubo | Camponês | R$ 7.500 | ~R$ 375 |
| Galinheiro | Ração | Fazendeiro | R$ 12 mil | ~R$ 600 |
| Pomar | Adubo | Lenhador | R$ 21 mil | ~R$ 1.050 |
| Pocilga | Ração | Ferreiro | R$ 40 mil | ~R$ 2.000 |
| Trigal | Adubo | Mercador | R$ 59 mil | ~R$ 2.960 |
| Curral | Ração | Escudeiro | R$ 160 mil | ~R$ 7.800 |
| Criação de Ovelhas | Ração | Clérigo | R$ 200 mil | ~R$ 10.200 |
| Apiário | Adubo | Cavaleiro | R$ 310 mil | ~R$ 15.600 |
| Abóboras e Melancias | Adubo | Barão | R$ 440 mil | ~R$ 22.200 |
| Cogumelos Gigantes | Adubo | Conde | R$ 560 mil | ~R$ 28.000 |
| Verrugas do Nether | Adubo | Duque | R$ 1,2 mi | ~R$ 58.000 |
| Estufa de Chorus | Adubo | Rei | R$ 1,5 mi | ~R$ 73.500 |

## Comandos

| Comando | Quem | O que faz |
|---|---|---|
| `/fazendas` ou `/fazendas loja` | todos | Abre a loja |
| `/fazendas dar <jogador> <tipo\|racao\|adubo> [qtd]` | staff | Dá uma Escritura ou insumo |
| `/fazendas dar <jogador> raro <tipo> [qtd]` | staff | Dá achados raros |
| `/fazendas lista` | staff | Lista as fazendas colocadas |
| `/fazendas acelerar <min>` | staff | Teste: o relógio de todas anda N minutos |
| `/fazendas remover` | staff | Remove a fazenda que está olhando (a Escritura vai para quem removeu) |
| `/fazendas reload` | staff | Recarrega o config |

Permissão da staff: `fazendas.admin`.
