// Sobe o site com um link público (túnel grátis da Cloudflare).
// Uso: node publicar.js [oficial|teste]   (ou dois cliques no site.bat)
// O link muda toda vez que o túnel reinicia.
const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');

const servidor = process.argv[2] || 'oficial';
const candidatos = [
  'C:\\Program Files (x86)\\cloudflared\\cloudflared.exe',
  'C:\\Program Files\\cloudflared\\cloudflared.exe',
];
const cloudflared = candidatos.find(c => fs.existsSync(c)) || 'cloudflared';

let site = null;
const tunel = spawn(cloudflared, ['tunnel', '--no-autoupdate', '--url', 'http://127.0.0.1:8080']);

function procurarLink(pedaco) {
  if (site) return;
  const achado = String(pedaco).match(/https:\/\/[a-z0-9-]+\.trycloudflare\.com/);
  if (!achado) return;
  const url = achado[0];
  console.log('\n==============================================');
  console.log('  Site no ar: ' + url);
  console.log('  (feche esta janela para tirar o site do ar)');
  console.log('==============================================\n');
  site = spawn(process.execPath, [path.join(__dirname, 'src', 'servidor.js'), servidor], {
    cwd: __dirname,
    env: { ...process.env, SITE_URL: url },
    stdio: 'inherit',
  });
  site.on('exit', codigo => {
    console.log('O site parou (código ' + codigo + '). Fechando o túnel.');
    tunel.kill();
    process.exit(1);
  });
}

tunel.stdout.on('data', procurarLink);
tunel.stderr.on('data', procurarLink);
tunel.on('exit', codigo => {
  console.log('O túnel parou (código ' + codigo + ').');
  if (site) site.kill();
  process.exit(1);
});

function encerrar() {
  if (site) site.kill();
  tunel.kill();
  process.exit(0);
}
process.on('SIGINT', encerrar);
process.on('SIGTERM', encerrar);
console.log('Abrindo o túnel (uns 10 segundos)...');
