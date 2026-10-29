import {
  AbsoluteFill, Audio, OffthreadVideo, Sequence, interpolate, random, spring, staticFile, useCurrentFrame, useVideoConfig,
} from 'remotion';
import { COR } from './marca';
import { Fagulhas, Final, Texto, limita } from './Teaser';
import config from '../reels.json';

// Os brutos são 1920x1020 (a Game Bar corta a barra de título). O corte vertical pega o centro,
// o que deixa de fora o placar (direita) e o chat (esquerda) e mantém a barra do boss no topo.
const ALTURA = 1920;
const LARGURA = Math.round((1920 * ALTURA) / 1020);

const Clipe = ({ arquivo, inicio, zoom = [1, 1.08] }) => {
  const f = useCurrentFrame();
  const { durationInFrames, fps } = useVideoConfig();
  const escala = interpolate(f, [0, durationInFrames], zoom);
  return (
    <AbsoluteFill style={{ overflow: 'hidden', backgroundColor: 'black' }}>
      <OffthreadVideo
        src={staticFile(`brutos/${arquivo}`)}
        startFrom={Math.round(inicio * fps)}
        volume={0.25}
        style={{
          position: 'absolute', height: ALTURA, width: LARGURA, left: (1080 - LARGURA) / 2, top: 0,
          transform: `scale(${escala})`, filter: 'brightness(1.15) contrast(1.12) saturate(1.15)',
        }}
      />
      {/* Escurece em cima e embaixo pra o texto ler bem. */}
      <AbsoluteFill style={{ background: 'linear-gradient(rgba(0,0,0,0.55) 0%, transparent 28%, transparent 62%, rgba(0,0,0,0.75) 100%)' }} />
    </AbsoluteFill>
  );
};

// Texto que entra com impacto (escala de 2 pra 1) e um tremor curto.
const Chamada = ({ linhas, topo = 1300 }) => {
  const f = useCurrentFrame();
  const { fps } = useVideoConfig();
  const s = spring({ frame: f, fps, config: { damping: 8, stiffness: 160 } });
  const tremor = interpolate(f, [0, 10], [18, 0], limita);
  const dx = (random(`cx${f}`) - 0.5) * tremor;
  return (
    <AbsoluteFill style={{ alignItems: 'center', top: topo, transform: `translateX(${dx}px)` }}>
      {linhas.map(([texto, tamanho, cor], i) => (
        <Texto key={i} tamanho={tamanho} cor={COR[cor]}
          estilo={{ transform: `scale(${2 - s})`, opacity: s, padding: '0 40px' }}>
          {texto}
        </Texto>
      ))}
    </AbsoluteFill>
  );
};

const Flash = () => {
  const f = useCurrentFrame();
  return <AbsoluteFill style={{ backgroundColor: 'white', opacity: interpolate(f, [0, 4], [0.7, 0], limita) }} />;
};

export const duracaoReel = (id) => config.reels[id].cenas.reduce((t, c) => t + c[2], 0) + config.final;

// Cenas e textos vêm do reels.json; a trilha (audio/sintetizar.py) é gerada dos mesmos cortes.
export const Reel = ({ id }) => {
  let t = 0;
  return (
    <AbsoluteFill style={{ backgroundColor: COR.fundo }}>
      <Audio src={staticFile(`audio/${id}.wav`)} />
      {config.reels[id].cenas.map(([arquivo, inicio, dur, linhas], i) => {
        const de = t;
        t += dur;
        return (
          <Sequence key={i} from={de} durationInFrames={dur}>
            <Clipe arquivo={arquivo} inicio={inicio} />
            <Fagulhas quantas={25} />
            <Chamada linhas={linhas} />
            <Flash />
          </Sequence>
        );
      })}
      <Sequence from={t}>
        <AbsoluteFill style={{ background: `radial-gradient(ellipse 90% 60% at 50% 100%, ${COR.fundoBrilho} 0%, ${COR.fundo} 70%)` }} />
        <Fagulhas />
        <Final />
      </Sequence>
    </AbsoluteFill>
  );
};
