# ChequePlugin

Cheques entre jogadores, com dinheiro de verdade do servidor (via Vault). O cheque nasce numa **folha de cheque** comprada no **Bancário** e só vira dinheiro depositado no Bancário.

## O que ele faz

1. O jogador compra uma **folha de cheque** em branco no Bancário (NPC do OficinasPlugin, tipo `bancario`), por `banco.preco-folha` (R$ 50).
2. Com a folha na mão, digita `/cheque <valor> [jogador]`. Exemplo: `/cheque 250` cria um cheque de R$ 250,00.
3. O valor sai do saldo de quem emite (sem saldo, não emite). A folha é consumida (1 unidade) e vira um item **Cheque**, com nome, dono, data de emissão e valor gravado de forma segura dentro do item (não é só texto — usa `PersistentDataContainer`, então não dá pra falsificar editando o nome/lore no bigorna). Papel comum não serve.
4. Quem está com o cheque leva ao Bancário e clica em **Depositar cheques**: todos os cheques do inventário caem na conta de quem depositou, menos a **taxa de compensação** (`banco.taxa-deposito-percentual`, 1%, igual à do Pix, senão o cheque seria um Pix sem taxa). O clique direito no cheque só avisa pra levar ao Bancário.
5. **Extrato:** no mesmo balcão, o botão **Extrato** abre um livro com as últimas `banco.extrato-transacoes` (30) movimentações de dinheiro do jogador (o histórico do PixPlugin: Pix, loja, oficinas, rankup, cheques, Mine-sena, boss, VIP...). Nele, a folha e o cheque emitido saem de quem emite, o cancelamento devolve, e o depósito entra pra quem deposita, já sem a taxa.

A folha e a taxa somem da economia. `/cheque` é liberado pra todos (`cheque.criar`, padrão true).

## Mine-sena

A loteria do Bancário. No balcão, o botão **Mine-sena** abre os números de 1 a `minesena.numero-maximo` (100), em páginas. Cada clique compra um bilhete daquele número por `minesena.preco-bilhete` (R$ 500). O número fica verde com a quantidade de bilhetes que o jogador tem nele.

- Todo dia às `minesena.horario` (18:00, hora do servidor) sai o número premiado. O **pote inteiro** (todas as apostas acumuladas) é dividido entre os bilhetes com esse número: quem tem 2 bilhetes no número leva 2 partes.
- **Sem ganhador, acumula** pro sorteio seguinte.
- O bilhete fica registrado no nome do jogador (não é item: não dá pra duplicar nem perder) e vale pro próximo sorteio. Cada um compra até `minesena.maximo-bilhetes-por-jogador` (20) por sorteio.
- Se o servidor estava desligado no horário, o sorteio sai assim que ele liga. Sem aposta e sem pote, não há anúncio.
- O resultado vai pro chat e pro Discord (categoria `minesena` do DiscordPlugin). Quem ganhou offline é avisado ao entrar. A aposta e o prêmio aparecem no extrato do Bancário.
- `/minesena` mostra o pote, o horário, os seus números e o último resultado. A staff (`cheque.admin`) sorteia na hora com `/minesena sortear [número]` (sem número, sorteia de verdade). Tudo fica em `minesena.yml`.
- **Bilhetes grátis:** `/minesena dar <jogador> <quantidade> [motivo]` (staff ou console) dá bilhetes grátis, guardados no nome do jogador. Ele escolhe os números no Bancário como sempre, e enquanto tiver grátis o clique não cobra (o número mostra "GRÁTIS"). O grátis não põe dinheiro no pote e conta no limite de bilhetes por sorteio; o que sobrar fica pro próximo. O bot do Discord dá 5 a quem vincula a conta.

O dinheiro das apostas não sai da economia: volta inteiro pra quem ganha.

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
