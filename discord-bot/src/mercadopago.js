// Cliente mínimo da API do Mercado Pago: cria o link de pagamento (Checkout Pro, com Pix e cartão)
// e procura os pagamentos de um pedido pela external_reference.
const config = require('./config');

const API = 'https://api.mercadopago.com';

async function chamar(metodo, rota, corpo) {
  const resposta = await fetch(API + rota, {
    method: metodo,
    headers: { Authorization: `Bearer ${config.mercadoPago.token}`, 'Content-Type': 'application/json' },
    body: corpo === undefined ? undefined : JSON.stringify(corpo),
    signal: AbortSignal.timeout(15000),
  });
  const dados = await resposta.json().catch(() => ({}));
  if (!resposta.ok) throw new Error(`Mercado Pago ${resposta.status}: ${dados.message || 'erro'}`);
  return dados;
}

/** Data no formato que o Mercado Pago aceita (com fuso). */
const data = (ms) => new Date(ms).toISOString().replace('Z', '+00:00');

/** Link de pagamento de um item só. Retorna { id, link }. */
async function criarLink({ referencia, titulo, preco, expiraEm, voltarPara }) {
  const p = await chamar('POST', '/checkout/preferences', {
    items: [{ id: referencia, title: titulo, quantity: 1, unit_price: preco, currency_id: 'BRL' }],
    external_reference: referencia,
    statement_descriptor: 'BRASAMC',
    back_urls: { success: voltarPara, pending: voltarPara, failure: voltarPara },
    auto_return: 'approved',
    expires: true,
    expiration_date_from: data(Date.now() - 60_000),
    expiration_date_to: data(expiraEm),
  });
  return { id: p.id, link: p.init_point };
}

/** Pagamentos de um pedido (aprovados, pendentes, recusados...). */
async function pagamentosDe(referencia) {
  const r = await chamar('GET', `/v1/payments/search?external_reference=${encodeURIComponent(referencia)}&sort=date_created&criteria=asc`);
  return r.results || [];
}

module.exports = { criarLink, pagamentosDe };
