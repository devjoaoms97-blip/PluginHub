# ChequePlugin

Cheques entre jogadores, com dinheiro de verdade do servidor (via Vault). O cheque nasce numa **folha de cheque** comprada no **Bancário** e só vira dinheiro depositado no Bancário.

## O que ele faz

1. O jogador compra uma **folha de cheque** em branco no Bancário (NPC do OficinasPlugin, tipo `bancario`), por `banco.preco-folha` (R$ 50).
2. Com a folha na mão, digita `/cheque <valor> [jogador]`. Exemplo: `/cheque 250` cria um cheque de R$ 250,00.
3. O valor sai do saldo de quem emite (sem saldo, não emite). A folha é consumida (1 unidade) e vira um item **Cheque**, com nome, dono, data de emissão e valor gravado de forma segura dentro do item (não é só texto — usa `PersistentDataContainer`, então não dá pra falsificar editando o nome/lore no bigorna). Papel comum não serve.
4. Quem está com o cheque leva ao Bancário e clica em **Depositar cheques**: todos os cheques do inventário caem na conta de quem depositou, menos a **taxa de compensação** (`banco.taxa-deposito-percentual`, 1%, igual à do Pix, senão o cheque seria um Pix sem taxa). O depósito aparece no `/pix historico` como transação do emissor pra quem depositou. O clique direito no cheque só avisa pra levar ao Bancário.

A folha e a taxa somem da economia. `/cheque` é liberado pra todos (`cheque.criar`, padrão true).

## Cheque nominal, limites e log

- `/cheque <valor> [jogador]`: com o jogador, o cheque é **nominal**. O nome vai no lore e só ele deposita (no depósito de outro, o cheque fica no inventário).
- **Limites** (`config.yml`): `limite.valor-maximo` por cheque e `limite.cooldown-segundos` entre emissões. A permissão `cheque.limite.ignorar` pula os dois.
- **Log**: cada emissão, resgate e recusa vira uma linha em `plugins/ChequePlugin/cheques.log`, com data, jogador, valor e o ID do cheque.
- **Anti-cópia**: o ID de cada cheque resgatado fica em `resgatados.yml`. Um cheque com ID já resgatado (cópia de um dupe) é recusado, anulado e registrado no log.

## Cancelar um cheque

- `/cheque cancelar`, com o cheque na mão: anula ele (some do inventário). Vale pra quem emitiu e pra staff (`cheque.admin`).
- `/cheque cancelar <id>`: só a staff. Anula pelo ID que aparece no lore (8 caracteres) ou pelo inteiro do `cheques.log`, mesmo com o cheque no inventário de outra pessoa. Quem tentar resgatar depois perde o cheque, e a recusa vai pro log.

Ao cancelar, o valor volta pro saldo de quem emitiu (avisado no chat, se estiver online). Os cheques em aberto ficam em `emitidos.yml` (quem emitiu e o valor), e os cancelados em `cancelados.yml`. Cheques emitidos antes do valor sair do saldo não têm o que devolver nem o ID no lore: a staff cancela com o ID do log, sem devolução.

## Requisitos

- Servidor **Paper** 26.2 (requer Java 25)
- Plugin **[Vault](https://www.spigotmc.org/resources/vault.34315/)** instalado
- Um plugin de economia compatível com Vault (ex: EssentialsX, CMI, etc.)

## Como compilar

Você precisa do [Maven](https://maven.apache.org/) e do JDK 25 instalados na sua máquina (o ambiente onde eu gerei este código não tem acesso ao repositório central do Maven, então não consegui compilar aqui — mas o código está pronto para compilar no seu computador).

```bash
cd cheque-plugin
mvn clean package
```

O `.jar` final vai aparecer em `target/ChequePlugin.jar`. Copie esse arquivo para a pasta `plugins/` do seu servidor (junto com o Vault e um plugin de economia) e reinicie.

## Permissões

| Permissão        | Padrão | Descrição                          |
|-------------------|--------|-------------------------------------|
| `cheque.criar`    | true   | Permite emitir cheques (`/cheque`, com a folha na mão) |
| `cheque.resgatar` | true   | Permite depositar cheques no Bancário |
| `cheque.admin`    | op     | Cancela qualquer cheque, inclusive pelo ID |

## Estrutura do projeto

```
cheque-plugin/
├── pom.xml
├── README.md
└── src/main/
    ├── java/com/example/cheque/
    │   ├── ChequePlugin.java     # classe principal, integra com Vault
    │   ├── ChequeCommand.java    # comando /cheque
    │   └── ChequeListener.java   # resgate ao clicar
    └── resources/
        └── plugin.yml
```
