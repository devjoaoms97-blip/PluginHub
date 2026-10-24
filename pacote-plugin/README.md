# PacotePlugin

O resource pack do servidor: as texturas próprias (produtos e máquinas do MaquinasPlugin, e o que vier depois).

## Como funciona

- O pacote (`pacote.zip`) vem **dentro do jar**. Ele é gerado pelo `tools/pacote/gerar.py`, que desenha as texturas por código (`tools/pacote/texturas.py`) no estilo do spawn: pedra, vinho, carmesim, cerejeira e dourado.
- Um **HTTP embutido** serve o pacote na `porta` (8765).
- Ao entrar, cada jogador recebe o pacote com a hash SHA-1: quem já tem a versão certa não baixa de novo. O id do pacote sai da hash, então uma versão nova substitui a antiga no cliente.
- `obrigatorio: true` (padrão): quem recusa é desconectado. Sem o pacote, os itens e as máquinas aparecem com a textura de "faltando".
- `/pacote` manda de novo.

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
