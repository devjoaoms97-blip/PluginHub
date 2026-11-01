"""
Quanto cada spawner rende, comparado com a mina no rank em que ele libera.

    python tools/economia/spawners.py [--pilhagem 3] [--terrenos 3]

Lê o config do SpawnersPlugin, os preços da loja do servidor de teste e a simulação da mina
(simular.py, jogador de referência). Um spawner soma 1 mob a cada ciclo (180 por hora com 20 s), e
quem mata a pilha leva os drops de todos. Pra cada tipo: o drop médio por mob (o do Minecraft, sem
os raros), a renda de 1 spawner no preço de âncora, em quantas horas ele se paga, e a renda com o
terreno cheio (limite-por-terreno do mesmo tipo), já no preço de equilíbrio da loja (o preço cai
com o volume vendido) e com o bônus de venda do rank. A última coluna compara essa renda com a
mina do rank.
"""
import argparse
import sys
from pathlib import Path

import yaml

sys.path.insert(0, str(Path(__file__).parent))
_args = sys.argv[1:]
sys.argv = sys.argv[:1]
import simular as S  # noqa: E402

RAIZ = Path(__file__).resolve().parents[2]
CONFIG = RAIZ / "spawners-plugin/src/main/resources/config.yml"

# Drop médio por mob, sem Pilhagem: (item, média, +média por nível de Pilhagem). Os raros (ferro do
# zumbi, cabeças, discos) ficam de fora.
DROPS = {
    "CHICKEN": [("FEATHER", 1.0, 0.5), ("CHICKEN", 1.0, 0.5)],
    "PIG": [("PORKCHOP", 2.0, 0.5)],
    "SHEEP": [("WHITE_WOOL", 1.0, 0.0), ("MUTTON", 1.5, 0.5)],
    "COW": [("LEATHER", 1.0, 0.5), ("BEEF", 2.0, 0.5)],
    "ZOMBIE": [("ROTTEN_FLESH", 1.0, 0.5)],
    "SKELETON": [("BONE", 1.0, 0.5), ("ARROW", 1.0, 0.5)],
    "SPIDER": [("STRING", 1.0, 0.5), ("SPIDER_EYE", 1 / 3, 0.5)],
    "CAVE_SPIDER": [("STRING", 1.0, 0.5), ("SPIDER_EYE", 1 / 3, 0.5)],
    "HUSK": [("ROTTEN_FLESH", 1.0, 0.5)],
    "DROWNED": [("ROTTEN_FLESH", 1.0, 0.5), ("COPPER_INGOT", 0.11, 0.02)],
    "STRAY": [("BONE", 1.0, 0.5), ("ARROW", 1.0, 0.5)],
    "BOGGED": [("BONE", 1.0, 0.5), ("ARROW", 1.0, 0.5)],
    "ZOMBIFIED_PIGLIN": [("ROTTEN_FLESH", 1.0, 0.5), ("GOLD_NUGGET", 0.5, 0.5)],
    "WITHER_SKELETON": [("COAL", 1 / 3, 0.5), ("BONE", 1.0, 0.5)],
    "BREEZE": [("BREEZE_ROD", 1.5, 0.5)],
    "CREEPER": [("GUNPOWDER", 1.0, 0.5)],
    "SLIME": [("SLIME_BALL", 1.0, 0.5)],
    "ENDERMAN": [("ENDER_PEARL", 0.5, 0.5)],
    "BLAZE": [("BLAZE_ROD", 0.5, 0.5)],
    "IRON_GOLEM": [("IRON_INGOT", 4.0, 0.0), ("POPPY", 1.0, 0.0)],
}


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--pilhagem", type=int, default=0, help="nível de Pilhagem da espada (padrão 0)")
    ap.add_argument("--terrenos", type=int, default=1, help="terrenos cheios do mesmo tipo vendendo juntos (padrão 1)")
    a = ap.parse_args(_args)
    cfg = yaml.safe_load(CONFIG.read_text(encoding="utf-8"))
    ranks, itens, loja, minas = S.carregar()
    linhas = S.simular(ranks, itens, loja, minas, argparse.Namespace(**S.REFERENCIA))
    renda_no_rank = {ranks[i]["id"]: linhas[i][2] for i in range(len(linhas))}
    renda_no_rank[ranks[-1]["id"]] = linhas[-1][2]
    mult = {r["id"]: r["multiplicador-venda"] for r in ranks}
    imposto = loja["imposto"]["percentual-venda"] / 100
    por_hora = 3600 / cfg["ciclo-segundos"]
    lim = cfg["limite-por-terreno"]

    print(f"{por_hora:.0f} mobs/h por spawner | {lim} por terreno | Pilhagem {a.pilhagem} | {a.terrenos} terreno(s)")
    print(f"{'tipo':<17}{'rank':<11}{'preço':>9}{'R$/h 1':>9}{'se paga':>9}{'R$/h cheio':>12}{'mina/h':>10}{'% mina':>8}  sem venda")
    for tid, t in cfg["tipos"].items():
        drops = DROPS.get(t["entidade"], [])
        rank = t["rank"]
        renda_1 = renda_cheio = 0.0
        sem_venda = []
        for item, media, extra in drops:
            q = media + extra * a.pilhagem
            dados = itens.get(item)
            if not dados or dados.get("venda") is False:
                sem_venda.append(item.lower())
                continue
            _, ancora = S.preco_equilibrio(dados, 0, loja)
            renda_1 += q * por_hora * ancora
            unidades_min = q * por_hora * lim * a.terrenos / 60
            preco, _ = S.preco_equilibrio(dados, unidades_min, loja)
            renda_cheio += q * por_hora * lim * preco
        fator = mult.get(rank, 1.0) * (1 - imposto)
        renda_1 *= fator
        renda_cheio *= fator
        mina = renda_no_rank.get(rank, float("nan"))
        paga = t["preco"] / renda_1 if renda_1 else float("inf")
        print(f"{tid:<17}{rank:<11}{t['preco']:>9,}{renda_1:>9,.0f}{paga:>8.1f}h{renda_cheio:>12,.0f}{mina:>10,.0f}"
              f"{renda_cheio / mina:>8.0%}  {', '.join(sem_venda)}")


if __name__ == "__main__":
    main()
