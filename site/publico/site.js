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
