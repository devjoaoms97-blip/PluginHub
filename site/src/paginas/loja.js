// /loja: os pacotes e a compra em 3 passos (pacote, nick + código do jogo, pagar). O site.js cuida dos
// passos; sem JS, a página explica que precisa dele. /loja/pedido/<id>: o pedido, que se atualiza sozinho.
const config = require('../config');
const loja = require('../loja');
const { layout } = require('./layout');
const { esc, numero, reais, cabeca } = require('./util');

function paginaLoja() {
  const pacotes = loja.PACOTES.map((p) => `<button type="button" class="pacote${p.destaque ? ' pacote-destaque' : ''} revelar" data-pacote="${p.id}" data-brilho aria-pressed="false">
      ${p.destaque ? '<span class="pacote-fita">Mais escolhido</span>' : ''}
      <span class="pacote-emoji" aria-hidden="true">${p.emoji}</span>
      <span class="pacote-nome">${p.nome}</span>
      <strong class="pacote-creditos">${numero(p.creditos)}<small> créditos</small></strong>
      ${p.bonus ? `<span class="pacote-bonus">+${p.bonus}% de bônus</span>` : '<span class="pacote-bonus pacote-bonus-vazio">sem bônus</span>'}
      <span class="pacote-preco">${reais(p.preco)}</span>
    </button>`).join('');

  const corpo = `
<section class="cabecalho-pagina">
  <div class="largura">
    <h1 class="surgir">Loja de créditos</h1>
    <p class="chamada surgir">Créditos compram <strong>VIP</strong> e <strong>cosméticos</strong> no jogo, com <code>/creditos</code>. Pague com Pix ou cartão: os créditos caem sozinhos na sua conta.</p>
  </div>
</section>
<section class="largura bloco">
  ${loja.aberta() ? `
  <noscript><p class="aviso">A compra precisa do JavaScript ligado no navegador.</p></noscript>
  <form class="compra" data-loja novalidate>
    <ol class="etapas" aria-label="Passos da compra">
      <li class="etapa" data-etapa-marca="1" aria-current="step"><span>1</span> Pacote</li>
      <li class="etapa" data-etapa-marca="2"><span>2</span> Sua conta</li>
      <li class="etapa" data-etapa-marca="3"><span>3</span> Pagamento</li>
    </ol>

    <fieldset class="passo" data-etapa="1">
      <legend>Escolha o pacote</legend>
      <div class="pacotes">${pacotes}</div>
    </fieldset>

    <fieldset class="passo" data-etapa="2" hidden>
      <legend>Pra qual conta?</legend>
      <p class="fraco">Entre no servidor com a conta que vai receber os créditos. Vamos mandar um código no chat do jogo pra confirmar que é você.</p>
      <div class="campo-linha">
        <div class="campo">
          <label for="loja-nick">Nick no Minecraft</label>
          <div class="campo-com-cabeca"><img data-nick-cabeca src="https://mc-heads.net/avatar/MHF_Steve/48" alt="" width="24" height="24"><input id="loja-nick" name="nick" autocomplete="off" maxlength="16" placeholder="SeuNick" required></div>
        </div>
        <button type="button" class="botao botao-secundario" data-enviar-codigo>Mandar código</button>
      </div>
      <div class="campo" data-campo-codigo hidden>
        <label for="loja-codigo">Código que chegou no chat do jogo</label>
        <input id="loja-codigo" name="codigo" autocomplete="one-time-code" maxlength="8" placeholder="ABC123" class="campo-codigo">
        <p class="fraco pequeno">Não chegou? Confira se você está online e espere 1 minuto pra pedir outro.</p>
      </div>
      <p class="mensagem" data-mensagem role="status" aria-live="polite"></p>
      <div class="acoes">
        <button type="button" class="botao botao-fantasma" data-voltar>← Trocar pacote</button>
        <button type="submit" class="botao botao-primario" data-pagar disabled>Ir pro pagamento</button>
      </div>
    </fieldset>

    <fieldset class="passo" data-etapa="3" hidden>
      <legend>Indo pro Mercado Pago…</legend>
      <p>Abrindo o pagamento. Se não abrir, <a data-link-pagamento href="#">clique aqui</a>.</p>
    </fieldset>

    <aside class="resumo" data-resumo hidden>
      <span class="fraco">Seu pedido</span>
      <strong data-resumo-texto></strong>
    </aside>
  </form>` : `<div class="aviso aviso-grande"><h2>A loja está fechada agora</h2><p>Volte daqui a pouco${config.discord ? `, ou acompanhe os avisos no <a href="${esc(config.discord)}" target="_blank" rel="noopener">Discord</a>` : ''}.</p></div>`}
</section>
<section class="largura bloco">
  <h2 class="revelar">Como funciona</h2>
  <div class="cartoes">
    <div class="cartao revelar"><div class="cartao-icone" aria-hidden="true">🎮</div><h3>No jogo, com /creditos</h3><p>Troque os créditos por VIP (pra você ou de presente, num código que dá até pra vender no jogo) e por cosméticos: rastros, cor do nome e efeitos de abate.</p></div>
    <div class="cartao revelar"><div class="cartao-icone" aria-hidden="true">⚡</div><h3>Entrega automática</h3><p>Pagou, caiu: o Pix leva até 1 minuto. O cartão pode levar mais, se o banco analisar. Se o servidor estiver desligado, chega quando ele voltar.</p></div>
    <div class="cartao revelar"><div class="cartao-icone" aria-hidden="true">🛡️</div><h3>Sem sorteio, sem item do jogo</h3><p>A loja não vende itens, coins nem caixas: só créditos, que viram VIP e visual. Quem contribui ganha a tag de apoiador.</p></div>
    <div class="cartao revelar"><div class="cartao-icone" aria-hidden="true">💬</div><h3>Problema com o pagamento?</h3><p>Abra um ticket de Pagamento no ${config.discord ? `<a href="${esc(config.discord)}" target="_blank" rel="noopener">Discord</a>` : 'Discord'} com o número do pedido.</p></div>
  </div>
</section>`;
  return layout({ titulo: 'Loja', descricao: 'Compre créditos do BrasaMC com Pix ou cartão: VIP e cosméticos, com entrega automática.', ativo: '/loja', corpo });
}

