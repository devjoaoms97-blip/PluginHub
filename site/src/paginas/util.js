// Ajudantes das páginas: escapar texto, formatar números e datas, cabeças e cores do Minecraft.

/** Escapa texto pra entrar no HTML. Tudo que vem do jogo (nick, nome de clã) passa por aqui. */
const esc = (v) => String(v ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

const numero = (v) => Number(v || 0).toLocaleString('pt-BR');
const reais = (v) => Number(v || 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL', maximumFractionDigits: 0 });
/** R$ 1,2 mi, R$ 35 mil... pra caber nos cards. */
function reaisCurto(v) {
  const n = Number(v || 0);
  if (n >= 1e9) return `R$ ${(n / 1e9).toLocaleString('pt-BR', { maximumFractionDigits: 1 })} bi`;
  if (n >= 1e6) return `R$ ${(n / 1e6).toLocaleString('pt-BR', { maximumFractionDigits: 1 })} mi`;
  if (n >= 1e4) return `R$ ${(n / 1e3).toLocaleString('pt-BR', { maximumFractionDigits: 0 })} mil`;
  return reais(n);
}

/** "agora", "há 5 min", "há 3 dias"... */
function tempoAtras(ms) {
  if (!ms) return 'nunca';
  const s = Math.max(0, (Date.now() - ms) / 1000);
  if (s < 90) return 'agora';
  const m = s / 60, h = m / 60, d = h / 24;
  if (m < 60) return `há ${Math.round(m)} min`;
  if (h < 24) return `há ${Math.round(h)} h`;
  if (d < 30) return `há ${Math.round(d)} dia${Math.round(d) === 1 ? '' : 's'}`;
  if (d < 365) return `há ${Math.round(d / 30)} ${Math.round(d / 30) === 1 ? 'mês' : 'meses'}`;
  return `há ${Math.round(d / 365)} ano${Math.round(d / 365) === 1 ? '' : 's'}`;
}

const data = (ms) => (ms ? new Date(ms).toLocaleDateString('pt-BR', { day: '2-digit', month: 'short', year: 'numeric' }) : '—');

/** A cabeça do jogador pelo nick (mc-heads.net). */
const cabeca = (nick, tamanho = 32) =>
  `<img class="cabeca" src="https://mc-heads.net/avatar/${encodeURIComponent(nick)}/${tamanho * 2}" alt="" width="${tamanho}" height="${tamanho}" loading="lazy">`;

/** "§a" → a classe da cor do Minecraft (mc-a). Sem cor, a padrão. */
const corMc = (cor) => {
  const m = /§([0-9a-f])/i.exec(cor || '');
  return m ? `mc-${m[1].toLowerCase()}` : '';
};

const linkJogador = (nick) => `/jogador/${encodeURIComponent(nick)}`;

const VIP = { BRONZE: 'Bronze', PRATA: 'Prata', OURO: 'Ouro', DIAMANTE: 'Diamante' };

module.exports = { esc, numero, reais, reaisCurto, tempoAtras, data, cabeca, corMc, linkJogador, VIP };
