# 🗡️ Skills de armas: nível a nível

Cada skill de arma treina **causando dano com aquela arma** (2 XP por ponto de dano contra jogador, 1 contra mob) e dá **dano a mais com ela**. Algumas também têm uma **chance de efeito** a cada golpe. Tudo cresce um pouco a cada nível; a tabela mostra de 10 em 10:
```
Dano a mais     /nível   10    20    30    40    50
Espadas          0,5%   +5%  +10%  +15%  +20%  +25%
Machado          0,4%   +4%   +8%  +12%  +16%  +20%
Arqueiro         0,5%   +5%  +10%  +15%  +20%  +25%
Porradeiro       0,6%   +6%  +12%  +18%  +24%  +30%
Tridente         0,5%   +5%  +10%  +15%  +20%  +25%
Lanceiro         0,5%   +5%  +10%  +15%  +20%  +25%
Marreteiro       0,5%   +5%  +10%  +15%  +20%  +25%

Chance de efeito /nível   10    20    30    40    50
Porradeiro       0,2%    2%    4%    6%    8%   10%
Tridente         0,2%    2%    4%    6%    8%   10%
Lanceiro         0,2%    2%    4%    6%    8%   10%
Marreteiro       0,2%    2%    4%    6%    8%   10%
Crítico          0,2%    2%    4%    6%    8%   10%
```
**Cada uma:**
- **Espadas:** treina com espada. Só dano.
- **Machado:** treina com machado. Só dano.
- **Arqueiro:** treina com arco ou besta (conta a flecha que acerta). Só dano; o Crítico com arco aparece como **HEADSHOT!**.
- **Porradeiro:** treina com a **mão vazia**. Efeito: **Lentidão** forte por 1 s no alvo, sem aviso no chat.
- **Tridente:** treina com tridente (golpe ou arremesso). Efeito: **empurrão** que joga o alvo pra longe e um pouco pra cima. Boss não é empurrado.
- **Lanceiro:** treina com lança, de qualquer material. Efeito: **Sangramento**, 2 de dano por segundo por 4 s (8 no total), que atravessa a armadura. Se o alvo morrer sangrando, o abate é seu.
- **Marreteiro:** treina com maça. Efeito: **Atordoamento** de 3 s. Jogador fica travado no lugar (só olha em volta) e apanha sem a pausa normal entre golpes; mob fica quase parado. Boss é imune.
- **Crítico:** só treina com a **Adaga**, uma espada especial. O **Ferreiro** (oficinas da mina) transforma a espada da sua mão em Adaga, mantendo os encantamentos: R$ 500 (madeira/pedra), R$ 2.000 (ferro/ouro), R$ 8.000 (diamante) ou R$ 20.000 (netherite), e desfaz pelo mesmo preço. Uma Adaga de netherite encantada também cai do boss (4%) e sai no baú lendário dos tesouros. Golpes com ela treinam Crítico em vez de Espadas e não recebem o bônus de dano das Espadas. Efeito: **+40% de dano** no golpe que acerta a chance. A chance vale com **qualquer arma**, inclusive arco, e soma com o crítico do pulo.

**Como somam:** o dano a mais da skill entra depois da armadura e dos encantamentos. O Crítico vem por cima de tudo. Com prestígio, tudo isso cresce 10% por ✦ (Porradeiro 50 ✦5: +45% de dano e 15% de Lentidão).

**Treino:** o XP vem do dano causado, então arma forte treina mais rápido. Contra mob de spawner, o XP é um quarto do normal; a `/warp mobs` dá XP de mob cheio. Em PvP, os primeiros 250 XP contra a mesma pessoa no dia valem inteiros, até 500 valem metade, depois um quarto, com teto de 1.000 XP por vítima ao dia.
