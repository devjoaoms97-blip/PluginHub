// Paleta da BrasaMC, a mesma do tools/marca/icone.py.
// Inter Black: a fonte pixelada (Pixelify Sans) deixava o C parecido com O ("BRASAMO") e o 2 com 8.
import { loadFont } from '@remotion/google-fonts/Inter';

export const { fontFamily: FONTE } = loadFont('normal', { weights: ['400', '900'], subsets: ['latin', 'latin-ext'] });

export const COR = {
  fundo: '#180e0c',
  fundoBrilho: '#782c12',
  contorno: '#240a08',
  branco: '#fff6c4',
  amarelo: '#ffc436',
  laranja: '#f7741e',
  vermelho: '#c42a18',
  carvao: '#2c2828',
};
