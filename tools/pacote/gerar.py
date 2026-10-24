"""
Gera o resource pack do servidor (texturas próprias) e grava em
pacote-plugin/src/main/resources/pacote.zip, de onde o PacotePlugin serve pros jogadores.

    python tools/pacote/gerar.py            # gera o pacote.zip
    python tools/pacote/gerar.py --previa   # e uma prévia ampliada das texturas (previa.png, aqui do lado)

Tudo fica no namespace "servidor":
- itens (produtos das máquinas e a célula de combustível): assets/servidor/items/<id>.json -> modelo
  item/generated com a textura 16x16 de texturas.py;
- máquinas: bloco "orientable" (frente, lado e topo); o mesmo modelo serve pro item na mão e pro
  item display que desenha a máquina colocada no terreno.

No plugin, o item usa o componente item_model ("servidor:<id>"). Mudou uma textura? Rode de novo,
recompile o PacotePlugin e reinicie: a hash muda e os jogadores baixam o pacote novo.
"""
import argparse
import io
import json
import sys
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import texturas as T  # noqa: E402

RAIZ = Path(__file__).resolve().parents[2]
DESTINO = RAIZ / "pacote-plugin/src/main/resources/pacote.zip"
FORMATO = 88  # pack_version.resource_major do Paper 26.2 (version.json do servidor)


def png(img):
    buf = io.BytesIO()
    img.save(buf, "PNG")
    return buf.getvalue()


def montar():
    arquivos = {}

    def j(caminho, dados):
        arquivos[caminho] = json.dumps(dados, indent=2, ensure_ascii=False).encode("utf-8")

    j("pack.mcmeta", {"pack": {"description": "§6Texturas do servidor", "pack_format": FORMATO,
                               "min_format": FORMATO, "max_format": FORMATO}})
    arquivos["pack.png"] = png(T.cristal_de_energia().resize((64, 64), 0))

    for nome, desenho in T.PRODUTOS.items():
        arquivos[f"assets/servidor/textures/item/{nome}.png"] = png(desenho())
        j(f"assets/servidor/models/item/{nome}.json",
          {"parent": "minecraft:item/generated", "textures": {"layer0": f"servidor:item/{nome}"}})
        j(f"assets/servidor/items/{nome}.json", {"model": {"type": "minecraft:model", "model": f"servidor:item/{nome}"}})

    arquivos["assets/servidor/textures/block/maquina_lado.png"] = png(T.maquina_lado())
    arquivos["assets/servidor/textures/block/maquina_topo.png"] = png(T.maquina_topo())
    for nome, frente in T.FRENTES.items():
        arquivos[f"assets/servidor/textures/block/maquina_{nome}_frente.png"] = png(frente())
        j(f"assets/servidor/models/block/maquina_{nome}.json",
          {"parent": "minecraft:block/orientable",
           "textures": {"top": "servidor:block/maquina_topo", "front": f"servidor:block/maquina_{nome}_frente",
                        "side": "servidor:block/maquina_lado"}})
        j(f"assets/servidor/items/maquina_{nome}.json",
          {"model": {"type": "minecraft:model", "model": f"servidor:block/maquina_{nome}"}})
    return arquivos


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--previa", action="store_true")
    args = ap.parse_args()

    arquivos = montar()
    DESTINO.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(DESTINO, "w", zipfile.ZIP_DEFLATED) as z:
        for caminho in sorted(arquivos):
            # data fixa: o mesmo conteúdo gera o mesmo zip (e a mesma hash)
            info = zipfile.ZipInfo(caminho, date_time=(2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(info, arquivos[caminho])
    print(f"{len(arquivos)} arquivos -> {DESTINO} ({DESTINO.stat().st_size} bytes)")

    if args.previa:
        from PIL import Image
        itens = [f() for f in T.PRODUTOS.values()] + [f() for f in T.FRENTES.values()] + [T.maquina_lado(), T.maquina_topo()]
        folha = Image.new("RGBA", (len(itens) * 136, 136), (60, 60, 60, 255))
        for i, img in enumerate(itens):
            folha.alpha_composite(img.resize((128, 128), 0), (i * 136 + 4, 4))
        folha.save(Path(__file__).parent / "previa.png")
        print("prévia: tools/pacote/previa.png")


if __name__ == "__main__":
    main()
