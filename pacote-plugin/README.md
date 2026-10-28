# PacotePlugin

O resource pack do servidor: as texturas próprias (produtos e máquinas do MaquinasPlugin, as skins de armadura, ferramenta e arma do CreditosPlugin, e o que vier depois).

## Como funciona

- O pacote (`pacote.zip`) vem **dentro do jar**. Ele é gerado pelo `tools/pacote/gerar.py`, que desenha as texturas por código (`tools/pacote/texturas.py`) no estilo do spawn: pedra, vinho, carmesim, cerejeira e dourado.
- Um **HTTP embutido** serve o pacote na `porta` (8765).
- Ao entrar, cada jogador recebe o pacote com a hash SHA-1: quem já tem a versão certa não baixa de novo. O id do pacote sai da hash, então uma versão nova substitui a antiga no cliente.
- `obrigatorio: true` (padrão): quem recusa é desconectado. Sem o pacote, os itens e as máquinas aparecem com a textura de "faltando".
- `/pacote` manda de novo.

## Pacote de outros plugins

O que estiver em `juntar` no config (hoje, o zip que o FreeMinecraftModels gera com os modelos dele) entra no mesmo pacote: o cliente só recebe um pacote daqui, e quando os dois têm o mesmo arquivo fica o nosso. A cada 5 s o plugin confere se algum desses zips mudou (o FMM gera o dele uns segundos depois do servidor abrir, e de novo num `/fmm reload`) e, se mudou, junta de novo e manda pra quem está online. `/pacote recarregar` (staff) faz isso na hora.

## Terceiros

`tools/pacote/terceiros/` guarda modelos de outros autores, cada pasta com um LEIA-ME de origem e licença: `artillex` (asas e chapéus, entram no pacote como `servidor:asa_<id>` e `servidor:chapeu_<id>`) e `frenchkrab` (as montarias do FMM; aqui só entram os ícones, desenhados pelo `icones.py`: `servidor:icone_<arquivo>`).

## Skins (CreditosPlugin)

`tools/pacote/skins.py` desenha as coleções de skin: cada uma é uma paleta (metal, detalhe e cabo) aplicada às mesmas 9 máscaras (capacete, peitoral, calça, botas, espada, machado, picareta, pá e enxada). Sai o item `servidor:skin_<coleção>_<peça>` e, pra armadura vestida, o equipamento `servidor:skin_<coleção>` (camadas `humanoid` e `humanoid_leggings`, 64x32). Coleção nova: uma entrada em `COLECOES` e outra em `skins` no config do CreditosPlugin, com o mesmo id.

## Montarias (MontariaPlugin)

`tools/pacote/montarias.py` desenha os bichos das skins de montaria como caixas 3D, em pixels do mundo (chão em y = 0, frente pra +z), encolhidos 3x pra caber no limite do modelo (-16 a 32); o item display do MontariaPlugin aumenta de novo. Cada bicho sai em 5 quadros (`montaria_<id>` parado e `montaria_<id>_0..3` andando), com uma textura de paleta só pra todos. Bicho novo: uma função em `montarias.py`, uma entrada em `BICHOS` e as entradas nos configs do CreditosPlugin (`cosmeticos.montarias`) e do MontariaPlugin (`skins`).

## No servidor de verdade

`url-publica` precisa ser o endereço que os jogadores alcançam, com a porta liberada no firewall:

```yaml
url-publica: "http://meuservidor.com.br:8765/pacote.zip"
```

Vazio, o plugin usa `http://localhost:8765/pacote.zip`, que só funciona pra quem joga na mesma máquina (o servidor de teste).

## Mudar uma textura

1. Edite `tools/pacote/texturas.py` (cada textura é uma função; a paleta fica no topo).
2. `python tools/pacote/gerar.py --previa` (gera o `pacote.zip` e uma prévia ampliada em `tools/pacote/previa.png`).
3. Recompile o PacotePlugin e reinicie: a hash muda e os jogadores baixam o pacote novo.

O formato do pacote é o **88** (o `pack_version.resource_major` do Paper 26.2). Numa versão nova do jogo, troque o `FORMATO` no `gerar.py`.

## Como compilar

```bash
python tools/pacote/gerar.py
mvn package
```

O `.jar` sai em `target/PacotePlugin.jar`.
