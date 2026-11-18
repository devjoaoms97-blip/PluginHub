# Modelos 3D dos bosses

Os `.bbmodel` (Blockbench) dos 6 bosses, gerados por código pelo `tools/pacote/bosses.py` (2026-11-17). Quem desenha no jogo é o **FreeMinecraftModels**:

- os arquivos vão em `plugins/FreeMinecraftModels/models/` do servidor;
- o BossPlugin põe o modelo no boss quando ele nasce (`fmm-modelo` de cada boss no config);
- o pacote que o FMM gera entra no nosso pelo PacotePlugin (`juntar`).

| Arquivo | Boss | Como é |
|---|---|---|
| `boss_tita.bbmodel` | Titã Sombrio (zumbi) | Gigante de pele cinza-azulada, armadura negra, ombreiras com espinhos, corrente e olhos vermelhos |
| `boss_carrasco.bbmodel` | Carrasco Ósseo (esqueleto) | Esqueleto de costelas aparentes, capuz negro de carrasco, olhos azuis e um machado enorme com sangue no fio |
| `boss_rainha.bbmodel` | Rainha Aracnídea (aranha) | Aranha de abdome marcado, oito olhos vermelhos, presas, coroa de ouro e oito pernas |
| `boss_acougueiro.bbmodel` | Açougueiro Infernal (piglin zumbificado) | Cabeça de porco podre com presas e orelha verde, avental sujo de sangue e um cutelo |
| `boss_estilhaco.bbmodel` | Estilhaço Verde (creeper) | Creeper com cristais verdes saindo das costas e da cabeça |
| `boss_feiticeira.bbmodel` | Feiticeira Maldita (bruxa) | Bruxa de manto roxo, chapéu pontudo torto, verruga no nariz e cajado com orbe |

Cada um tem as animações que o FMM toca sozinho: `idle`, `walk`, `attack`, `damaged`, `death` e `spawn`. O tamanho é o de um mob comum. O BossPlugin aumenta o modelo junto com o boss (`setScaleModifier` com a escala dele, 9 hoje). O Wither fica como é.

**Mudar um modelo:** edite o `bosses.py` e rode `python tools/pacote/bosses.py`. Depois copie os `.bbmodel` pro servidor e reinicie (ou `/fmm reload`). Também dá pra abrir e retocar no Blockbench, mas aí a mudança se perde no próximo `bosses.py`.
