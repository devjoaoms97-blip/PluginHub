// O que mexe no site: copiar, status ao vivo, brilho que segue o mouse, entrada ao rolar, contadores,
// faíscas no topo e as abas dos rankings. Quem pede menos movimento no sistema não vê as animações.
document.documentElement.classList.add('js');
const calmo = matchMedia('(prefers-reduced-motion: reduce)').matches;

// ---- copiar (IP e comandos)
document.addEventListener('click', async (e) => {
  const botao = e.target.closest('[data-copiar]');
  if (!botao) return;
  const texto = botao.querySelector('[data-copiar-texto]');
  let ok = true;
  try {
    await navigator.clipboard.writeText(botao.dataset.copiar);
  } catch {
    ok = false;
  }
  if (texto) {
    texto.textContent = ok ? 'Copiado!' : 'Selecione e copie';
    setTimeout(() => { texto.textContent = 'Copiar'; }, 2000);
  } else if (ok) {
    botao.classList.add('copiado');
    setTimeout(() => botao.classList.remove('copiado'), 1200);
  }
});

// ---- brilho que segue o mouse nos cards
document.addEventListener('pointermove', (e) => {
  const alvo = e.target.closest && e.target.closest('[data-brilho]');
  if (!alvo) return;
  const r = alvo.getBoundingClientRect();
  alvo.style.setProperty('--x', `${e.clientX - r.left}px`);
  alvo.style.setProperty('--y', `${e.clientY - r.top}px`);
});

// ---- contadores (sobem até o número quando aparecem)
function contar(el, ate) {
  if (calmo || !ate) { el.textContent = ate.toLocaleString('pt-BR'); return; }
  const inicio = performance.now();
  const duracao = Math.min(1600, 500 + ate * 2);
  const passo = (agora) => {
    const t = Math.min(1, (agora - inicio) / duracao);
    el.textContent = Math.round(ate * (1 - Math.pow(1 - t, 3))).toLocaleString('pt-BR');
    if (t < 1) requestAnimationFrame(passo);
  };
  requestAnimationFrame(passo);
}

// ---- entrada ao rolar
document.querySelectorAll('[data-atraso]').forEach((el) => el.style.setProperty('--atraso', el.dataset.atraso));
const revelar = document.querySelectorAll('.revelar');
const contadores = document.querySelectorAll('[data-contar]');
if ('IntersectionObserver' in window && !calmo) {
  const obs = new IntersectionObserver((entradas) => {
    for (const e of entradas) {
      if (!e.isIntersecting) continue;
      e.target.classList.add('visivel');
      e.target.querySelectorAll('[data-contar]').forEach((c) => contar(c, Number(c.dataset.contar)));
      obs.unobserve(e.target);
    }
  }, { rootMargin: '0px 0px -40px 0px', threshold: 0.08 });
  revelar.forEach((el) => obs.observe(el));
  contadores.forEach((c) => { if (!c.closest('.revelar')) contar(c, Number(c.dataset.contar)); else c.textContent = '0'; });
} else {
  revelar.forEach((el) => el.classList.add('visivel'));
}

// ---- status ao vivo (a cada 30 s)
const cartao = document.querySelector('[data-status]');
if (cartao) {
  const contador = document.querySelector('[data-status-online]');
  setInterval(async () => {
    if (document.hidden) return;
    try {
      const r = await fetch('/api/status', { cache: 'no-store' });
      if (!r.ok) return;
      const s = await r.json();
      cartao.innerHTML = s.html;
      if (contador) contador.textContent = s.quantos.toLocaleString('pt-BR');
    } catch {
      // sem rede: tenta na próxima
    }
  }, 30000);
}

