"""
Gera o resource pack do servidor (texturas próprias) e grava em
pacote-plugin/src/main/resources/pacote.zip, de onde o PacotePlugin serve pros jogadores.

    python tools/pacote/gerar.py            # gera o pacote.zip
    python tools/pacote/gerar.py --previa   # e uma prévia ampliada das texturas (previa.png, aqui do lado)

Tudo fica no namespace "servidor":
- itens (produtos das máquinas, a célula de combustível e o fragmento arcano): assets/servidor/items/<id>.json -> modelo
  item/generated com a textura 16x16 de texturas.py;
- máquinas: bloco "orientable" (frente, lado e topo); o mesmo modelo serve pro item na mão e pro
  item display que desenha a máquina colocada no terreno;
- skins de armadura, ferramenta e arma (skins.py, cosméticos do CreditosPlugin): o item skin_<coleção>_<peça>
  (handheld nas ferramentas) e, na armadura, o equipamento skin_<coleção> (assets/servidor/equipment), com as
  camadas "humanoid" e "humanoid_leggings";
- skins de montaria (montarias.py, cosméticos do CreditosPlugin usados pelo MontariaPlugin): modelos 3D de
  caixas, o item montaria_<id> (parado) e montaria_<id>_0..3 (o ciclo andando), todos com a textura de
  paleta item/montaria_paleta; e o equipamento "invisivel" (sem camada nenhuma), que esconde a sela do
  cavalo invisível que carrega o modelo;
- asas e chapéus de terceiros (terceiros/artillex, ver o LEIA-ME de lá): os modelos vêm prontos; aqui só
  mudam de lugar (texturas em servidor:item/asas e servidor:item/chapeus) e ganham os itens asa_<id> e chapeu_<id>;
- ícones das montarias do FreeMinecraftModels (terceiros/frenchkrab): icones.py desenha o .bbmodel inteiro
  numa imagem, item icone_<arquivo> (a loja mostra; o FMM só gera as partes soltas do bicho).

No plugin, o item usa o componente item_model ("servidor:<id>"); a armadura com skin, o equippable com
asset_id "servidor:skin_<coleção>". Mudou uma textura? Rode de novo,
recompile o PacotePlugin e reinicie: a hash muda e os jogadores baixam o pacote novo.
"""
import argparse
import io
import json
import sys
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import icones as IC  # noqa: E402
import montarias as MO  # noqa: E402
import skins as S  # noqa: E402
import texturas as T  # noqa: E402

RAIZ = Path(__file__).resolve().parents[2]
TERCEIROS = Path(__file__).parent / "terceiros"
# pasta de terceiros -> (tipo, prefixo do item, namespace/pasta das texturas no modelo original)
PACOTES_DE_TERCEIROS = {
    "artillex/asas": ("asas", "asa", "ax_wings_pack/"),
    "artillex/chapeus": ("chapeus", "chapeu", "ax_free_hats_pack/"),
}
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

    for colecao in S.COLECOES:
        for peca in S.PECAS:
            nome = f"skin_{colecao}_{peca}"
            pai = "minecraft:item/generated" if peca in S.ARMADURA else "minecraft:item/handheld"
            arquivos[f"assets/servidor/textures/item/{nome}.png"] = png(S.icone(colecao, peca))
            j(f"assets/servidor/models/item/{nome}.json", {"parent": pai, "textures": {"layer0": f"servidor:item/{nome}"}})
            j(f"assets/servidor/items/{nome}.json", {"model": {"type": "minecraft:model", "model": f"servidor:item/{nome}"}})
        arquivos[f"assets/servidor/textures/entity/equipment/humanoid/skin_{colecao}.png"] = png(S.humanoid(colecao))
        arquivos[f"assets/servidor/textures/entity/equipment/humanoid_leggings/skin_{colecao}.png"] = png(S.humanoid_leggings(colecao))
        j(f"assets/servidor/equipment/skin_{colecao}.json",
          {"layers": {"humanoid": [{"texture": f"servidor:skin_{colecao}"}],
                      "humanoid_leggings": [{"texture": f"servidor:skin_{colecao}"}]}})

    paleta, indices = MO.paleta()
    arquivos["assets/servidor/textures/item/montaria_paleta.png"] = png(paleta)
    for bicho in MO.BICHOS:
        for quadro in MO.QUADROS:
            nome = f"montaria_{bicho}" if quadro == "parado" else f"montaria_{bicho}_{quadro}"
            j(f"assets/servidor/models/montaria/{nome}.json", MO.modelo(bicho, quadro, indices))
            j(f"assets/servidor/items/{nome}.json", {"model": {"type": "minecraft:model", "model": f"servidor:montaria/{nome}"}})
    j("assets/servidor/equipment/invisivel.json", {"layers": {}})

    for pasta, (tipo, prefixo, antigo) in PACOTES_DE_TERCEIROS.items():
        origem = TERCEIROS / pasta
        for png_ in sorted(origem.glob("*.png")):
            arquivos[f"assets/servidor/textures/item/{tipo}/{png_.name}"] = png_.read_bytes()
        for modelo in sorted(origem.glob("*.json")):
            dados = json.loads(modelo.read_text(encoding="utf-8"))
            dados["textures"] = {k: v.replace(antigo, f"servidor:item/{tipo}/") for k, v in dados.get("textures", {}).items()}
            id_ = modelo.stem.removesuffix("_wings").removesuffix("_hat").removesuffix("_helmet") if tipo == "asas" else modelo.stem
            j(f"assets/servidor/models/{tipo}/{modelo.stem}.json", dados)
            j(f"assets/servidor/items/{prefixo}_{id_}.json", {"model": {"type": "minecraft:model", "model": f"servidor:{tipo}/{modelo.stem}"}})

    for bbmodel in sorted((TERCEIROS / "frenchkrab").glob("*.bbmodel")):
        nome = f"icone_{bbmodel.stem}"
        arquivos[f"assets/servidor/textures/item/icones/{nome}.png"] = png(IC.icone(bbmodel))
        j(f"assets/servidor/models/item/{nome}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"servidor:item/icones/{nome}"}})
        j(f"assets/servidor/items/{nome}.json", {"model": {"type": "minecraft:model", "model": f"servidor:item/{nome}"}})
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
        itens += [S.icone(c, p) for c in S.COLECOES for p in S.PECAS]
        por_linha = len(S.PECAS)
        linhas = (len(itens) + por_linha - 1) // por_linha
        folha = Image.new("RGBA", (por_linha * 136, linhas * 136), (60, 60, 60, 255))
        for i, img in enumerate(itens):
            folha.alpha_composite(img.resize((128, 128), 0), (i % por_linha * 136 + 4, i // por_linha * 136 + 4))
        folha.save(Path(__file__).parent / "previa.png")
        print("prévia: tools/pacote/previa.png")


if __name__ == "__main__":
    main()
