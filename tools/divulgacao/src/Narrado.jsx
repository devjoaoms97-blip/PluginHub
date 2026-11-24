import { AbsoluteFill, Audio, Sequence, interpolate, spring, staticFile, useCurrentFrame, useVideoConfig } from 'remotion';
import { COR, FONTE } from './marca';
import { Fagulhas, Final, limita } from './Teaser';
import { Clipe } from './Reel';

// Reel narrado: o roteiro (narracao/<id>.json) diz o vídeo de fundo e o título de cada fala; os tempos
// (narracao/<id>-tempos.json, do narracao/gerar.py) dizem quando cada fala e cada palavra começa.
// A legenda mostra 1 a 3 palavras por vez, com a palavra falada em amarelo.

const FPS = 30;
export const FINAL_NARRADO = 90;
export const duracaoNarrado = (tempos) => Math.ceil(tempos.duracao * FPS) + FINAL_NARRADO;

/** Junta as palavras em blocos curtos (até 3, quebrando em pontuação), pra ler rápido no celular. */
function blocos(palavras) {
  const lista = [];
  let atual = [];
  for (const p of palavras) {
    atual.push(p);
    if (atual.length === 3 || /[.,!?:]$/.test(p.texto)) { lista.push(atual); atual = []; }
  }
  if (atual.length) lista.push(atual);
  return lista;
}

const Legenda = ({ palavras, inicioFala }) => {
  const f = useCurrentFrame();
  const { fps } = useVideoConfig();
  const t = inicioFala + f / fps;
  const bloco = blocos(palavras).find((b, i, todos) => t >= b[0].ini - 0.05 && t < (todos[i + 1] ? todos[i + 1][0].ini - 0.05 : Infinity));
  if (!bloco) return null;
  const desde = Math.round((bloco[0].ini - inicioFala) * fps);
  const s = spring({ frame: f - desde, fps, config: { damping: 12, stiffness: 220 } });
  return (
    <AbsoluteFill style={{ top: 1180, alignItems: 'center' }}>
      <div style={{
        fontFamily: FONTE, fontWeight: 900, fontSize: 96, lineHeight: 1.1, textAlign: 'center', padding: '0 60px',
        textTransform: 'uppercase', letterSpacing: '-0.01em', transform: `scale(${0.7 + 0.3 * s})`,
      }}>
        {bloco.map((p, i) => (
          <span key={i} style={{
            color: t >= p.ini && t < p.fim + 0.15 ? COR.amarelo : COR.branco,
            textShadow: `0 7px 0 ${COR.contorno}, 0 0 30px rgba(0,0,0,.9), 0 0 6px #000`,
          }}>{p.texto.replace(/[.,!?:]$/, '')}{i < bloco.length - 1 ? ' ' : ''}</span>
        ))}
      </div>
    </AbsoluteFill>
  );
};

const Titulo = ({ texto }) => {
  const f = useCurrentFrame();
  const entra = interpolate(f, [0, 8], [-40, 0], limita);
  return (
    <AbsoluteFill style={{ top: 210, alignItems: 'center' }}>
      <div style={{
        fontFamily: FONTE, fontWeight: 900, fontSize: 54, color: COR.fundo, background: COR.amarelo, padding: '14px 34px',
        borderRadius: 16, boxShadow: `0 8px 0 ${COR.laranja}`, transform: `translateY(${entra}px)`, opacity: interpolate(f, [0, 6], [0, 1], limita),
      }}>{texto}</div>
    </AbsoluteFill>
  );
};

/** O cartão do assunto: imagens em pixel (as texturas do jogo, ampliadas sem borrar) e um texto curto. */
const Carta = ({ imagens, texto, seta, larga, fundo }) => {
  const f = useCurrentFrame();
  const { fps } = useVideoConfig();
  const s = spring({ frame: f - 4, fps, config: { damping: 11, stiffness: 170 } });
  const flutua = Math.sin(f / 12) * 8;
  const lado = larga ? 760 : imagens.length > 1 ? 300 : 400;
  return (
    <AbsoluteFill style={{ top: 420, alignItems: 'center' }}>
      <div style={{
        transform: `translateY(${flutua}px) scale(${s})`, opacity: s,
        background: fundo || 'rgba(24,14,12,0.88)', border: `6px solid ${COR.laranja}`, borderRadius: 36,
        boxShadow: `0 14px 0 ${COR.contorno}, 0 0 60px ${COR.laranja}66`, padding: '36px 44px 26px',
        display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 22,
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 30 }}>
          {imagens.map((img, i) => (
            <div key={img} style={{ display: 'flex', alignItems: 'center', gap: 30 }}>
              {i > 0 && seta && <span style={{ fontFamily: FONTE, fontWeight: 900, fontSize: 110, color: COR.amarelo }}>→</span>}
              <img src={staticFile(`narracao/icones/${img}.png`)}
                style={{ width: lado, imageRendering: 'pixelated', filter: 'drop-shadow(0 10px 0 rgba(0,0,0,.45))' }} />
            </div>
          ))}
        </div>
        {texto && <div style={{ fontFamily: FONTE, fontWeight: 900, fontSize: 56, color: COR.amarelo, textShadow: `0 5px 0 ${COR.contorno}` }}>{texto}</div>}
      </div>
    </AbsoluteFill>
  );
};

export const Narrado = ({ id, roteiro, tempos }) => {
  const fim = Math.ceil(tempos.duracao * FPS);
  return (
    <AbsoluteFill style={{ backgroundColor: COR.fundo }}>
      <Audio src={staticFile(`narracao/${id}.mp3`)} />
      {roteiro.musica && <Audio src={staticFile(roteiro.musica)} volume={0.1} loop />}
      {tempos.falas.map((fala, i) => {
        const de = Math.round(fala.ini * FPS);
        const dur = (i + 1 < tempos.falas.length ? Math.round(tempos.falas[i + 1].ini * FPS) : fim) - de;
        const { video: [arquivo, inicio], titulo, carta } = roteiro.falas[i];
        return (
          <Sequence key={i} from={de} durationInFrames={dur}>
            <Clipe arquivo={arquivo} inicio={inicio} volume={0} zoom={[1.02, 1.1]} />
            {carta && <AbsoluteFill style={{ backgroundColor: 'rgba(10,6,4,0.45)' }} />}
            <Fagulhas quantas={18} />
            {titulo && <Titulo texto={titulo} />}
            {carta && <Carta {...carta} />}
            <Legenda palavras={fala.palavras} inicioFala={fala.ini} />
          </Sequence>
        );
      })}
      <Sequence from={fim}>
        <AbsoluteFill style={{ background: `radial-gradient(ellipse 90% 60% at 50% 100%, ${COR.fundoBrilho} 0%, ${COR.fundo} 70%)` }} />
        <Fagulhas />
        <Final />
      </Sequence>
    </AbsoluteFill>
  );
};
