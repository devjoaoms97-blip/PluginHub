"""Gera o modpack opcional do servidor (Fabric), em três formas:

- BrasaMC-<versão>.mrpack: o formato do Modrinth, que o Modrinth App e o Prism importam.
- BrasaMC-<versão>-jogar.zip: uma pasta com o Prism portátil e o "Jogar BrasaMC.bat" (dois cliques e joga;
  pede conta original, como todo launcher oficial).
- BrasaMC-<versão>-instalador.zip: o "Instalar BrasaMC.bat", que põe o Fabric, os mods e o shader na
  .minecraft, para jogar por qualquer launcher (inclusive os de conta não original). Ele não baixa o jogo.

O .mrpack não leva nenhum jar: só a lista dos arquivos com o link do CDN do Modrinth e as hashes, que o
launcher (Modrinth App, Prism) baixa na instalação. As versões ficam fixas em MODS (id da versão no
Modrinth); o script pega no Modrinth o link, o tamanho e as hashes de cada uma e confere que todas são
do 26.2 e do Fabric e que as dependências obrigatórias estão na lista.

A textura do servidor não entra: o PacotePlugin manda ela ao entrar (o servers.dat já aceita).

Nenhum dos três leva jar de mod nem o shader: o launcher (ou o instalador) baixa do CDN do Modrinth e
confere a hash. A pasta "jogar" leva o Prism, que é GPL-3.0 e pode ir junto.

Uso:
    python tools/modpack/gerar.py                       # endereço de produção (ENDERECO)
    python tools/modpack/gerar.py --ip stammer-fix.tun.ply.gg --saida teste
"""
import argparse
import hashlib
import io
import json
import struct
import sys
import urllib.request
import zipfile
from pathlib import Path

NOME = "BrasaMC"
VERSAO = "1.0.0"
MINECRAFT = "26.2"
FABRIC_LOADER = "0.20.1"
# trocar quando a VPS tiver endereço
ENDERECO = "jogar.brasamc.com.br"

# (slug, id da versão no Modrinth, pasta no jogo). Antes de pôr um mod, ler a licença: o Entity Culling ficou
# de fora porque a dele proíbe uso com vantagem comercial (o servidor vende VIP).
MODS = [
    ("fabric-api", "ewUK83HI", "mods"),             # 0.161.0+26.2
    ("sodium", "xJZxADzI", "mods"),                 # 0.9.2, desempenho do render
    ("iris", "gxZWWnKH", "mods"),                   # 1.11.4, shaders
    ("lithium", "f7vZ0VWU", "mods"),                # 0.25.3, desempenho do jogo
    ("ferrite-core", "d5ddUdiB", "mods"),           # 9.0.0, menos memória
    ("immediatelyfast", "pMWERcSu", "mods"),        # 1.16.5, desempenho da interface e do texto
    ("sodium-extra", "GJjGKm2A", "mods"),           # 0.9.5, mais opções de vídeo
    ("reeses-sodium-options", "nnz8j4Hu", "mods"),  # 2.2.4, tela de opções do Sodium
    ("modmenu", "iRo7AZXf", "mods"),                # 20.0.3, lista de mods no menu
    ("placeholder-api", "NDqH16LT", "mods"),        # 3.1.0, exigido pelo Mod Menu
    # Licença: pode entrar em modpack pelo sistema do Modrinth (nunca o zip enviado direto), com crédito
    # visível na página do modpack (README.md daqui).
    ("complementary-reimagined", "Bqen1mJX", "shaderpacks"),  # r5.9.3
]

# Prism Launcher portátil (Windows x64), da página de releases do GitHub; sha256 de lá (campo digest)
PRISM_URL = ("https://github.com/PrismLauncher/PrismLauncher/releases/download/11.1.1/"
             "PrismLauncher-Windows-MSVC-Portable-11.1.1.zip")
PRISM_SHA256 = "ab35a770fb06d89d2ccc098079db5db329fb4e68f42b72babd8b095efde3d2d7"

API = "https://api.modrinth.com/v2"
AGENTE = "PluginHub-modpack/1.0 (github.com/devjoaoms97-blip/PluginHub)"
PASTA = Path(__file__).parent


def baixar(url):
    req = urllib.request.Request(url, headers={"User-Agent": AGENTE})
    with urllib.request.urlopen(req, timeout=120) as r:
        return r.read()


def api(caminho):
    return json.loads(baixar(API + caminho))


def prism():
    """O zip do Prism, guardado em .cache (baixa uma vez só) e conferido pelo sha256."""
    cache = PASTA / ".cache" / PRISM_URL.rsplit("/", 1)[1]
    if not cache.exists():
        cache.parent.mkdir(exist_ok=True)
        cache.write_bytes(baixar(PRISM_URL))
    dados = cache.read_bytes()
    if hashlib.sha256(dados).hexdigest() != PRISM_SHA256:
        cache.unlink()
        sys.exit("ERRO: o zip do Prism veio com outra hash")
    return dados


def windows(texto, bom=False):
    """Texto com quebra CRLF (o .bat precisa) e, no .ps1, BOM (o PowerShell 5.1 lê sem BOM como ANSI)."""
    t = texto.replace("\r\n", "\n").replace("\n", "\r\n").encode("utf-8")
    return (b"\xef\xbb\xbf" + t) if bom else t


# --- NBT (só o necessário pro servers.dat) ---

