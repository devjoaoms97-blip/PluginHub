# Servidor de teste

Monta um servidor Paper local com todos os plugins do PluginHub, configs pensados pra testar rápido e backup automático do mundo.

O servidor fica **fora do repositório** (padrão: `C:\mc-teste`). Aqui só ficam os scripts e os configs. O mundo, os jars e os dados de jogador nunca entram no Git.

## Montar

1. Instale o **Java 25** (Temurin, em adoptium.net).
2. Compile os plugins (`mvn package` em cada pasta). Os jars ficam em `*/target/`.
3. Rode `test-server\montar.bat`.

Rodar o `montar.bat` significa aceitar o [EULA do Minecraft](https://aka.ms/MinecraftEULA), porque ele grava `eula=true`.

O `montar.bat`:
- baixa o Paper 26.2, o VaultUnlocked, o EssentialsX e o LuckPerms nas versões fixas do `montar.ps1`, conferindo o sha256;
- copia os nossos jars;
- grava o `server.properties`: offline, só `127.0.0.1`, RCON ligado com uma senha gerada na hora;
- coloca os configs de teste de `configs/`;
- copia os atalhos e os scripts pra pasta do servidor.

Pode rodar de novo quando quiser: ele só baixa o que falta e não mexe no mundo, nos dados nem em configs que já existem. Opções:

```
montar.bat -Destino D:\outro-servidor
montar.bat -SobrescreverConfigs      (volta os configs de teste aos daqui)
```

Pra atualizar a versão do Paper ou de um plugin de fora, troque a URL e o sha256 no topo do `montar.ps1`.

## Usar (na pasta do servidor)

| Atalho | O que faz |
|---|---|
| `iniciar.bat` | Liga o servidor. Quando ele desliga (`stop`), faz um backup sozinho. |
| `atualizar-plugins.bat` | Copia os jars recompilados do repositório pra `plugins/`. Rode com o servidor desligado. |
| `backup.bat` | Backup na hora, com o servidor ligado ou desligado. |
| `restaurar.bat` | Lista os backups e restaura o escolhido. Rode com o servidor desligado. |
| `agendar-backup.bat` | Liga o backup automático a cada 2 h (`-Horas 1` muda o intervalo e `-Remover` desliga). |

Entre em `localhost` pelo Minecraft. Na primeira vez, rode `op <seu nick>` e `eco give <nick> 100000` no console. O `online-mode=false` deixa entrar com vários nicks no mesmo PC, pra testar com 2 ou 3 jogadores.

## Backup

Cada backup é um `.zip` em `backups/` com o mundo (`world*`), os dados e configs dos plugins (sem os jars) e os arquivos do servidor (`server.properties`, `ops.json`...).

- **Com o servidor ligado**, o script usa o RCON: `save-off`, `save-all flush`, copia e `save-on`. Assim o mundo não é copiado no meio de um salvamento. No fim, avisa no chat.
- **Com o servidor desligado**, só copia. No backup agendado, ele pula se nada mudou desde o último.
- O `iniciar.bat` faz um backup sempre que o servidor desliga, inclusive depois de um crash.
- Ficam os **20 mais recentes**. Mude `manterBackups` em `scripts\pluginhub.json` pra guardar mais ou menos. A pasta dos backups também é configurada lá (`backups`), por exemplo pra outro disco ou pra uma pasta sincronizada com a nuvem.
- O `restaurar.bat` faz um backup do estado atual antes de restaurar, então dá pra desfazer.
- O histórico fica em `backups\backup.log`.

## Configs de teste

Só os plugins com valores mudados têm config aqui. Os outros usam o padrão do próprio plugin.

| Plugin | Mudança | Por quê |
|---|---|---|
| BountyPlugin | `anti-farm.mesmo-ip: false`, `cooldown-horas: 0`, anúncios a partir de R$ 100 | Todas as contas de teste têm o mesmo IP |
| BarracasPlugin | Aluguel de 1 h, aviso 5 min antes, leilão de 2 min | Ver vencimento e renovação automática sem esperar 6 h |
| PainelEconomiaPlugin | `alerta.minimo: 100` | Conseguir disparar o alerta |
| BossPlugin | Vida de 500 (800 no Wither) | Matar rápido |
| MissoesPlugin | Quantidades em cerca de 1/10 | Completar rápido |

Os configs são cópias completas. Se um plugin ganhar uma opção nova, ela não aparece aqui até alguém atualizar o arquivo. O plugin usa o valor padrão até lá.

## Limitações

- **EssentialsX 2.22.0** avisa "unsupported server version" no 26.2 (oficialmente vai até o 26.1.2), mas a economia funciona.
- **O backup automático só roda com você logado no Windows** (tarefa do Agendador sem administrador).
- **O RCON escuta só em `127.0.0.1`**, igual ao servidor. Não abra a porta 25575 pra fora.
