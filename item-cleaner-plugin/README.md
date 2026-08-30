# ItemCleanerPlugin

Limpa periodicamente os itens dropados no chão (drops de mob, blocos minerados, PvP, farms automáticas, etc.) pra reduzir lag causado por muitas entidades acumuladas — sem pegar o jogador de surpresa: avisa no chat em contagem regressiva antes de limpar.

## Como funciona

- A cada `intervalo-segundos` (padrão: 300s / 5 min), o plugin remove todos os itens no chão.
- Antes disso, avisa no chat nos segundos definidos em `avisos-segundos` (padrão: 60, 30, 10, 5).
- Só remove itens que já estão no chão há pelo menos `idade-minima-segundos` — assim ninguém perde algo que acabou de cair na sua frente.
- Por padrão, **não remove itens renomeados** (ex: na bigorna) — geralmente é sinal de que o jogador guardou aquilo de propósito.
- Dá pra restringir a mundos específicos (`mundos.modo` + `mundos.lista`), por exemplo pra nunca limpar num mundo de museu/spawn onde itens ficam expostos.

## Comandos

| Comando | Ação |
|---|---|
| `/limparchao` | (staff) Avisa no chat e limpa em 10 segundos |
| `/limparchao agora` | (staff) Limpa na hora, sem aviso |
| `/limparchao status` | (staff) Mostra quantos segundos faltam pra próxima limpeza automática |
| `/limparchao reload` | (staff) Recarrega o `config.yml` e reinicia o temporizador |

## Permissões

| Permissão | Padrão | Descrição |
|---|---|---|
| `itemcleaner.admin` | op | Permite usar `/limparchao` e seus subcomandos |

## Requisitos

- Paper 26.2 (Java 25)
- Não depende de Vault nem de nenhum outro plugin — funciona sozinho

## Como compilar

```bash
cd item-cleaner-plugin
mvn clean package
```

O `.jar` sai em `target/ItemCleanerPlugin.jar`.

## ⚠️ Simplificações técnicas

1. **Só remove entidades do tipo `Item`** (itens dropados no chão) — não mexe em orbes de experiência, minecarts, boats, item frames, armor stands ou qualquer outra entidade. Se quiser incluir orbes de XP também (outra fonte comum de lag), é só pedir.
2. **A contagem regressiva roda a cada segundo (1 tick a cada 20 ticks do servidor)**, então o timing dos avisos é preciso o suficiente pra esse uso, mas não é "tempo real" milissegundo a milissegundo.
3. **Limpeza manual (`/limparchao` sem argumento) sempre avisa 10s antes** — esse valor é fixo no código, não configurável via `config.yml` (só o intervalo automático é). Se quiser deixar configurável também, é rápido de adicionar.
4. **Não protege itens perto de jogadores especificamente** — o filtro é só por idade e por nome customizado. Se quiser algo tipo "não limpar itens a menos de X blocos de um jogador", dá pra adicionar.