// ---- faíscas subindo no topo da página inicial
const tela = document.querySelector('[data-faiscas]');
if (tela && !calmo) {
  const ctx = tela.getContext('2d');
  let largura = 0, altura = 0, faiscas = [];
  const medir = () => {
    const dpr = Math.min(2, devicePixelRatio || 1);
    largura = tela.clientWidth; altura = tela.clientHeight;
    tela.width = largura * dpr; tela.height = altura * dpr;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  };
  const nova = (embaixo) => ({
    x: Math.random() * largura,
    y: embaixo ? altura + Math.random() * 40 : Math.random() * altura,
    v: 0.25 + Math.random() * 0.8,
    t: 1 + Math.random() * 2.2,
    fase: Math.random() * Math.PI * 2,
    vida: 0.4 + Math.random() * 0.6,
    cor: Math.random() < 0.6 ? '242,107,29' : Math.random() < 0.5 ? '255,179,71' : '255,209,102',
  });
  medir();
  faiscas = Array.from({ length: Math.round(Math.min(70, largura / 18)) }, () => nova(false));
  addEventListener('resize', medir);
  let visivel = true;
  new IntersectionObserver(([e]) => { visivel = e.isIntersecting; }).observe(tela);
  const quadro = (agora) => {
    if (visivel && !document.hidden) {
      ctx.clearRect(0, 0, largura, altura);
      for (let i = 0; i < faiscas.length; i++) {
        const f = faiscas[i];
        f.y -= f.v;
        f.x += Math.sin(agora / 900 + f.fase) * 0.35;
        const alfa = Math.max(0, Math.min(1, f.y / altura)) * f.vida;
        if (f.y < -10 || alfa <= 0.01) { faiscas[i] = nova(true); continue; }
        ctx.fillStyle = `rgba(${f.cor},${alfa})`;
        ctx.shadowColor = `rgba(${f.cor},${alfa})`;
        ctx.shadowBlur = 8;
        ctx.fillRect(f.x, f.y, f.t, f.t);
      }
    }
    requestAnimationFrame(quadro);
  };
  requestAnimationFrame(quadro);
}

// ---- abas dos rankings (sem recarregar; o endereço acompanha)
const abas = document.querySelector('[data-abas]');
if (abas) {
  const mostrar = (id, foco) => {
    abas.querySelectorAll('[data-aba]').forEach((a) => {
      const sim = a.dataset.aba === id;
      a.setAttribute('aria-selected', sim);
      a.tabIndex = sim ? 0 : -1;
      if (sim && foco) a.focus();
    });
    document.querySelectorAll('[data-painel]').forEach((p) => {
      const sim = p.dataset.painel === id;
      p.hidden = !sim;
      if (sim) p.querySelectorAll('.revelar').forEach((el) => el.classList.add('visivel'));
    });
  };
  abas.addEventListener('click', (e) => {
    const a = e.target.closest('[data-aba]');
    if (!a) return;
    e.preventDefault();
    mostrar(a.dataset.aba);
    history.replaceState(null, '', a.href);
  });
  abas.addEventListener('keydown', (e) => {
    if (e.key !== 'ArrowRight' && e.key !== 'ArrowLeft') return;
    const lista = [...abas.querySelectorAll('[data-aba]')];
    const i = lista.indexOf(document.activeElement);
    if (i < 0) return;
    const prox = lista[(i + (e.key === 'ArrowRight' ? 1 : lista.length - 1)) % lista.length];
    mostrar(prox.dataset.aba, true);
    history.replaceState(null, '', prox.href);
  });
  // o seletor de skill dentro da aba Skills
  document.addEventListener('click', (e) => {
    const chip = e.target.closest('[data-skill]');
    if (!chip) return;
    e.preventDefault();
    document.querySelectorAll('[data-skill]').forEach((c) => c.setAttribute('aria-selected', c === chip));
    document.querySelectorAll('[data-sub-painel]').forEach((p) => {
      p.hidden = p.dataset.subPainel !== chip.dataset.skill;
      if (!p.hidden) p.querySelectorAll('.revelar').forEach((el) => el.classList.add('visivel'));
    });
    history.replaceState(null, '', chip.href);
  });
}

