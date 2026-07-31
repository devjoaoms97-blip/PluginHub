# ChequePlugin

Plugin para Spigot/Paper (1.20.x) que transforma papel em um **cheque** resgatável por dinheiro real do servidor (via Vault).

## O que ele faz

1. Um jogador com permissão segura **papel** na mão e digita:
   ```
   /cheque <valor>
   ```
   Exemplo: `/cheque 250` cria um cheque de R$ 250,00.

2. O papel na mão é consumido (1 unidade) e vira um item **Cheque**, com nome, dono, data de emissão e valor gravado de forma segura dentro do item (não é só texto — usa `PersistentDataContainer`, então não dá pra falsificar editando o nome/lore no bigorna).

3. Qualquer jogador com o cheque em mãos pode **clicar com o botão direito** para resgatá-lo: o valor é depositado na conta dele (via Vault) e o item é consumido.

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
| `cheque.criar`    | op     | Permite criar cheques (`/cheque`)   |
| `cheque.resgatar` | true   | Permite resgatar cheques (clique)   |

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

## Possíveis melhorias futuras

- Cheque "nominal" (só quem foi especificado pode resgatar)
- Cancelar/estornar um cheque emitido por engano
- Log de cheques emitidos/resgatados em um arquivo ou banco de dados
- Cooldown ou limite de valor máximo por cheque
