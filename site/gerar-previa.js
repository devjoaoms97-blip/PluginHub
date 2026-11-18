// Gera publico/previa.png (a imagem que aparece quando alguém manda o link do site no Discord ou no
// WhatsApp): tira uma foto de 1200x630 da página /previa com o Edge ou o Chrome sem janela.
// Com o site ligado: node gerar-previa.js [endereço do site, padrão http://127.0.0.1:8080]
const { execFileSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const site = (process.argv[2] || 'http://127.0.0.1:8080').replace(/\/$/, '');
const candidatos = [
  process.env.NAVEGADOR,
  'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
  'C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe',
  'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
  '/usr/bin/chromium', '/usr/bin/chromium-browser', '/usr/bin/google-chrome',
].filter(Boolean);
const navegador = candidatos.find((c) => fs.existsSync(c));
if (!navegador) throw new Error('Não achei o Edge nem o Chrome. Passe o caminho em NAVEGADOR.');

const saida = path.join(__dirname, 'publico', 'previa.png');
execFileSync(navegador, [
  '--headless=new', '--disable-gpu', '--hide-scrollbars', '--force-device-scale-factor=1',
  '--virtual-time-budget=5000', '--window-size=1200,630', `--screenshot=${saida}`, `${site}/previa`,
], { stdio: 'ignore' });
console.log(`Prévia salva em ${saida} (${fs.statSync(saida).size} bytes)`);
