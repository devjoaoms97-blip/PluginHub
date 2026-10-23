# RecursosPlugin

Mundo de recursos: minérios, árvores, mobs, pesca e PvP. O `/recursos` leva a um ponto aleatório seguro, e o mundo é **resetado de tempos em tempos** para não crescer sem fim. No reset, tudo o que estiver lá some, então quem quer guardar algo leva para o terreno.

## Como funciona

- **`/recursos`** procura um ponto aleatório até 2.500 blocos do centro, dentro da borda. O ponto precisa ter chão firme e espaço livre em cima, sem água, lava, cacto, fogo, magma ou neve fofa. Os chunks carregam sem travar o servidor, e há 30 s de espera entre um uso e outro.
- **Borda** de 6.000 blocos (de -3.000 a 3.000), reaplicada ao ligar e depois de cada reset.
- **Reset automático** a cada 30 dias, às 05:00 do dia em que vence, contando do último reset (ou da instalação):
  1. avisos no chat 60, 30, 10, 5 e 1 minuto antes;
  2. quem está no mundo é levado para fora (`saida.mundo`, ou o mundo principal);
  3. o Multiverse regenera o mundo com uma seed nova, mantendo as regras do mundo e a borda (`mv regen <mundo> --seed`).
- **Quem saiu do servidor dentro do mundo de recursos** e volta depois de um reset é levado a um ponto seguro do mundo novo, com um aviso. Sem isso, o jogador apareceria no mundo principal nas coordenadas antigas, porque o servidor não reconhece mais o mundo em que ele estava.

## Comandos

| Comando | O que faz |
|---|---|
| `/recursos` (ou `/rtp`) | Vai para um ponto aleatório seguro do mundo de recursos |
| `/recursos info` | Data do próximo reset |
| `/recursos admin resetar [minutos\|agora]` | Marca um reset (padrão: em 5 min, com os avisos) ou reseta na hora |
| `/recursos admin cancelar` | Cancela o reset marcado (o automático continua) |
| `/recursos admin reload` | Recarrega o config e reaplica a borda |

`recursos.admin` (op) também ignora a espera do `/recursos`.

## Config

```yaml
mundo: recursos
reset:
  ativado: true
  a-cada-dias: 30
  hora: "05:00"
  avisos-minutos: [60, 30, 10, 5, 1]
saida:
  mundo: ""          # vazio = mundo principal; com o mapa novo, "spawn"
borda:
  tamanho: 6000      # 0 = sem borda
  centro-x: 0
  centro-z: 0
teleporte:
  raio: 2500
  tentativas: 20
  cooldown-segundos: 30
```

## Requisitos

Multiverse-Core: cria o mundo (`mv create recursos normal`, ou o `configurar-mundos.bat` do servidor de teste) e faz o regen. A data do último reset e o ciclo ficam em `dados.yml`.
