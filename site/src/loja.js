// Loja de créditos do site. O comprador digita o nick, recebe um código no chat do jogo (DiscordPlugin,
// POST /loja/codigo), digita o código aqui (POST /loja/confirmar) e escolhe o pacote: o site cria o
// pedido e o link do Mercado Pago (Pix ou cartão, vale 1 hora). A cada 20 s, confere os pagamentos dos
// pedidos abertos; aprovado e com o valor certo, entrega pelo POST /entregar do DiscordPlugin, o mesmo
// da loja do Discord, que anota o ID do pagamento e nunca entrega o mesmo duas vezes.
//
// Os pedidos ficam em <DISCORD_PASTA>/site-<servidor>.json. Cada um tem um id aleatório, que é o
// endereço da página dele (/loja/pedido/<id>), e a referência no Mercado Pago é site-<servidor>-<número>.
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const config = require('./config');
const dados = require('./dados');

// Os mesmos pacotes da loja do Discord (discord-bot/src/loja.js): R$ 1 = 10 créditos, os maiores com bônus.
const PACOTES = [
  { id: 'p100', nome: 'Pequeno', preco: 10, creditos: 100, emoji: '💵' },
  { id: 'p260', nome: 'Médio', preco: 25, creditos: 260, bonus: 4, emoji: '💰' },
  { id: 'p550', nome: 'Grande', preco: 50, creditos: 550, bonus: 10, emoji: '💎', destaque: true },
  { id: 'p1150', nome: 'Enorme', preco: 100, creditos: 1150, bonus: 15, emoji: '👑' },
];

const CONFERIR_MS = 20_000;
const LINK_MS = 60 * 60_000;
const ESPERA_PENDENTE_MS = 48 * 60 * 60_000; // Pix ou boleto em análise: continua conferindo até 48 h
const GUARDAR_MS = 30 * 24 * 60 * 60_000;

const ARQUIVO = path.join(config.pasta, `site-${config.servidor}.json`);
const memoria = fs.existsSync(ARQUIVO) ? JSON.parse(fs.readFileSync(ARQUIVO, 'utf8')) : {};
memoria.contador ||= 0;
memoria.pedidos ||= {};
const salvar = () => fs.writeFileSync(ARQUIVO, JSON.stringify(memoria, null, 2));

const aberta = () => Boolean(config.mercadoPago.token);

// ---------------------------------------------------------------- Mercado Pago

async function mp(metodo, rota, corpo) {
  const resposta = await fetch(config.mercadoPago.api + rota, {
    method: metodo,
    headers: { Authorization: `Bearer ${config.mercadoPago.token}`, 'Content-Type': 'application/json' },
    body: corpo === undefined ? undefined : JSON.stringify(corpo),
    signal: AbortSignal.timeout(15000),
  });
  const r = await resposta.json().catch(() => ({}));
  if (!resposta.ok) throw new Error(`Mercado Pago ${resposta.status}: ${r.message || 'erro'}`);
  return r;
}

const dataMp = (ms) => new Date(ms).toISOString().replace('Z', '+00:00');

// ---------------------------------------------------------------- limite por IP (o código vai pro chat de alguém)

const tentativas = new Map();
/** true se o IP já passou de `max` pedidos dessa ação nos últimos `janelaMs`. */
function limitado(ip, acao, max, janelaMs) {
  const chave = `${acao}:${ip}`;
  const agora = Date.now();
  const lista = (tentativas.get(chave) || []).filter((t) => agora - t < janelaMs);
  lista.push(agora);
  tentativas.set(chave, lista);
  return lista.length > max;
}
setInterval(() => {
  const agora = Date.now();
  for (const [k, v] of tentativas) if (!v.some((t) => agora - t < 30 * 60_000)) tentativas.delete(k);
}, 5 * 60_000).unref();

// ---------------------------------------------------------------- o que a página da loja chama

const NICK = /^[A-Za-z0-9_]{2,16}$/;

