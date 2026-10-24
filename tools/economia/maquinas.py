"""
Quanto cada máquina rende, comparado com a mina no rank em que ela libera.

    python tools/economia/maquinas.py

Lê o config do MaquinasPlugin e a simulação da mina (simular.py, jogador de referência). Pra cada
máquina: produtos por hora, lucro por hora no preço de âncora (venda menos combustível, sem bônus),
em quantas horas ela se paga e quanto o lucro é da renda da mina naquele rank. Meta: uma máquina
rende uns 10% da mina do rank no nível 0. A coluna "nível máx" é com a velocidade no máximo, e as
duas últimas, com o limite de máquinas todo usado nesse nível (sem VIP e no VIP Diamante).
"""
import argparse
import sys
from pathlib import Path

import yaml

sys.path.insert(0, str(Path(__file__).parent))
sys.argv = sys.argv[:1]
import simular as S  # noqa: E402

RAIZ = Path(__file__).resolve().parents[2]
CONFIG = RAIZ / "maquinas-plugin/src/main/resources/config.yml"


def main():
    argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter).parse_args()
    cfg = yaml.safe_load(CONFIG.read_text(encoding="utf-8"))
    ranks, itens, loja, minas = S.carregar()
    linhas = S.simular(ranks, itens, loja, minas, argparse.Namespace(**S.REFERENCIA))
    # renda da mina de quem ESTÁ no rank (a linha i é a subida pro rank i+1, feita no rank i)
    renda_no_rank = {ranks[i]["id"]: linhas[i][2] for i in range(len(linhas))}
    renda_no_rank[ranks[-1]["id"]] = linhas[-1][2]
    preco_cel = cfg["combustivel"]["preco"]
    fator_max = 1 / (1 - cfg["velocidade-por-nivel"] / 100 * cfg["nivel-maximo"])
    lim = cfg["limite-por-jogador"]
    lim_vip = max(v["limite"] for v in cfg.get("limite-vip", [])) if cfg.get("limite-vip") else lim
    print(f"velocidade no máximo: x{fator_max:.2f} | limite: {lim} sem VIP, {lim_vip} no Diamante")
    print(f"{'máquina':<12}{'rank':<12}{'prod/h':>8}{'lucro/h':>12}{'se paga':>9}{'mina/h':>12}{'% mina':>8}"
          f"{'nível máx':>10}{'todas':>8}{'todas VIP':>11}")
    for mid, m in cfg["maquinas"].items():
        prod = cfg["produtos"][m["produto"]]
        por_hora = 3600 / m["intervalo-segundos"]
        lucro = por_hora * (prod["ancora"] - m["combustivel-por-produto"] * preco_cel)
        mina = renda_no_rank.get(m["rank"], float("nan"))
        p = lucro / mina
        print(f"{mid:<12}{m['rank']:<12}{por_hora:>8.0f}{lucro:>12,.0f}{m['preco'] / lucro:>8.1f}h{mina:>12,.0f}{p:>8.0%}"
              f"{p * fator_max:>10.0%}{p * fator_max * lim:>8.0%}{p * fator_max * lim_vip:>11.0%}")


if __name__ == "__main__":
    main()
