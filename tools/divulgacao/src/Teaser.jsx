import {
  AbsoluteFill, Img, Sequence, interpolate, random, spring, staticFile, useCurrentFrame, useVideoConfig,
} from 'remotion';
import { COR, FONTE } from './marca';

export const limita = { extrapolateLeft: 'clamp', extrapolateRight: 'clamp' };

// Fundo escuro com o brilho da brasa embaixo, que pulsa devagar.
const Fundo = ({ vermelhidao = 0 }) => {
  const f = useCurrentFrame();
  const pulso = 0.85 + 0.15 * Math.sin(f / 12);
  return (
    <AbsoluteFill
      style={{
        background: `radial-gradient(ellipse 90% 60% at 50% 100%, ${COR.fundoBrilho}${Math.round(pulso * 200).toString(16)} 0%, ${COR.fundo} 70%)`,
      }}
    >
      <AbsoluteFill style={{ backgroundColor: COR.vermelho, opacity: vermelhidao }} />
    </AbsoluteFill>
  );
};

// Fagulhas quadradas (pixel) subindo do chão. A posição sai de random(semente), então todo render é igual.
export const Fagulhas = ({ quantas = 70 }) => {
  const f = useCurrentFrame();
  const { width, height } = useVideoConfig();
  return (
    <AbsoluteFill>
      {Array.from({ length: quantas }, (_, i) => {
        const vel = 3 + random(`v${i}`) * 6;
        const ciclo = height + 200;
        const y = height + 100 - ((f * vel + random(`y${i}`) * ciclo) % ciclo);
        const x = random(`x${i}`) * width + Math.sin((f + i * 20) / 18) * 30;
        const lado = 6 + Math.floor(random(`t${i}`) * 3) * 6;
        const cor = [COR.amarelo, COR.laranja, COR.branco][i % 3];
        const brilho = interpolate(y, [0, height * 0.4, height], [0, 0.9, 0.6], limita);
        return (
          <div
            key={i}
            style={{
              position: 'absolute', left: x, top: y, width: lado, height: lado,
              backgroundColor: cor, opacity: brilho, boxShadow: `0 0 ${lado * 2}px ${cor}`,
            }}
          />
        );
      })}
    </AbsoluteFill>
  );
};

export const Texto = ({ children, tamanho, cor = COR.branco, estilo }) => (
  <div
    style={{
      fontFamily: FONTE, fontSize: tamanho, color: cor, fontWeight: 700, textAlign: 'center', lineHeight: 1.05,
      textShadow: `0 8px 0 ${COR.contorno}, 0 0 40px ${COR.laranja}88`, ...estilo,
    }}
  >
    {children}
  </div>
);

// Cena 1: a chama cresce, o nome cai letra por letra e a chamada sobe.
const Abertura = () => {
  const f = useCurrentFrame();
  const { fps } = useVideoConfig();
  const entra = spring({ frame: f, fps, config: { damping: 9, mass: 0.8 } });
  const sai = interpolate(f, [95, 115], [1, 0], limita);
  const brilho = 40 + 25 * Math.sin(f / 6);
  return (
    <AbsoluteFill style={{ alignItems: 'center', justifyContent: 'center', opacity: sai }}>
      <Img
        src={staticFile('brasamc-1024.png')}
        style={{
          width: 560, imageRendering: 'pixelated', borderRadius: 48,
          transform: `scale(${entra}) translateY(${-140}px)`,
          filter: `drop-shadow(0 0 ${brilho}px ${COR.laranja})`,
        }}
      />
      <div style={{ display: 'flex', marginTop: -40 }}>
        {'BRASAMC'.split('').map((letra, i) => {
          const s = spring({ frame: f - 18 - i * 3, fps, config: { damping: 11 } });
          return (
            <Texto key={i} tamanho={170} cor={i < 5 ? COR.amarelo : COR.branco}
              estilo={{ transform: `translateY(${(1 - s) * -300}px)`, opacity: s }}>
              {letra}
            </Texto>
          );
        })}
      </div>
      <Texto tamanho={60} cor={COR.laranja}
        estilo={{ marginTop: 30, letterSpacing: 12, opacity: interpolate(f, [45, 60], [0, 1], limita) }}>
        RANKUP MEDIEVAL
      </Texto>
    </AbsoluteFill>
  );
};

