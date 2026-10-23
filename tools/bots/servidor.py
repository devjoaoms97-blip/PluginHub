# Liga o servidor de teste sem console interativo, pra rodar os bots, e o mantém ligado até
# existir o arquivo PARAR nesta pasta. Cria PRONTO quando o servidor termina de ligar.
# O log vai para <servidor>\ultimo-boot.txt. Java e memória vêm do scripts\pluginhub.json do
# servidor (gerado pelo montar.ps1). Pasta do servidor: MC_TESTE (padrão C:\mc-teste).
#
#   python servidor.py        (em outro terminal, ou em segundo plano)
#   ... node teste-coleta.js ...
#   criar o arquivo PARAR     (stop limpo; se travar no stop, encerra o processo após 90 s)
import json, os, subprocess, threading, time

aqui = os.path.dirname(os.path.abspath(__file__))
pasta = os.environ.get("MC_TESTE", r"C:\mc-teste")
parar, pronto = os.path.join(aqui, "PARAR"), os.path.join(aqui, "PRONTO")
for f in (parar, pronto):
    if os.path.exists(f):
        os.remove(f)

with open(os.path.join(pasta, "scripts", "pluginhub.json"), encoding="utf-8-sig") as f:
    config = json.load(f)
memoria = config.get("memoria", "2G")
p = subprocess.Popen([config["java"], f"-Xms{memoria}", f"-Xmx{memoria}", "-jar", "paper.jar", "--nogui"], cwd=pasta,
                     stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                     text=True, encoding="utf-8", errors="replace")
log = open(os.path.join(pasta, "ultimo-boot.txt"), "w", encoding="utf-8")

def ler():
    for linha in p.stdout:
        log.write(linha)
        log.flush()
        if "Done (" in linha:
            open(pronto, "w").close()
            print("servidor pronto", flush=True)

threading.Thread(target=ler, daemon=True).start()
while p.poll() is None and not os.path.exists(parar):
    time.sleep(1)
if p.poll() is None:
    p.stdin.write("stop\n")
    p.stdin.flush()
    try:
        p.wait(90)
    except subprocess.TimeoutExpired:
        print("travou no stop, encerrando o processo")
        p.kill()
time.sleep(1)
log.close()
for f in (parar, pronto):
    if os.path.exists(f):
        os.remove(f)
print("exit", p.returncode)
