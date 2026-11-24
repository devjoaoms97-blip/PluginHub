# PluginHub (BrasaMC)

## Toda novidade, melhoria ou correção vira post no Discord

Cada mudança que o jogador percebe (feature nova, melhoria, correção de bug, balanceamento, evento novo)
ganha um post no Discord oficial, junto com a própria mudança, sem esperar acumular:

- **#changelog**: sempre. Um arquivo novo em `discord-bot/conteudo/changelog/`, com o nome
  `AAAA-MM-DD-assunto.md`; o bot (Faísca) publica um embed por arquivo, na ordem, e edita quando o
  arquivo muda. Primeira linha `# título`; depois, em linguagem de jogador (sem nome de classe, plugin ou
  arquivo), o que mudou, separado em **✨ Novidades**, **🔧 Melhorias** e **🐛 Correções** (só as seções
  que tiverem item), e como usar (o comando ou o `/warp`).
- **#anuncios**: só o que é grande (sistema novo, evento novo, data de lançamento, temporada). Arquivo em
  `discord-bot/conteudo/anuncios/`, mesmo formato; marca o cargo 🔔 Novidades.
- Mudança só de bastidor (refatoração, log, script de teste, ferramenta interna) não vira post.
- Antes do lançamento os posts contam o que está sendo preparado ("diário de desenvolvimento"), sem IP.
- O post faz parte da entrega: ao terminar a implementação, mostre o texto do post junto com o resumo
  e publique quando o usuário aprovar (o mesmo cuidado de commit: nada sai pro Discord sem ele ver).