// Cena 2: o boss acorda. A tela treme, a barra de vida desce e o aviso da maldição aparece.
const Boss = () => {
  const f = useCurrentFrame();
  const { fps } = useVideoConfig();
  const impacto = spring({ frame: f, fps, config: { damping: 7, stiffness: 180 } });
  const tremor = interpolate(f, [0, 18], [26, 0], limita);
  const dx = (random(`dx${f}`) - 0.5) * tremor;
  const dy = (random(`dy${f}`) - 0.5) * tremor;
  const vida = interpolate(f, [25, 80], [1, 0.12], limita);
  const flash = interpolate(f, [0, 3, 12], [0, 0.55, 0], limita);
  return (
    <AbsoluteFill>
      <Fundo vermelhidao={flash} />
      <Fagulhas quantas={40} />
      <AbsoluteFill style={{ alignItems: 'center', justifyContent: 'center', transform: `translate(${dx}px, ${dy}px)` }}>
      <Texto tamanho={150} cor={COR.branco} estilo={{ transform: `scale(${2 - impacto})`, opacity: impacto }}>
        O BOSS
      </Texto>
      <Texto tamanho={190} cor={COR.vermelho}
        estilo={{ transform: `scale(${2 - impacto})`, opacity: impacto, textShadow: `0 10px 0 ${COR.contorno}, 0 0 60px ${COR.vermelho}` }}>
        ACORDOU
      </Texto>
      <div style={{ marginTop: 70, width: 820, opacity: interpolate(f, [15, 25], [0, 1], limita) }}>
        <Texto tamanho={44} cor={COR.amarelo} estilo={{ textAlign: 'left', marginBottom: 14 }}>WITHER</Texto>
        <div style={{ height: 56, border: `8px solid ${COR.contorno}`, backgroundColor: COR.carvao }}>
          <div style={{ width: `${vida * 100}%`, height: '100%', background: `linear-gradient(${COR.laranja}, ${COR.vermelho})` }} />
        </div>
      </div>
      <Texto tamanho={56} estilo={{ marginTop: 80, opacity: interpolate(f, [40, 50], [0, 1], limita) }}>
        1 hora pra derrotar.
      </Texto>
      <Texto tamanho={56} cor={COR.laranja} estilo={{ marginTop: 20, opacity: interpolate(f, [62, 72], [0, 1], limita) }}>
        Senão, o servidor é amaldiçoado.
      </Texto>
      </AbsoluteFill>
    </AbsoluteFill>
  );
};

// Cena 3: chamada final.
export const Final = () => {
  const f = useCurrentFrame();
  const { fps } = useVideoConfig();
  const s = spring({ frame: f, fps, config: { damping: 10 } });
  return (
    <AbsoluteFill style={{ alignItems: 'center', justifyContent: 'center' }}>
      <Img src={staticFile('brasamc-1024.png')}
        style={{ width: 280, imageRendering: 'pixelated', borderRadius: 24, transform: `scale(${s})`, filter: `drop-shadow(0 0 40px ${COR.laranja})` }} />
      <Texto tamanho={180} cor={COR.amarelo} estilo={{ marginTop: 40, transform: `scale(${s})` }}>EM BREVE</Texto>
      <Texto tamanho={58} estilo={{ marginTop: 50, opacity: interpolate(f, [15, 25], [0, 1], limita) }}>
        Entra no Discord pra<br />saber a data do lançamento
      </Texto>
      <Texto tamanho={30} cor={COR.laranja}
        estilo={{ position: 'absolute', bottom: 120, opacity: 0.7, textShadow: 'none', fontWeight: 400 }}>
        Servidor não oficial. Não afiliado à Mojang ou à Microsoft.
      </Texto>
    </AbsoluteFill>
  );
};

export const Teaser = () => (
  <AbsoluteFill style={{ backgroundColor: COR.fundo }}>
    <Fundo />
    <Fagulhas />
    <Sequence durationInFrames={115}><Abertura /></Sequence>
    <Sequence from={115} durationInFrames={100}><Boss /></Sequence>
    <Sequence from={215}><Final /></Sequence>
  </AbsoluteFill>
);
