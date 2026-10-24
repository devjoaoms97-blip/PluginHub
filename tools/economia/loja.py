"""
Catálogo da Loja Oficial: gera o itens.yml do EconomyShopPlugin e confere se nenhum preço abre
brecha pra imprimir dinheiro.

    python tools/economia/loja.py                 # confere e mostra o resumo (não grava nada)
    python tools/economia/loja.py --gravar        # grava test-server/configs/EconomyShopPlugin/itens.yml
    python tools/economia/loja.py --gravar --servidor C:\\mc-teste   # e no servidor de teste também

Com o servidor LIGADO, rode /shop admin reload logo depois de gravar: a loja guarda o catálogo na
memória e, sem o reload, grava o antigo por cima do arquivo na próxima venda ou ao desligar.

Regras do catálogo (decididas em 2026-10-24):
- Nas minas, o que se quebra é vendido na hora (venda automática, pra todos). Os mesmos itens também
  vendem pros vendedores, vindos de qualquer lugar (mundo de recursos, farms). Os preços desses itens
  são os de sempre (a simulação das 150 h depende deles) e ficam exatamente como estão.
- Os vendedores compram do jogador minérios, toras, farm, comida e drop de mob. Sem limite de
  quantidade: o preço dinâmico regula (o que muita gente vende vale menos), então farm AFK é bem-vinda.
- Blocos, tábuas, natureza e alquimia: só a loja vende (tira dinheiro de circulação).
- Item feito de outros custa de 1,0 a 1,1 vez a soma dos ingredientes.

Verificações (com o pior caso: rank x2, prestígio 10 x2, VIP +12% e evento x2 = x8,96):
1. Nenhum item vendido do inventário rende mais que a trava (90% do menor preço de compra).
2. Comprar ingredientes, transformar (craftar, fundir, cortar ou quebrar o bloco) e vender o
   resultado nunca dá lucro. Receitas do Minecraft (minecraft-data 26.1) mais as listas abaixo.
3. Aviso (não é brecha): item feito que sai mais barato que os ingredientes e pode ser desmontado.
"""
import argparse
import json
import math
import sys
from pathlib import Path

import yaml

RAIZ = Path(__file__).resolve().parents[2]
DADOS = RAIZ / "tools/bots/node_modules/minecraft-data/minecraft-data/data/pc/26.1"
ITENS_TESTE = RAIZ / "test-server/configs/EconomyShopPlugin/itens.yml"

# Pior caso da venda: rank (último x2,0), prestígio 10 (+100%), VIP Diamante (+12%), evento (x2).
MULT_MAX = 2.0 * 2.0 * 1.12 * 2.0
DESCONTO_MAX = 0.12      # maior desconto de VIP na compra (Diamante)
TRAVA = 0.90             # trava-venda.percentual-da-compra
IMPOSTO = 0.02

# ---------------------------------------------------------------------------------------------
# Catálogo. Cada item: (preço de compra, opções). Opções:
#   venda: "nao" (só a loja vende; padrão) ou "sim" (vendedor compra do inventário)
#   ancora: fração do preço de compra que o vendedor paga normalmente (padrão 0.4)
#   minimo: fração do preço de compra no fundo do poço (padrão 0.1)
#   compra: False = a loja não vende esse item (só compra do jogador)
# ---------------------------------------------------------------------------------------------
CATEGORIAS = [
    ("Minérios", "DIAMOND"), ("Blocos", "STONE_BRICKS"), ("Madeiras", "OAK_LOG"), ("Farm", "WHEAT"),
    ("Drops", "BONE"), ("Natureza", "OAK_SAPLING"), ("Alquimia", "BREWING_STAND"),
]

CORES = ["white", "light_gray", "gray", "black", "brown", "red", "orange", "yellow", "lime", "green",
         "cyan", "light_blue", "blue", "purple", "magenta", "pink"]
