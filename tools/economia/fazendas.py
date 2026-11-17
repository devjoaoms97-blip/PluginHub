"""
Quanto cada fazenda rende, comparado com a mina no rank em que ela libera.

    python tools/economia/fazendas.py

Lê o config do FazendasPlugin e a simulação da mina (simular.py, jogador de referência). Pra cada
fazenda: safras por hora, lucro por hora (renda menos o insumo, sem bônus de rank e VIP), em quantas
horas ela se paga e quanto o lucro é da renda da mina naquele rank. Meta (2026-11-17): ~7% da mina no
nível 0 e se paga em ~20 h, como as máquinas. "nível máx" é com a produtividade no máximo, e "terreno"
com o limite de fazendas do terreno todo usado nesse nível.

A mina do Rei é a do Duque × 1,25 (a simulação acaba na subida pro Rei).

O achado raro (desde 2026-11-17) vale raro.horas-de-renda da renda a cada raro.horas-online de produção:
a coluna "raro" é o valor de um, e a linha de cima diz quanto ele soma na renda.

Com --sugerir, mostra a renda por safra e o preço que acertam a meta pro intervalo e o insumo do config.
"""
import argparse
import sys
from pathlib import Path

import yaml

sys.path.insert(0, str(Path(__file__).parent))
_ARGV, sys.argv = sys.argv, sys.argv[:1]  # o simular.py lê os próprios argumentos ao importar
import simular as S  # noqa: E402
sys.argv = _ARGV

RAIZ = Path(__file__).resolve().parents[2]
CONFIG = RAIZ / "fazendas-plugin/src/main/resources/config.yml"


def bonito(v):
    """Arredonda pra dois algarismos significativos (13, 220, 1.200.000)."""
    e = 10 ** max(0, len(str(int(v))) - 2)
    return int(round(v / e) * e)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--meta", type=float, default=0.07, help="lucro por hora, em fração da mina do rank")
    ap.add_argument("--horas", type=float, default=20, help="em quantas horas a fazenda se paga")
    ap.add_argument("--sugerir", action="store_true", help="mostra renda e preço que acertam a meta")
    args = ap.parse_args(sys.argv[1:] if __name__ == "__main__" else [])
    cfg = yaml.safe_load(CONFIG.read_text(encoding="utf-8"))
    ranks, itens, loja, minas = S.carregar()
    linhas = S.simular(ranks, itens, loja, minas, argparse.Namespace(**S.REFERENCIA))
    mina = {r["id"]: linhas[min(i, len(linhas) - 1)][2] for i, r in enumerate(ranks)}
    mina["rei"] = mina["duque"] * 1.25
    fator_max = 1 / (1 - cfg["produtividade-por-nivel"] / 100 * cfg["nivel-maximo"])
    lim = cfg["limite-por-terreno"]
    raro = cfg.get("raro", {})
    print(f"produtividade no máximo: x{fator_max:.2f} | limite: {lim} por terreno | achado raro: "
          f"{raro.get('horas-de-renda', 1.5)} h de renda a cada {raro.get('horas-online', 48)} h (+{raro.get('horas-de-renda', 1.5) / raro.get('horas-online', 48):.1%})")
    print(f"{'fazenda':<11}{'rank':<11}{'safra/h':>8}{'insumo':>8}{'lucro/h':>10}{'se paga':>9}{'mina/h':>10}{'% mina':>8}"
          f"{'nível máx':>10}{'terreno':>9}{'raro':>10}" + (f"{'renda sug.':>12}{'preço sug.':>12}" if args.sugerir else ""))
    for fid, f in cfg["fazendas"].items():
        preco_ins = cfg["insumos"][f["insumo"]]["preco"]
        safras = 3600 / f["intervalo-segundos"]
        custo = f["insumo-por-safra"] * preco_ins
        lucro = safras * (f["renda-por-safra"] - custo)
        m = mina.get(f["rank"], float("nan"))
        p = lucro / m
        linha = (f"{fid:<11}{f['rank']:<11}{safras:>8.0f}{custo / f['renda-por-safra']:>8.0%}{lucro:>10,.0f}"
                 f"{f['preco'] / lucro:>8.1f}h{m:>10,.0f}{p:>8.1%}{p * fator_max:>10.1%}{p * fator_max * lim:>9.0%}"
                 f"{round(raro.get('horas-de-renda', 1.5) * safras * f['renda-por-safra']):>10,}")
        if args.sugerir:
            renda = bonito(args.meta * m / safras + custo)
            preco = bonito(args.horas * safras * (renda - custo))
            linha += f"{renda:>12,}{preco:>12,}"
        print(linha)


if __name__ == "__main__":
    main()