// ---- loja: pacote → nick + código do jogo → Mercado Pago
const compra = document.querySelector('[data-loja]');
if (compra) {
  const estado = { pacote: null, nick: '' };
  const passo = (n) => {
    compra.querySelectorAll('[data-etapa]').forEach((f) => { f.hidden = f.dataset.etapa !== String(n); });
    compra.querySelectorAll('[data-etapa-marca]').forEach((m) => {
      const k = Number(m.dataset.etapaMarca);
      m.toggleAttribute('aria-current', k === n);
      m.classList.toggle('feita', k < n);
    });
    const alvo = compra.querySelector(`[data-etapa="${n}"]`);
    if (alvo) alvo.querySelectorAll('.revelar').forEach((el) => el.classList.add('visivel'));
  };
  const msg = compra.querySelector('[data-mensagem]');
  const avisar = (texto, erro) => { msg.textContent = texto || ''; msg.classList.toggle('erro', Boolean(erro)); msg.classList.toggle('ok', Boolean(texto) && !erro); };
  const nickInput = compra.querySelector('#loja-nick');
  const codigoInput = compra.querySelector('#loja-codigo');
  const cabecaNick = compra.querySelector('[data-nick-cabeca]');
  const botaoCodigo = compra.querySelector('[data-enviar-codigo]');
  const botaoPagar = compra.querySelector('[data-pagar]');
  const resumo = compra.querySelector('[data-resumo]');
  const NICK = /^[A-Za-z0-9_]{2,16}$/;

  const postar = async (rota, corpo) => {
    try {
      const r = await fetch(rota, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(corpo) });
      return await r.json();
    } catch {
      return { ok: false, erro: 'Sem conexão. Tente de novo.' };
    }
  };

  compra.addEventListener('click', (e) => {
    const p = e.target.closest('[data-pacote]');
    if (!p) return;
    estado.pacote = p.dataset.pacote;
    compra.querySelectorAll('[data-pacote]').forEach((b) => b.setAttribute('aria-pressed', b === p));
    const nome = p.querySelector('.pacote-nome').textContent;
    const creditos = p.querySelector('.pacote-creditos').textContent;
    const preco = p.querySelector('.pacote-preco').textContent;
    resumo.hidden = false;
    resumo.querySelector('[data-resumo-texto]').textContent = `${nome} · ${creditos} · ${preco}`;
    setTimeout(() => { passo(2); nickInput.focus(); }, calmo ? 0 : 220);
  });
  compra.querySelector('[data-voltar]').addEventListener('click', () => passo(1));

  let tempoCabeca;
  nickInput.addEventListener('input', () => {
    clearTimeout(tempoCabeca);
    const n = nickInput.value.trim();
    tempoCabeca = setTimeout(() => { if (NICK.test(n)) cabecaNick.src = `https://mc-heads.net/avatar/${encodeURIComponent(n)}/48`; }, 400);
    if (n !== estado.nick) { compra.querySelector('[data-campo-codigo]').hidden = true; botaoPagar.disabled = true; avisar(''); }
  });

  botaoCodigo.addEventListener('click', async () => {
    const nick = nickInput.value.trim();
    if (!NICK.test(nick)) return avisar('Digite o seu nick do Minecraft (letras, números e _).', true);
    botaoCodigo.disabled = true;
    botaoCodigo.textContent = 'Mandando…';
    const r = await postar('/api/loja/codigo', { nick });
    botaoCodigo.disabled = false;
    botaoCodigo.textContent = 'Mandar de novo';
    if (!r.ok) return avisar(r.erro, true);
    estado.nick = nick;
    compra.querySelector('[data-campo-codigo]').hidden = false;
    avisar(`Código enviado no chat do jogo pra ${r.nick}. Digite ele aqui embaixo.`);
    codigoInput.value = '';
    codigoInput.focus();
  });

  codigoInput.addEventListener('input', () => {
    codigoInput.value = codigoInput.value.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 6);
    botaoPagar.disabled = codigoInput.value.length !== 6;
  });

  compra.addEventListener('submit', async (e) => {
    e.preventDefault();
    if (botaoPagar.disabled) return;
    botaoPagar.disabled = true;
    botaoPagar.textContent = 'Conferindo…';
    const r = await postar('/api/loja/pedido', { nick: estado.nick, codigo: codigoInput.value, pacote: estado.pacote });
    botaoPagar.textContent = 'Ir pro pagamento';
    if (!r.ok) { botaoPagar.disabled = false; return avisar(r.erro, true); }
    passo(3);
    compra.querySelector('[data-link-pagamento]').href = r.link;
    location.href = r.link;
  });
}

// ---- página do pedido: se atualiza sozinha até entregar ou vencer
const pedido = document.querySelector('[data-pedido]');
if (pedido) {
  const FINAIS = ['entregue', 'vencido', 'problema'];
  const atualizar = async () => {
    if (FINAIS.includes(pedido.dataset.pedidoEstado)) return;
    try {
      const r = await fetch(`/api/loja/pedido/${encodeURIComponent(pedido.dataset.pedido)}`, { cache: 'no-store' });
      if (r.ok) {
        const s = await r.json();
        if (s.estado !== pedido.dataset.pedidoEstado) {
          pedido.innerHTML = s.html;
          pedido.dataset.pedidoEstado = s.estado;
          pedido.classList.add('mudou');
          setTimeout(() => pedido.classList.remove('mudou'), 900);
        }
      }
    } catch { /* tenta de novo */ }
    setTimeout(atualizar, 5000);
  };
  setTimeout(atualizar, 3000);
}