/** POST /api/loja/codigo {nick}: manda o código pro jogador no jogo. */
async function pedirCodigo(ip, corpo) {
  if (!aberta()) return { ok: false, erro: 'A loja está fechada agora. Volte daqui a pouco.' };
  const nick = String(corpo.nick || '').trim();
  if (!NICK.test(nick)) return { ok: false, erro: 'Digite um nick válido (letras, números e _).' };
  if (limitado(ip, 'codigo', 5, 10 * 60_000)) return { ok: false, erro: 'Muitos pedidos de código. Espere uns minutos.' };
  try {
    const r = await dados.postar('/loja/codigo', { nick });
    return r.ok ? { ok: true, nick: r.nick } : { ok: false, erro: r.erro || 'Não deu pra mandar o código.' };
  } catch (e) {
    return { ok: false, erro: e.message };
  }
}

/** POST /api/loja/pedido {nick, codigo, pacote}: confere o código e cria o pedido com o link de pagamento. */
async function criarPedido(ip, corpo) {
  if (!aberta()) return { ok: false, erro: 'A loja está fechada agora. Volte daqui a pouco.' };
  const pacote = PACOTES.find((p) => p.id === corpo.pacote);
  if (!pacote) return { ok: false, erro: 'Escolha um pacote.' };
  const nick = String(corpo.nick || '').trim();
  const codigo = String(corpo.codigo || '').trim();
  if (!NICK.test(nick) || !/^[A-Za-z0-9 -]{4,12}$/.test(codigo)) return { ok: false, erro: 'Confira o nick e o código.' };
  if (limitado(ip, 'pedido', 15, 10 * 60_000)) return { ok: false, erro: 'Muitas tentativas. Espere uns minutos.' };
  let conta;
  try {
    conta = await dados.postar('/loja/confirmar', { nick, codigo });
  } catch (e) {
    return { ok: false, erro: e.message };
  }
  if (!conta.ok) return { ok: false, erro: conta.erro || 'Código errado.' };

  const numero = ++memoria.contador;
  const id = crypto.randomBytes(12).toString('base64url');
  const referencia = `site-${config.servidor}-${numero}`;
  const expira = Date.now() + LINK_MS;
  const voltar = `${config.url}/loja/pedido/${id}`;
  let preferencia;
  try {
    preferencia = await mp('POST', '/checkout/preferences', {
      items: [{ id: referencia, title: `BrasaMC · ${pacote.creditos.toLocaleString('pt-BR')} créditos (${conta.nick})`, quantity: 1, unit_price: pacote.preco, currency_id: 'BRL' }],
      external_reference: referencia,
      statement_descriptor: 'BRASAMC',
      back_urls: { success: voltar, pending: voltar, failure: voltar },
      // o Mercado Pago só volta sozinho pra endereço https (o do PC de teste é http)
      ...(config.url.startsWith('https://') ? { auto_return: 'approved' } : {}),
      expires: true,
      expiration_date_from: dataMp(Date.now() - 60_000),
      expiration_date_to: dataMp(expira),
    });
  } catch (e) {
    memoria.contador--;
    console.warn('[loja]', e.message);
    return { ok: false, erro: 'O Mercado Pago não respondeu. Tente de novo.' };
  }
  memoria.pedidos[id] = {
    numero, referencia, uuid: conta.uuid, nick: conta.nick, pacote: pacote.id, preco: pacote.preco, creditos: pacote.creditos,
    link: preferencia.init_point, criado: Date.now(), expira, estado: 'aguardando', pagamentos: {},
  };
  salvar();
  console.log(`[loja] pedido ${referencia} (${id}): ${conta.nick}, ${pacote.creditos} créditos, R$ ${pacote.preco}`);
  return { ok: true, pedido: id, link: preferencia.init_point };
}

