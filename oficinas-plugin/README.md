# OficinasPlugin

NPCs de serviço nas barraquinhas da mina. Cada um fica atrás do balcão de uma barraca temática na praça de entrada da Avenida das Minas (estrutura `oficinas` do EstruturasPlugin). Clique direito no NPC:

| NPC | Barraca | O que faz |
|---|---|---|
| ⚒ **Ferreiro** | Deepslate, bigorna, alto-forno, lava e chaminé fumegando (leste, x 10..18) | Menu com os itens gastos do seu inventário e o preço do reparo de cada um. Clique pra reparar um, ou "Reparar tudo" |
| ⛏ **Picareteiro** | Pinho, mostruário de minérios, barris e picaretas nos quadros (leste, x 21..29) | Vende as ferramentas da lista do config, encantadas ou não |
| ✦ **Bruxa** | Carvalho escuro, estantes, vitral roxo, caldeirão, velas e ametista (oeste, x -18..-10) | Abre uma mesa de encantamento com o poder máximo (nível 30). O lápis é por conta dela: o espaço vem cheio e travado, e o jogador só gasta o XP |
| 📖 **Sábio** | Estantes, quartzo e lápis (oeste, x -29..-21) | Vende níveis de XP |

## Preços

- **Reparo**: `preco-cheio` do material (a primeira chave que aparece no nome do item, ex: `DIAMOND`) × fração gasta × (1 + 12% por nível de encantamento). Uma picareta de diamante Eficiência V, Inquebrável III e Fortuna III pela metade custa 250 × 0,5 × 2,32 = R$ 290. Item inquebrável não aparece.
- **XP**: o custo em pontos de XP do vanilla vezes `sabio.preco-por-ponto` (do 0 ao 30 são 1.395 pontos). Ninguém compra além de `sabio.nivel-maximo`.
- **Picareteiro**: preço fixo por item em `picareteiro.itens` (material, nome, quantidade e encantamentos pelo id do Minecraft).
- **Bruxa**: `bruxa.taxa` por abrir (0 = de graça).

Todos têm o desconto de VIP (`vip-descontos`, as mesmas permissões da loja) e aparecem no `/pix historico`.

## A mesa da bruxa

A mesa fica numa câmara fechada embaixo da barraca da bruxa (centro em -14, 61, -25), com o ar em volta e 32 estantes a 2 blocos (o jogo conta 15: poder 30). O NPC abre essa mesa de longe. Por isso ninguém usa a mesa direto, e a barraca não precisa ter as estantes do jeito do vanilla. A estrutura monta a câmara e marca o lugar no config (`bruxa.mesa`); a staff muda com `/oficina mesa`.

## Comandos (staff, `oficinas.admin`)

| Comando | Ação |
|---|---|
| `/oficina criar <id> <tipo> [mundo x y z yaw]` | Põe o NPC (onde você está, ou nas coordenadas, pelo console). Tipos: `ferreiro`, `bruxa`, `picareteiro`, `sabio`. Com um id que já existe, move |
| `/oficina remover <id>` | Tira o NPC |
| `/oficina lista` | Lista as oficinas |
| `/oficina mesa [mundo x y z]` | Onde fica a mesa da bruxa |
| `/oficina abrir <tipo>` | Abre o serviço sem o NPC (pra testar) |
| `/oficina reload` | Recarrega o config (nomes e skins) |

## NPCs

Mesmo esquema dos de dicas e das barracas: Mannequin que não fica salvo no mundo (nasce quando o chunk carrega), não toma dano e volta sozinho se for removido. Ficam atrás do balcão, olhando pro passeio. Nome, descrição e skin de cada tipo ficam em `tipos.<tipo>` no config. A skin aceita `textura:<hash>` (a textura direto do textures.minecraft.net, por exemplo de uma skin do mineskin.org) ou o nick de um jogador. As temáticas que vêm no config: ferreiro de avental, minerador de capacete, bruxa de cabelo roxo e sábio de barba branca. Os lugares ficam em `oficinas.yml`.

## Montagem

`node tools/bots/montar-mapa.js construir oficinas` (ou junto com `minas`) constrói as barracas, cria os 4 NPCs, marca a mesa e põe os quadros com o que cada um vende. Teste: `node tools/bots/teste-oficinas.js`.

## Como compilar

```bash
mvn package
```

O `.jar` sai em `target/OficinasPlugin.jar`. Requisitos: Paper 26.2 e Vault (o PixPlugin é opcional).