const ESTADOS = {
  aguardando: { icone: '⏳', titulo: 'Aguardando o pagamento', texto: 'Assim que o Mercado Pago aprovar, os créditos caem sozinhos. Esta página se atualiza.' },
  analise: { icone: '🔎', titulo: 'Pagamento em análise', texto: 'O banco está analisando. Quando aprovar, os créditos caem sozinhos (pode levar algumas horas).' },
  pago: { icone: '💸', titulo: 'Pago! Entregando…', texto: 'O pagamento foi aprovado. Os créditos chegam assim que o servidor confirmar (se ele estiver desligado, quando voltar).' },
  entregue: { icone: '✅', titulo: 'Créditos entregues!', texto: 'Use no jogo com /creditos. Obrigado pelo apoio ao BrasaMC! 🔥' },
  vencido: { icone: '⌛', titulo: 'O link venceu', texto: 'Ninguém pagou este pedido a tempo. Faça um pedido novo na loja.' },
  problema: { icone: '⚠️', titulo: 'Pagamento com valor diferente', texto: 'Nada foi entregue. Abra um ticket de Pagamento no Discord com o número do pedido.' },
};

/** O miolo do pedido (o site.js troca a cada 5 s com /api/loja/pedido/<id>). */
function conteudoPedido(s) {
  const e = ESTADOS[s.estado];
  return `<div class="pedido-estado pedido-${s.estado}">
    <span class="pedido-icone" aria-hidden="true">${e.icone}</span>
    <h2>${e.titulo}</h2>
    <p>${e.texto}</p>
    ${s.estado === 'aguardando' && s.link ? `<a class="botao botao-primario" href="${esc(s.link)}">Pagar ${reais(s.preco)}</a>` : ''}
    ${s.estado === 'vencido' ? '<a class="botao botao-primario" href="/loja">Voltar pra loja</a>' : ''}
  </div>`;
}

function paginaPedido(id) {
  const s = loja.situacao(id);
  if (!s) return null;
  const corpo = `
<section class="cabecalho-pagina">
  <div class="largura">
    <a class="voltar" href="/loja">← Loja</a>
    <h1 class="surgir">Pedido #${numero(s.numero)}</h1>
  </div>
</section>
<section class="largura bloco pedido">
  <div class="pedido-cartao" data-pedido="${esc(id)}" data-pedido-estado="${s.estado}">${conteudoPedido(s)}</div>
  <dl class="pedido-dados">
    <div><dt>Conta</dt><dd>${cabeca(s.nick, 22)} ${esc(s.nick)}</dd></div>
    <div><dt>Pacote</dt><dd>${s.pacote ? `${s.pacote.emoji} ${esc(s.pacote.nome)} · ` : ''}${numero(s.creditos)} créditos</dd></div>
    <div><dt>Valor</dt><dd>${reais(s.preco)}</dd></div>
  </dl>
  <p class="fraco pequeno">Guarde o endereço desta página: dá pra voltar nela pra ver o pedido.</p>
</section>`;
  return layout({ titulo: `Pedido #${s.numero}`, ativo: '/loja', corpo });
}

module.exports = { paginaLoja, paginaPedido, conteudoPedido };
