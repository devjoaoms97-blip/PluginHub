# 💰 Dinheiro, loja e Pix

**Loja Oficial** · `/warp loja`
Há sete vendedores: Minerador, Construtor, Lenhador, Fazendeiro, Caçador, Jardineiro e Alquimista. Clique com o botão direito no vendedor para abrir a categoria. No menu, clique esquerdo vende 1 item; **Shift + esquerdo** vende os aceitos do inventário; clique direito compra 1; **Shift + direito** compra até 64.

O preço de compra do servidor é fixo. O preço de venda é dinâmico: cada venda reduz o preço daquele item, que se recupera com o tempo. Bônus de rank e VIP e o imposto da loja entram no valor final, mostrado na venda. Em eventos de **📈 ALTA**, alguns itens pagam mais; acompanhe {#eventos}. Máquinas e spawners têm balcões próprios (`/maquinas loja` e `/spawners loja`).

**Pix entre jogadores**
`/pix pagar <jogador> <valor> [motivo]` transfere na hora. Para cobrar, use `/pix receber <jogador> <valor> [motivo]`; a outra pessoa paga com `/pix aceitar <jogador>`. A cobrança expira em 5 minutos. `/pix qrcode <valor>` publica um botão para alguém te pagar.

`/pix agendar <jogador> <valor> <dias>` paga a primeira parcela na hora e repete a cada tantos dias; consulte ou cancele com `/pix agendamentos`. No servidor de teste, a taxa é **1%** e sai do valor recebido; o pagador perde o valor cheio. O limite diário está desativado atualmente; `/pix limite` mostra o teto caso ele seja ligado. `/pix historico` orienta a procurar o **Extrato no Bancário**.

**Bancário e Mine-sena**
No Bancário, veja o **Extrato** e deposite cheques. Para emitir um, compre uma folha e use `/cheque <valor> [jogador]`; um cheque nominal só pode ser depositado pela pessoa indicada.

No botão **Mine-sena**, escolha um número de 1 a 100. Cada bilhete custa R$ 500, vale para o próximo sorteio e fica registrado no seu nome; o limite é 20 bilhetes por sorteio. O resultado sai diariamente às 18h. O pote é dividido entre os bilhetes vencedores; sem ganhador, acumula. `/minesena` mostra o pote e seus números.

**Barracas: comércio entre jogadores**
Há 32 barracas no spawn, alugadas por **6 horas**. As normais são alugadas por ordem de chegada; as especiais (★) vão a leilão e não renovam. Veja preços e disponibilidade com `/barraca lista` e `/barraca info <id>`.

Para anunciar, segure o item e use `/barraca vender <quantidade> <preço>`: o preço é do lote inteiro, e o estoque inicial vai da sua mochila para a loja. Para a barraca comprar esse item dos outros, use `/barraca comprar <quantidade> <preço>`. Os clientes negociam clicando no NPC, mesmo quando você está offline. Barraca vencida? Retire o estoque com `/barraca retirar` em até 7 dias.

Use o Pix, cheques ou barracas para negociar com registro. Confira nick, item e valor antes de confirmar uma troca.
