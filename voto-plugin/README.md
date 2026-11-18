# VotoPlugin

Voto com recompensa (2026-11-18). Os sites de lista de servidores avisam cada voto pelo **Votifier** (o plugin `Votifier` 3.3.6, o fork [azuvotifier](https://github.com/azurejelly/azuvotifier) do NuVotifier, que está no Modrinth). O VotoPlugin escuta o evento dele por reflexão, sem dependência de compilação.

- **Cada voto:** uma **Chave de Voto** (CaixasPlugin). Quem votou offline recebe quando entrar.
- **Sequência:** o primeiro voto de cada dia paga o bônus do dia da sequência de dias seguidos, em % do preço do próximo rank (0,25% nos dias 1 e 2 até 1% no 7º, mínimo R$ 200, como a `/diaria`). No 7º dia vem também uma chave extra, e a sequência recomeça. Faltou um dia, volta pro 1.
- **Festa do Voto:** a cada 50 votos no servidor, todo mundo online ganha uma Chave de Voto. O chat avisa quando faltam 25, 10 e 5.
- **Ranking do mês:** no começo do mês, os 3 que mais votaram no mês anterior ganham 3, 2 e 1 **Chave do Boss**, com anúncio.
- **Lembrete:** quem entra sem ter votado no dia recebe um aviso com o botão [VOTAR].
- Cada voto é anunciado no chat e no Discord (categoria `votos`). O bônus entra no extrato do Bancário.

O dia é o do fuso `America/Sao_Paulo`. Voto de nick que nunca entrou no servidor é ignorado.

## Comandos

| Comando | Quem | O que faz |
|---|---|---|
| `/votar` (`/vote`, `/votos`) | todos | Os links pra votar (clicáveis), a sua sequência, os seus votos no mês, quanto falta pra festa e o top 3 do mês |
| `/votar top` | todos | Os 10 que mais votaram no mês |
| `/votar admin simular <jogador> [site]` | staff | Um voto, sem passar pelo Votifier |
| `/votar admin festa [n]` | staff | Mostra (ou muda) o contador da festa |
| `/votar admin fimdomes` | staff | Paga o ranking do mês agora e zera |
| `/votar admin sequencia <jogador> <dia>` | staff | Põe a sequência (último voto ontem), pra teste |
| `/votar admin reload` | staff | Recarrega o config |

Pra testar o Votifier: `/testvote username=<nick> serviceName=<site>` (os argumentos vão com nome).

## Cadastrar o servidor nos sites

1. O Votifier abre a porta **8192** (TCP; `plugins/Votifier/config.yml`). Ela precisa estar liberada no firewall da VPS.
2. Em cada site de lista: o IP do servidor, a porta 8192 e a **chave pública** de `plugins/Votifier/rsa/public.key` (o conteúdo do arquivo). Sites mais novos usam o token do protocolo v2, em `plugins/Votifier/config.yml` (`tokens`).
3. O link de votar de cada site em `sites:` no config do VotoPlugin (`{nome: "...", url: "..."}`) e `/votar admin reload`.

Dados em `votos.yml` (por jogador: o último dia, a sequência, os votos do mês e o total; o contador da festa e o mês).

Teste: `node tools/bots/teste-voto.js`.
