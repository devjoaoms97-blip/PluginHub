import {
  AbsoluteFill, Audio, OffthreadVideo, Sequence, interpolate, random, spring, staticFile, useCurrentFrame, useVideoConfig,
} from 'remotion';
import { COR } from './marca';
import { Fagulhas, Final, Texto, limita } from './Teaser';
import config from '../reels.json';

// Os brutos podem vir deitados (1920x1020 da Game Bar, 1920x1080 do OBS) ou já em pé (1080x1920 do
// OBS com a janela do jogo em 9:16). O objectFit "cover" preenche a tela vertical: o deitado perde as
// laterais (placar e chat ficam de fora) e o em pé entra inteiro.

// Desfoque do chat do jogo (canto inferior esquerdo do bruto em pé), quando a gravação saiu com ele
// aberto: "ofuscarChat": true no reel. Fica dentro do zoom, então acompanha a imagem. Uma segunda cópia do vídeo, borrada, por cima só onde fica o chat; a máscara esfuma as bordas pra
// não aparecer um retângulo.
const MASCARA_CHAT = 'radial-gradient(ellipse 760px 330px at 230px 1740px, black 0%, black 62%, transparent 100%)';

export const Clipe = ({ arquivo, inicio, zoom = [1, 1.08], ofuscarChat = false, volume = 0.25 }) => {
  const f = useCurrentFrame();
  const { durationInFrames, fps } = useVideoConfig();
  const escala = interpolate(f, [0, durationInFrames], zoom);
  return (
    <AbsoluteFill style={{ overflow: 'hidden', backgroundColor: 'black' }}>
      <div style={{ position: 'absolute', width: 1080, height: 1920, left: 0, top: 0, transform: `scale(${escala})` }}>
        <OffthreadVideo
          src={staticFile(`brutos/${arquivo}`)}
          startFrom={Math.round(inicio * fps)}
          volume={volume}
          style={{
            position: 'absolute', width: 1080, height: 1920, left: 0, top: 0, objectFit: 'cover',
            filter: 'brightness(1.15) contrast(1.12) saturate(1.15)',
          }}
        />
        {ofuscarChat && (
          <OffthreadVideo
            src={staticFile(`brutos/${arquivo}`)}
            startFrom={Math.round(inicio * fps)}
            muted
            style={{
              // um pouco maior que a tela, senão a borda esquerda do borrão fica transparente
              position: 'absolute', width: 1080, height: 1920, left: 0, top: 0, objectFit: 'cover',
              transform: 'scale(1.08)', filter: 'blur(24px) brightness(1.05) contrast(1.12) saturate(1.15)',
              maskImage: MASCARA_CHAT, WebkitMaskImage: MASCARA_CHAT,
            }}
          />
        )}
      </div>
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
            <Clipe arquivo={arquivo} inicio={inicio} ofuscarChat={!!config.reels[id].ofuscarChat} />
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
