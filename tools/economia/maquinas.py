"""
Quanto cada máquina rende, comparado com a mina no rank em que ela libera.

    python tools/economia/maquinas.py

Lê o config do MaquinasPlugin e a simulação da mina (simular.py, jogador de referência). Pra cada
máquina: produtos por hora, lucro por hora no preço de âncora (venda menos combustível, sem bônus),
em quantas horas ela se paga e quanto o lucro é da renda da mina naquele rank. Meta: uma máquina
rende uns 10% da mina do rank no nível 0. A coluna "nível máx" é com a velocidade no máximo, e as
duas últimas, com o limite do terreno todo usado nesse nível (num terreno só, e pra quem tem dois).

A segunda tabela é o mercado. O preço é de cada jogador (as vendas dele só baixam o dele): com o
terreno cheio de uma máquina, na velocidade máxima e vendendo tudo, o preço dele se estabiliza onde a
queda das vendas empata com a volta pra âncora. Mostra esse preço com o "passo" do config, o passo
que deixa esse preço em --alvo da âncora, e confere se o mínimo cobre o combustível (senão, vender
no mínimo dá prejuízo). O mínimo sugerido é --margem acima do combustível, já pagando o imposto.
"""
import argparse
import math
import sys
from pathlib import Path

import yaml

sys.path.insert(0, str(Path(__file__).parent))
_ARGV, sys.argv = sys.argv, sys.argv[:1]  # o simular.py lê os próprios argumentos ao importar
import simular as S  # noqa: E402
sys.argv = _ARGV

RAIZ = Path(__file__).resolve().parents[2]
CONFIG = RAIZ / "maquinas-plugin/src/main/resources/config.yml"


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--alvo", type=float, default=0.75, help="preço de equilíbrio do terreno cheio, em fração da âncora")
    ap.add_argument("--margem", type=float, default=0.25, help="quanto o mínimo fica acima do combustível")
    args = ap.parse_args(sys.argv[1:] if __name__ == "__main__" else [])
    cfg = yaml.safe_load(CONFIG.read_text(encoding="utf-8"))
    ranks, itens, loja, minas = S.carregar()
    linhas = S.simular(ranks, itens, loja, minas, argparse.Namespace(**S.REFERENCIA))
    # renda da mina de quem ESTÁ no rank (a linha i é a subida pro rank i+1, feita no rank i)
    renda_no_rank = {ranks[i]["id"]: linhas[i][2] for i in range(len(linhas))}
    renda_no_rank[ranks[-1]["id"]] = linhas[-1][2]
    preco_cel = cfg["combustivel"]["preco"]
    fator_max = 1 / (1 - cfg["velocidade-por-nivel"] / 100 * cfg["nivel-maximo"])
    # o limite é por terreno, somando todos os tipos (config antigo: por jogador)
    lim = cfg.get("limite-por-terreno", cfg.get("limite-por-jogador", 10))
    lim_vip = 2 * lim
    print(f"velocidade no máximo: x{fator_max:.2f} | limite: {lim} por terreno ({lim_vip} com dois terrenos)")
    print(f"{'máquina':<12}{'rank':<12}{'prod/h':>8}{'lucro/h':>12}{'se paga':>9}{'mina/h':>12}{'% mina':>8}"
          f"{'nível máx':>10}{'1 terr.':>8}{'2 terr.':>11}")
    for mid, m in cfg["maquinas"].items():
        prod = cfg["produtos"][m["produto"]]
        por_hora = 3600 / m["intervalo-segundos"]
        lucro = por_hora * (prod["ancora"] - m["combustivel-por-produto"] * preco_cel)
        mina = renda_no_rank.get(m["rank"], float("nan"))
        p = lucro / mina
        print(f"{mid:<12}{m['rank']:<12}{por_hora:>8.0f}{lucro:>12,.0f}{m['preco'] / lucro:>8.1f}h{mina:>12,.0f}{p:>8.0%}"
              f"{p * fator_max:>10.0%}{p * fator_max * lim:>8.0%}{p * fator_max * lim_vip:>11.0%}")
    mercado(cfg, lim, fator_max, args)


def equilibrio(ancora, minimo, passo, fator, por_intervalo):
    """Preço em que as vendas de um intervalo e a volta pra âncora se empatam (Mercado.regenerar)."""
    queda = (1 - passo / 100) ** por_intervalo
    return max(minimo, ancora * fator / (1 - queda * (1 - fator)))


def passo_para(alvo, fator, por_intervalo):
    """O passo (%) que deixa o equilíbrio em alvo × âncora."""
    queda = (1 - fator / alvo) / (1 - fator)
    return 100 * (1 - queda ** (1 / por_intervalo))


def mercado(cfg, lim, fator_max, args):
    preco_cel = cfg["combustivel"]["preco"]
    fator = cfg["regeneracao"]["fator"]
    intervalo = cfg["regeneracao"]["intervalo-segundos"]
    imposto = cfg["imposto-venda"] / 100
    print(f"\nmercado por jogador, terreno cheio ({lim} máquinas) na velocidade máxima, vendendo tudo"
          f" | volta {fator:.0%} a cada {intervalo}s | alvo {args.alvo:.0%} da âncora")
    print(f"{'produto':<20}{'âncora':>8}{'comb.':>8}{'mínimo':>8}{'mín. sug.':>10}{'passo':>8}{'equilíbrio':>12}"
          f"{'passo p/ alvo':>15}{'lucro/un no eq.':>17}")
    for m in cfg["maquinas"].values():
        prod = cfg["produtos"][m["produto"]]
        comb = m["combustivel-por-produto"] * preco_cel
        por_intervalo = lim * fator_max * intervalo / m["intervalo-segundos"]
        eq = equilibrio(prod["ancora"], prod["minimo"], prod["passo"], fator, por_intervalo)
        sugerido = math.ceil(comb * (1 + args.margem) / (1 - imposto))
        aviso = "  << mínimo abaixo do combustível" if prod["minimo"] * (1 - imposto) < comb else ""
        print(f"{m['produto']:<20}{prod['ancora']:>8,.0f}{comb:>8,.2f}{prod['minimo']:>8,.0f}{sugerido:>10,.0f}"
              f"{prod['passo']:>7.3f}%{eq:>12,.2f}{passo_para(args.alvo, fator, por_intervalo):>14.3f}%"
              f"{eq * (1 - imposto) - comb:>17,.2f}{aviso}")


if __name__ == "__main__":
    main()
