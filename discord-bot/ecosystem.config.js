// O bot oficial no pm2 (religa sozinho se cair e volta quando o PC liga, pelo "pm2 save"):
//   pm2 start ecosystem.config.js     pm2 logs faisca-oficial     pm2 restart faisca-oficial
module.exports = {
  apps: [{
    name: 'faisca-oficial',
    script: 'src/index.js',
    args: 'oficial',
    cwd: __dirname,
    restart_delay: 10000,
    max_memory_restart: '300M',
  }],
};