def _nome(n):
    b = n.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def _string(n, v):
    return b"\x08" + _nome(n) + _nome(v)


def _byte(n, v):
    return b"\x01" + _nome(n) + struct.pack(">b", v)


def servers_dat(nome, ip):
    """servers.dat com um servidor só, que já aceita o resource pack (acceptTextures)."""
    servidor = _string("name", nome) + _string("ip", ip) + _byte("acceptTextures", 1) + b"\x00"
    lista = b"\x09" + _nome("servers") + b"\x0a" + struct.pack(">i", 1) + servidor
    return b"\x0a" + _nome("") + lista + b"\x00"


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--ip", default=ENDERECO, help="endereço do servidor na lista de servidores")
    p.add_argument("--saida", default="", help="sufixo do arquivo (ex.: teste)")
    args = p.parse_args()

    arquivos, projetos, dependencias, avisos = [], set(), [], []
    shader = None
    for slug, versao_id, pasta in MODS:
        v = api(f"/version/{versao_id}")
        if MINECRAFT not in v["game_versions"]:
            avisos.append(f"{slug} {v['version_number']} não diz {MINECRAFT}: {v['game_versions']}")
        if pasta == "mods" and "fabric" not in v["loaders"]:
            avisos.append(f"{slug} {v['version_number']} não é Fabric: {v['loaders']}")
        projetos.add(v["project_id"])
        f = next((x for x in v["files"] if x["primary"]), v["files"][0])
        arquivos.append({
            "path": f"{pasta}/{f['filename']}",
            "hashes": {"sha1": f["hashes"]["sha1"], "sha512": f["hashes"]["sha512"]},
            "env": {"client": "required", "server": "unsupported"},
            "downloads": [f["url"]],
            "fileSize": f["size"],
        })
        if pasta == "shaderpacks":
            shader = f["filename"]
        print(f"  {slug:26} {v['version_number']}")
        dependencias += [(slug, d["project_id"]) for d in v["dependencies"] if d["dependency_type"] == "required"]
    avisos += [f"{slug} precisa do projeto {d}, que não está em MODS" for slug, d in dependencias if d not in projetos]
    if avisos:
        print("\n".join("ERRO: " + a for a in avisos))
        sys.exit(1)

    indice = {
        "formatVersion": 1,
        "game": "minecraft",
        "versionId": VERSAO,
        "name": NOME,
        "summary": "Modpack opcional do BrasaMC: Sodium, Iris com o Complementary Reimagined e mods de desempenho.",
        "files": arquivos,
        "dependencies": {"minecraft": MINECRAFT, "fabric-loader": FABRIC_LOADER},
    }
    # shader ligado na primeira vez; dá pra trocar ou desligar em Opções > Vídeo > Shaders
    iris = f"enableShaders=true\nshaderPack={shader}\n"

    base = f"{NOME}-{VERSAO}" + (f"-{args.saida}" if args.saida else "")
    indice_json = json.dumps(indice, indent=2, ensure_ascii=False)
    servers = servers_dat(NOME, args.ip)

    mrpack = io.BytesIO()
    with zipfile.ZipFile(mrpack, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("modrinth.index.json", indice_json)
        z.writestr("overrides/servers.dat", servers)
        z.writestr("overrides/config/iris.properties", iris)
    (PASTA / f"{base}.mrpack").write_bytes(mrpack.getvalue())

    # instalador: a versão do Fabric com o nome do servidor (o launcher baixa o 26.2 e as bibliotecas)
    versao = json.loads(baixar(f"https://meta.fabricmc.net/v2/versions/loader/{MINECRAFT}/{FABRIC_LOADER}/profile/json"))
    versao["id"] = f"{NOME}-fabric-{MINECRAFT}"
    inst = PASTA / "instalador"
    with zipfile.ZipFile(PASTA / f"{base}-instalador.zip", "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("BrasaMC/Instalar BrasaMC.bat", windows((inst / "Instalar BrasaMC.bat").read_text(encoding="utf-8")))
        z.writestr("BrasaMC/instalar.ps1", windows((inst / "instalar.ps1").read_text(encoding="utf-8"), bom=True))
        z.writestr("BrasaMC/modrinth.index.json", indice_json)
        z.writestr("BrasaMC/versao.json", json.dumps(versao, indent=2))
        z.writestr("BrasaMC/servers.dat", servers)
        z.writestr("BrasaMC/iris.properties", iris)

    # pasta "jogar": o Prism portátil em launcher/, o .mrpack e o atalho
    jogar = (PASTA / "jogar" / "Jogar BrasaMC.bat").read_text(encoding="utf-8").replace("__SERVIDOR__", args.ip)
    with zipfile.ZipFile(PASTA / f"{base}-jogar.zip", "w", zipfile.ZIP_DEFLATED) as z:
        with zipfile.ZipFile(io.BytesIO(prism())) as p:
            for item in p.infolist():
                if not item.is_dir():
                    z.writestr(f"BrasaMC/launcher/{item.filename}", p.read(item))
        z.writestr("BrasaMC/BrasaMC.mrpack", mrpack.getvalue())
        z.writestr("BrasaMC/Jogar BrasaMC.bat", windows(jogar))

    print(f"\n{base}.mrpack, {base}-instalador.zip e {base}-jogar.zip: {len(arquivos)} arquivos, servidor {args.ip}")


if __name__ == "__main__":
    main()
