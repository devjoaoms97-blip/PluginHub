# FazendasPlugin

Fazendas nos terrenos que rendem **dinheiro** (não dão itens). Uma **Escritura** vira uma construção 7x7 com um fazendeiro parado na abertura da cerca. Cada safra põe dinheiro no cofre, gastando **Ração** (fazendas de animais) ou **Adubo** (plantações). O dono passa no fazendeiro e coleta.

Estado: **fase 1** (o funcionamento). Todas as fazendas usam a mesma construção simples; o visual próprio de cada uma, o balcão na Loja Oficial e o prêmio raro vêm nas próximas fases (veja o `ROADMAP.md`).

## Como funciona

- **Compra:** `/fazendas loja` (provisório; depois, o balcão Corretor Rural). Cada fazenda libera num rank. Lá também se compram a Ração e o Adubo (R$ 25 cada).
- **Escritura:** é um item comum, que dá para guardar, vender na barraca ou trocar. Ela guarda o tipo e os níveis das melhorias.
- **Colocar:** com a Escritura na mão, clique direito no chão do terreno. O bloco clicado é o centro.
  - O 1º clique mostra o contorno da área 7x7: **verde** se pode construir; **vermelho** com o motivo se não pode.
  - O 2º clique no mesmo bloco, em até 30 s, constrói.
  - **Condições da área:** ficar dentro de um terreno onde o jogador constrói (dono ou amigo com "construir"), no mundo dos terrenos (`mundos`); chão plano e firme (sem buraco, baú ou bloco especial); 3 blocos livres por cima (ar, grama, flor); não encostar em outra fazenda.
  - **Limite:** até 4 fazendas por terreno (`limite-por-terreno`), somando todos os tipos e donos, separado do limite das máquinas.
  - Quem estiver dentro da área na hora vai para fora da cerca.
- **Construção:** antes de construir, cada bloco da área é guardado; ao recolher, tudo volta como era. **Ninguém mexe na construção**, nem o dono nem a staff. Fica protegida contra:
  - quebrar e colocar blocos, balde, e usar item ou clicar nos blocos;
  - pisotear;
  - explosão, pistão, água e lava escorrendo, fogo, ejetor, esponja;
  - enderman, árvore crescendo, grama espalhando, plantação crescendo ou secando.

  A única forma de tirar é **recolher a Escritura** no fazendeiro. A staff pode usar o `/fazendas remover` olhando para a fazenda: a Escritura vai para quem removeu, e o cofre é descartado.
- **Fazendeiro e holograma:** o NPC (um Mannequin) e o holograma (dinheiro no cofre, insumo e estado) não ficam salvos no mundo. Aparecem quando o chunk carrega e somem quando ele descarrega, por isso nunca duplicam.
- **Produção:** segue o relógio, mesmo com o dono offline ou o terreno descarregado. Com o dono offline, anda na metade da velocidade (`producao-offline`). Cada safra põe a renda do tipo no cofre e gasta `insumo-por-safra`. Para quando o insumo acaba ou o cofre enche (96 safras, mais 64 por nível).
- **Menu (clique no fazendeiro, só o dono):**
  - **Coletar:** o cofre vai para o saldo, com o bônus do rank e do VIP, menos 2% de imposto.
  - **Abastecer:** põe a Ração ou o Adubo do inventário na despensa. Atalho: clicar no fazendeiro com o insumo na mão.
  - **Melhorias**, até o nível 5, cada nível custando 30% do preço × (nível + 1):
    - **Produtividade:** -5% de tempo por nível;
    - **Cofre:** +64 safras;
    - **Despensa:** +64 safras de insumo.
  - **Recolher a Escritura:** pede confirmação. Coleta o cofre, devolve o insumo que sobrou e a Escritura com os níveis, e a construção some.
- **Rank:** só coloca e mexe na fazenda quem tem o rank dela, ou quem já fez prestígio. Uma Escritura acima do rank pode ser guardada e negociada.

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
| `/fazendas lista` | staff | Lista as fazendas colocadas |
| `/fazendas acelerar <min>` | staff | Teste: o relógio de todas anda N minutos |
| `/fazendas remover` | staff | Remove a fazenda que está olhando (a Escritura vai para quem removeu) |
| `/fazendas reload` | staff | Recarrega o config |

Permissão da staff: `fazendas.admin`.