MADEIRAS = ["oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "pale_oak"]


def catalogo():
    c = {}

    def add(cat, nome, preco, **op):
        c[nome.upper()] = dict(categoria=cat, preco=float(preco), **op)

    # -- Minérios (minerador). Os da mina vêm do itens.yml de sempre; aqui os derivados. Todos vendem.
    M = "Minérios"
    for nome, preco in [("iron_ingot", 18), ("gold_ingot", 30), ("copper_ingot", 6.5), ("iron_nugget", 2),
                        ("charcoal", 4), ("netherite_scrap", 1050), ("netherite_ingot", 4500),
                        ("coal_block", 38), ("redstone_block", 56), ("lapis_block", 75), ("copper_block", 60),
                        ("raw_iron_block", 150), ("raw_gold_block", 280), ("raw_copper_block", 56),
                        ("quartz_block", 82), ("amethyst_cluster", 160)]:
        add(M, nome, preco, venda="sim")

    # -- Blocos (construtor): só compra.
    B = "Blocos"
    for nome, preco in [("stone", 1.5), ("smooth_stone", 2), ("stone_bricks", 2), ("mossy_stone_bricks", 3),
                        ("cracked_stone_bricks", 2.5), ("chiseled_stone_bricks", 2.5), ("mossy_cobblestone", 2.5),
                        ("deepslate", 1.5), ("polished_deepslate", 2), ("deepslate_bricks", 2.5),
                        ("deepslate_tiles", 2.5), ("chiseled_deepslate", 2.5), ("andesite", 1.5),
                        ("polished_andesite", 2), ("diorite", 1.5), ("polished_diorite", 2), ("granite", 1.5),
                        ("polished_granite", 2), ("tuff", 1.5), ("tuff_bricks", 2), ("calcite", 2),
                        ("sand", 1.5), ("red_sand", 2), ("gravel", 1.5), ("sandstone", 6.5),
                        ("smooth_sandstone", 7), ("cut_sandstone", 7), ("red_sandstone", 8.5),
                        ("glass", 2.5), ("glass_pane", 1), ("bricks", 10), ("mud_bricks", 3),
                        ("packed_mud", 2.5), ("mud", 2), ("clay", 8), ("blackstone", 2),
                        ("polished_blackstone_bricks", 2.5), ("basalt", 2), ("polished_basalt", 2.5),
                        ("nether_bricks", 4.5), ("red_nether_bricks", 12), ("end_stone", 3),
                        ("end_stone_bricks", 3.5), ("purpur_block", 8), ("obsidian", 30),
                        ("crying_obsidian", 40), ("quartz_bricks", 85), ("smooth_quartz", 85),
                        ("prismarine", 17), ("prismarine_bricks", 38), ("dark_prismarine", 36),
                        ("sea_lantern", 50), ("snow_block", 3), ("ice", 3), ("packed_ice", 30),
                        ("dripstone_block", 3), ("terracotta", 4)]:
        add(B, nome, preco)
    for cor in CORES:
        add(B, f"{cor}_concrete", 4)
        add(B, f"{cor}_terracotta", 4.5)
        add(B, f"{cor}_wool", 4)
        add(B, f"{cor}_stained_glass", 3)

    # -- Madeiras (lenhador): as toras vendem (carvalho e pinheiro vêm da mina, preço 7); o resto só compra.
    W = "Madeiras"
    for m in MADEIRAS:
        if m not in ("oak", "spruce"):
            add(W, f"{m}_log", 7, venda="sim")
        add(W, f"{m}_planks", 2)
    for nome, preco in [("crimson_stem", 8), ("warped_stem", 8)]:
        add(W, nome, preco, venda="sim")
    for nome, preco in [("crimson_planks", 2.2), ("warped_planks", 2.2), ("bamboo_block", 3), ("bamboo_planks", 1.6),
                        ("stick", 1)]:
        add(W, nome, preco)

    # -- Farm (fazendeiro): o vendedor compra a colheita; comida pronta só a loja vende.
    F = "Farm"
    for nome, preco, _ in [("wheat", 4, 512), ("carrot", 3, 512), ("potato", 3, 512), ("beetroot", 3, 512),
                                ("pumpkin", 6, 256), ("melon_slice", 1, 1024), ("sugar_cane", 2, 512),
                                ("cactus", 2, 256), ("cocoa_beans", 3, 256), ("sweet_berries", 2, 512),
                                ("glow_berries", 3, 256), ("apple", 4, 256), ("egg", 2, 256),
                                ("honeycomb", 8, 128), ("honey_bottle", 8, 128), ("nether_wart", 3, 512),
                                ("brown_mushroom", 2, 256), ("red_mushroom", 2, 256), ("bamboo", 1, 512),
                                ("kelp", 1, 512), ("chorus_fruit", 5, 256)]:
        add(F, nome, preco, venda="sim")
    for nome, preco in [("wheat_seeds", 0.5), ("beetroot_seeds", 0.5), ("melon_seeds", 2), ("pumpkin_seeds", 2),
                        ("bread", 13), ("baked_potato", 3.5), ("cookie", 2.5), ("pumpkin_pie", 9),
                        ("golden_carrot", 30), ("hay_block", 38), ("melon", 9.5), ("sugar", 2.2),
                        ("dried_kelp", 1.2), ("cooked_beef", 5), ("cooked_porkchop", 5), ("cooked_chicken", 4),
                        ("cooked_mutton", 4), ("cooked_cod", 3.5), ("cooked_salmon", 4.5), ("cake", 40),
                        ("bone_meal", 0.8)]:
        add(F, nome, preco)

    # -- Drops (caçador): o vendedor compra os drops de mob.
    D = "Drops"
    for nome, preco, _ in [("rotten_flesh", 1, 256), ("bone", 2, 256), ("string", 2, 256),
                                ("gunpowder", 4, 256), ("spider_eye", 3, 128), ("ender_pearl", 15, 64),
                                ("blaze_rod", 20, 64), ("slime_ball", 6, 128), ("leather", 4, 128),
                                ("feather", 1.5, 256), ("ink_sac", 3, 128), ("glow_ink_sac", 6, 64),
                                ("phantom_membrane", 15, 32), ("ghast_tear", 40, 16), ("magma_cream", 10, 64),
                                ("prismarine_shard", 4, 256), ("prismarine_crystals", 5, 256),
                                ("rabbit_hide", 2, 128), ("rabbit_foot", 15, 16), ("beef", 3, 256),
                                ("porkchop", 3, 256), ("chicken", 2, 256), ("mutton", 2, 256), ("rabbit", 2, 128),
                                ("cod", 2, 256), ("salmon", 3, 256), ("arrow", 1, 256), ("breeze_rod", 25, 32)]:
        add(D, nome, preco, venda="sim")
    for nome, preco in [("shulker_shell", 300), ("turtle_scute", 60), ("nautilus_shell", 80), ("totem_of_undying", 2500)]:
        add(D, nome, preco)

    # -- Natureza (jardineiro): só compra.
    N = "Natureza"
    for m in MADEIRAS:
        add(N, f"{m}_sapling" if m != "mangrove" else "mangrove_propagule", 5)
        if m not in ("pale_oak",):
            add(N, f"{m}_leaves", 1.5)
    for flor in ["dandelion", "poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip", "orange_tulip",
                 "white_tulip", "pink_tulip", "oxeye_daisy", "cornflower", "lily_of_the_valley", "torchflower"]:
        add(N, flor, 2)
    for flor in ["sunflower", "lilac", "rose_bush", "peony"]:
        add(N, flor, 3)
    for nome, preco in [("dirt", 0.5), ("coarse_dirt", 0.6), ("rooted_dirt", 1), ("grass_block", 2), ("podzol", 3),
                        ("mycelium", 5), ("moss_block", 3), ("moss_carpet", 2.1), ("azalea", 5),
                        ("flowering_azalea", 6), ("azalea_leaves", 1.5), ("flowering_azalea_leaves", 2),
                        ("vine", 2), ("lily_pad", 3), ("dead_bush", 1), ("fern", 1), ("short_grass", 1),
                        ("pink_petals", 2), ("glow_lichen", 2), ("spore_blossom", 30), ("big_dripleaf", 5),
                        ("hanging_roots", 2), ("pointed_dripstone", 1), ("sea_pickle", 3), ("flint", 2),
                        ("clay_ball", 2), ("snowball", 0.75)]:
        add(N, nome, preco)

    # -- Alquimia (alquimista): poções, redstone, nether e end. Só compra.
    A = "Alquimia"
    for nome, preco in [("glowstone_dust", 4), ("glowstone", 17), ("blaze_powder", 10), ("fermented_spider_eye", 7.5),
                        ("glistering_melon_slice", 26), ("ender_eye", 26), ("brewing_stand", 25), ("cauldron", 130),
                        ("redstone_torch", 7), ("repeater", 26), ("comparator", 48), ("piston", 36),
                        ("sticky_piston", 44), ("observer", 40), ("hopper", 110), ("dropper", 14), ("dispenser", 24),
                        ("lever", 2), ("stone_button", 1.6), ("note_block", 23), ("target", 63), ("rail", 7),
                        ("powered_rail", 35), ("daylight_detector", 72), ("book", 10), ("paper", 2.2),
                        ("bookshelf", 45), ("experience_bottle", 30), ("lantern", 6), ("soul_lantern", 6),
                        ("torch", 1.3), ("soul_torch", 1.5), ("chest", 17), ("barrel", 15), ("crafting_table", 8.5),
                        ("furnace", 8.5), ("anvil", 560), ("end_rod", 30), ("respawn_anchor", 400)]:
        add(A, nome, preco)
    return c


# Transformações que não estão nas receitas de mesa de trabalho: fundir, cortar e quebrar o bloco.
# (entrada, quantidade de entrada, saída, quantidade de saída). Quebrar usa o máximo com Fortuna III.
TRANSFORMACOES = [
    ("raw_iron", 1, "iron_ingot", 1), ("raw_gold", 1, "gold_ingot", 1), ("raw_copper", 1, "copper_ingot", 1),
    ("cobblestone", 1, "stone", 1), ("stone", 1, "smooth_stone", 1), ("sand", 1, "glass", 1),
    ("red_sand", 1, "glass", 1), ("clay_ball", 1, "brick", 1), ("clay", 1, "terracotta", 1),
    ("netherrack", 1, "nether_brick", 1), ("cactus", 1, "green_dye", 1), ("kelp", 1, "dried_kelp", 1),
    ("ancient_debris", 1, "netherite_scrap", 1), ("potato", 1, "baked_potato", 1), ("beef", 1, "cooked_beef", 1),
    ("porkchop", 1, "cooked_porkchop", 1), ("chicken", 1, "cooked_chicken", 1), ("mutton", 1, "cooked_mutton", 1),
    ("cod", 1, "cooked_cod", 1), ("salmon", 1, "cooked_salmon", 1), ("chorus_fruit", 1, "popped_chorus_fruit", 1),
    ("oak_log", 1, "charcoal", 1), ("sandstone", 1, "smooth_sandstone", 1), ("quartz_block", 1, "smooth_quartz", 1),
    ("stone_bricks", 1, "cracked_stone_bricks", 1), ("cobbled_deepslate", 1, "deepslate", 1),
    # quebrar o bloco (Fortuna III no máximo)
    ("melon", 1, "melon_slice", 9), ("glowstone", 1, "glowstone_dust", 4), ("bookshelf", 1, "book", 3),
    ("clay", 1, "clay_ball", 4), ("snow_block", 1, "snowball", 4), ("sea_lantern", 1, "prismarine_crystals", 5),
    ("gravel", 1, "flint", 1), ("stone", 1, "cobblestone", 1), ("grass_block", 1, "dirt", 1),
    ("hay_block", 1, "wheat", 9), ("amethyst_cluster", 1, "amethyst_shard", 7),
]


def carregar_mina():
    """Os itens da mina como estão hoje (preço, margem, mínimo, máximo, passo): ficam iguais."""
    dados = yaml.safe_load(ITENS_TESTE.read_text(encoding="utf-8"))["itens"]
    return {k: v for k, v in dados.items() if k in MINA}


# Os itens que saem das minas (a simulação usa os preços deles).
MINA = {"COBBLESTONE", "NETHERRACK", "COBBLED_DEEPSLATE", "COAL", "RAW_COPPER", "OAK_LOG", "SPRUCE_LOG",
        "RAW_IRON", "RAW_GOLD", "REDSTONE", "LAPIS_LAZULI", "GOLD_NUGGET", "QUARTZ", "AMETHYST_SHARD",
        "AMETHYST_BLOCK", "DIAMOND", "EMERALD", "ANCIENT_DEBRIS", "IRON_BLOCK", "GOLD_BLOCK", "DIAMOND_BLOCK",
        "EMERALD_BLOCK"}


def montar():
    """O itens.yml inteiro: os da mina como estão, mais o catálogo."""
    itens = {}
    for nome, v in carregar_mina().items():
        v = dict(v)
        v.pop("preco-venda-atual", None)
        v["categoria"] = "Minérios"
        v.pop("venda", None)  # vende na mina (automática) e pros vendedores
        itens[nome] = v
    for nome, d in catalogo().items():
        if nome in itens:
            raise SystemExit(f"{nome} está no catálogo e na mina")
        preco = d["preco"]
        venda = d.get("venda", "nao")
        e = {"categoria": d["categoria"], "preco-base": preco}
        if venda == "sim":
            ancora = d.get("ancora", 0.4)
            e.update({"margem": round((1 - ancora) * 100, 1), "minimo": round(preco * d.get("minimo", 0.1), 3),
                      "maximo": round(preco * ancora, 3), "passo": 0.5})
        else:
            # sem venda do inventário: os campos de venda não importam (ficam coerentes)
            e.update({"margem": 50.0, "minimo": round(preco * 0.1, 3), "maximo": round(preco * 0.5, 3), "passo": 1.0,
                      "venda": False})
        if d.get("compra") is False:
            e["compra"] = False
        itens[nome] = e
    return itens


# ---------------------------------------------------------------------------------------------
# Verificação
# ---------------------------------------------------------------------------------------------

def vende_do_inventario(e):
    return e.get("venda", True) in (True, "true")


def compravel(e):
    return e.get("compra", True) is not False


def venda_efetiva(e):
    """O máximo que 1 unidade do inventário rende no pior caso (com a trava), depois do imposto."""
    teto = e["preco-base"] * (1 - DESCONTO_MAX) * TRAVA
    return min(e["maximo"] * MULT_MAX, teto) * (1 - IMPOSTO)


def compra_minima(e):
    return e["preco-base"] * (1 - DESCONTO_MAX)


def receitas():
    itens = {i["id"]: i["name"] for i in json.loads((DADOS / "items.json").read_text(encoding="utf-8"))}
    lista = []
    for rs in json.loads((DADOS / "recipes.json").read_text(encoding="utf-8")).values():
        for r in rs:
            entrada = {}
            if "inShape" in r:
                for linha in r["inShape"]:
                    for i in linha:
                        if i is not None:
                            entrada[itens[i]] = entrada.get(itens[i], 0) + 1
            for i in r.get("ingredients", []):
                if i is not None:
                    entrada[itens[i]] = entrada.get(itens[i], 0) + 1
            if entrada:
                lista.append((entrada, itens[r["result"]["id"]], r["result"]["count"]))
    for a, qa, b, qb in TRANSFORMACOES:
        lista.append(({a: qa}, b, qb))
    return lista


def verificar(itens):
    problemas, avisos = [], []
    por_nome = {k.lower(): v for k, v in itens.items()}

    # 1. trava
    for nome, e in itens.items():
        if vende_do_inventario(e) and venda_efetiva(e) >= compra_minima(e):
            problemas.append(f"{nome}: vender ({venda_efetiva(e):.2f}) rende mais que comprar ({compra_minima(e):.2f})")

    # 2 e 3. transformações
    todas = receitas()
    inversas = {(saida, frozenset(entrada)) for entrada, saida, _ in todas if len(entrada) == 1}
    for entrada, saida, qtd in todas:
        if saida not in por_nome or any(i not in por_nome for i in entrada):
            continue
        es = por_nome[saida]
        custo = sum(compra_minima(por_nome[i]) * q for i, q in entrada.items()) if all(
            compravel(por_nome[i]) for i in entrada) else math.inf
        if vende_do_inventario(es) and qtd * venda_efetiva(es) > custo:
            problemas.append(f"{' + '.join(f'{q} {i}' for i, q in entrada.items())} -> {qtd} {saida}: "
                             f"custa {custo:.2f} e vende {qtd * venda_efetiva(es):.2f}")
        # desmontar: comprar a saída e voltar pros ingredientes, quando a receita inversa existe
        volta = len(entrada) == 1 and (next(iter(entrada)), frozenset([saida])) in inversas
        if volta and compravel(es) and custo < math.inf:
            preco_saida = es["preco-base"] * qtd
            soma = sum(por_nome[i]["preco-base"] * q for i, q in entrada.items())
            if preco_saida < 0.9 * soma:
                avisos.append(f"{saida} ({preco_saida:.1f} por {qtd}) sai mais barato que os ingredientes ({soma:.1f})")
    return problemas, avisos


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--gravar", action="store_true", help="grava o itens.yml do test-server")
    ap.add_argument("--servidor", help="grava também em <servidor>/plugins/EconomyShopPlugin/itens.yml")
    args = ap.parse_args()

    itens = montar()
    problemas, avisos = verificar(itens)
    cats = {}
    for e in itens.values():
        cats[e["categoria"]] = cats.get(e["categoria"], 0) + 1
    print(f"{len(itens)} itens: " + ", ".join(f"{c} {n}" for c, n in cats.items()))
    print(f"Os vendedores compram {sum(1 for e in itens.values() if vende_do_inventario(e))}; o resto só a loja vende.")
    for a in avisos:
        print("  aviso:", a)
    for p in problemas:
        print("  BRECHA:", p)
    print("Nenhuma brecha." if not problemas else f"{len(problemas)} brecha(s).")
    if problemas:
        sys.exit(1)

    if args.gravar:
        destinos = [ITENS_TESTE]
        if args.servidor:
            destinos.append(Path(args.servidor) / "plugins/EconomyShopPlugin/itens.yml")
        for d in destinos:
            d.write_text(yaml.safe_dump({"itens": itens}, allow_unicode=True, sort_keys=False), encoding="utf-8")
            print("gravado:", d)


if __name__ == "__main__":
    main()