/** O que a página do pedido mostra. */
function situacao(id) {
  const p = memoria.pedidos[id];
  if (!p) return null;
  const pagamentos = Object.values(p.pagamentos);
  let estado = 'aguardando';
  if (pagamentos.some((x) => x.estado === 'entregue')) estado = 'entregue';
  else if (pagamentos.some((x) => x.estado === 'pago')) estado = 'pago';
  else if (pagamentos.some((x) => x.estado === 'divergente')) estado = 'problema';
  else if (p.emAnalise) estado = 'analise';
  else if (p.estado === 'fechado' || Date.now() > p.expira) estado = 'vencido';
  const pacote = PACOTES.find((x) => x.id === p.pacote);
  return { numero: p.numero, nick: p.nick, creditos: p.creditos, preco: p.preco, pacote, link: p.link, expira: p.expira, estado };
}

// ---------------------------------------------------------------- conferir e entregar (a cada 20 s)

async function conferir(id, p) {
  const r = await mp('GET', `/v1/payments/search?external_reference=${encodeURIComponent(p.referencia)}&sort=date_created&criteria=asc`);
  const pagamentos = r.results || [];
  for (const pg of pagamentos) {
    const chave = String(pg.id);
    if (pg.status !== 'approved' || p.pagamentos[chave]) continue;
    const certo = pg.currency_id === 'BRL' && pg.transaction_amount + 0.001 >= p.preco;
    p.pagamentos[chave] = { estado: certo ? 'pago' : 'divergente', metodo: pg.payment_type_id, valor: pg.transaction_amount, quando: Date.now() };
    salvar();
    console.log(`[loja] ${p.referencia}: pagamento ${chave} aprovado (${pg.payment_type_id}, ${pg.transaction_amount})${certo ? '' : ' COM VALOR DIFERENTE: nada entregue'}`);
  }
  p.emAnalise = pagamentos.some((x) => ['pending', 'in_process', 'authorized'].includes(x.status));
  if ((Date.now() > p.expira + 10 * 60_000 && !p.emAnalise) || Date.now() - p.criado > ESPERA_PENDENTE_MS) {
    p.estado = 'fechado';
    salvar();
  }
}

async function entregar(p, chave) {
  let r;
  try {
    r = await dados.postar('/entregar', { pagamento: chave, pedido: `site #${p.numero}`, uuid: p.uuid, creditos: p.creditos });
  } catch (e) {
    if (!e.offline) console.warn(`[loja] entrega ${p.referencia}:`, e.message);
    return; // servidor desligado: tenta na próxima volta
  }
  if (!r.ok) {
    if (!p.pagamentos[chave].avisado) {
      p.pagamentos[chave].avisado = true;
      salvar();
      console.warn(`[loja] ${p.referencia}: entrega falhou (${r.erro}); continua tentando`);
    }
    return;
  }
  p.pagamentos[chave].estado = 'entregue';
  salvar();
  console.log(`[loja] ${p.referencia}: ${p.creditos} créditos entregues a ${r.nick}${r.repetida ? ' (já tinha sido)' : ''}`);
}

let rodando = false;
async function rodada() {
  if (rodando) return;
  rodando = true;
  try {
    for (const [id, p] of Object.entries(memoria.pedidos)) {
      if (p.estado === 'aguardando') await conferir(id, p).catch((e) => console.warn(`[loja] ${p.referencia}:`, e.message));
      for (const [chave, pg] of Object.entries(p.pagamentos)) if (pg.estado === 'pago') await entregar(p, chave);
      const pendente = Object.values(p.pagamentos).some((x) => x.estado === 'pago');
      const vazio = !Object.keys(p.pagamentos).length;
      if (p.estado === 'fechado' && !pendente && (vazio || Date.now() - p.criado > GUARDAR_MS)) {
        delete memoria.pedidos[id];
        salvar();
      }
    }
  } finally {
    rodando = false;
  }
}

function iniciar() {
  if (!aberta()) return console.warn('[loja] sem MP_ACCESS_TOKEN: a loja do site fica fechada');
  setInterval(() => rodada().catch((e) => console.warn('[loja]', e.message)), Number(process.env.LOJA_CONFERIR_MS || CONFERIR_MS)).unref();
}

module.exports = { PACOTES, aberta, pedirCodigo, criarPedido, situacao, iniciar, rodada };
