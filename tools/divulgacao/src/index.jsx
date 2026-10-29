import { Composition, registerRoot } from 'remotion';
import { Teaser } from './Teaser';
import { Reel, duracaoReel } from './Reel';
import config from '../reels.json';

// Reels e Stories: 1080x1920 a 30 fps. Um Composition por reel do reels.json.
const Raiz = () => (
  <>
    <Composition id="Teaser" component={Teaser} durationInFrames={270} fps={30} width={1080} height={1920} />
    {Object.keys(config.reels).map((id) => (
      <Composition key={id} id={id} component={Reel} defaultProps={{ id }}
        durationInFrames={duracaoReel(id)} fps={30} width={1080} height={1920} />
    ))}
  </>
);

registerRoot(Raiz);
