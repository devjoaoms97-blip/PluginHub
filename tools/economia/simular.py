"""
Simula a progressão de um jogador no rankup: quantas horas de mineração leva cada rank.

Lê os ranks do config do RankupPlugin, os preços da loja do servidor de teste e as minas de
minas-planejadas.yml. Em cada rank o jogador minera na mina que mais rende entre as que ele
tem acesso, vende tudo no /sell (com o bônus do rank e o imposto) e sobe quando junta o preço.

O preço de venda da loja cai a cada unidade vendida e volta aos poucos pra âncora. A simulação
usa o preço de equilíbrio entre as duas forças, com N jogadores vendendo o mesmo minério.

Uso:
  python simular.py                        (cenários padrão)
  python simular.py --jogadores 5 --fortuna 3
  python simular.py --meta-horas 150       (sugere preços de rank pra bater a meta)
"""
import argparse
import math
from pathlib import Path

import yaml

RAIZ = Path(__file__).resolve().parents[2]
RANKS = RAIZ / "rankup-plugin/src/main/resources/config.yml"
ITENS = RAIZ / "test-server/configs/EconomyShopPlugin/itens.yml"
LOJA = RAIZ / "economy/economy-shop-plugin/src/main/resources/config.yml"
MINAS = Path(__file__).with_name("minas-planejadas.yml")

# Jogador da meta de 150 h: não-VIP, 3 por mina, picareta de diamante com Eficiência III e
# Fortuna III (dá pra encantar na mesa). Os VIPs Ouro e Diamante ganham Fortuna IV e V no kit mensal.
REFERENCIA = dict(jogadores=3, fortuna=3, eficiencia=3, utilizacao=0.5, bonus_vip=0.0)
# Bônus de venda do VIP (vip-bonus-venda no config da loja): vale na venda automática da mina.
BONUS_VIP = {"bronze": 0.03, "prata": 0.06, "ouro": 0.09, "diamante": 0.12}

# bloco: (dureza, item que dropa, quantidade média sem fortuna, como a fortuna age)
# fortuna "minerio" = fórmula vanilla dos minérios; "soma" = +nível/2 (redstone); None = não age
BLOCOS = {
    "stone": (1.5, "COBBLESTONE", 1, None),
    "deepslate": (3.0, "COBBLED_DEEPSLATE", 1, None),
    "netherrack": (0.4, "NETHERRACK", 1, None),
    "coal_ore": (3.0, "COAL", 1, "minerio"),
    "copper_ore": (3.0, "RAW_COPPER", 3.5, "minerio"),
    "iron_ore": (3.0, "RAW_IRON", 1, "minerio"),
    "gold_ore": (3.0, "RAW_GOLD", 1, "minerio"),
    "deepslate_redstone_ore": (4.5, "REDSTONE", 4.5, "soma"),
    "deepslate_lapis_ore": (4.5, "LAPIS_LAZULI", 6.5, "minerio"),
    "deepslate_diamond_ore": (4.5, "DIAMOND", 1, "minerio"),
    "deepslate_emerald_ore": (4.5, "EMERALD", 1, "minerio"),
    "nether_quartz_ore": (3.0, "QUARTZ", 1, "minerio"),
    "nether_gold_ore": (3.0, "GOLD_NUGGET", 4, "minerio"),
    "amethyst_block": (1.5, "AMETHYST_BLOCK", 1, None),
    "oak_log": (2.0, "OAK_LOG", 1, None),
    "spruce_log": (2.0, "SPRUCE_LOG", 1, None),
    "iron_block": (5.0, "IRON_BLOCK", 1, None),
    "gold_block": (3.0, "GOLD_BLOCK", 1, None),
    "diamond_block": (5.0, "DIAMOND_BLOCK", 1, None),
    "emerald_block": (5.0, "EMERALD_BLOCK", 1, None),
    "ancient_debris": (30.0, "ANCIENT_DEBRIS", 1, None),
}


def multiplicador_fortuna(tipo, nivel):
    if not nivel or tipo is None:
        return 1.0
    if tipo == "soma":
        return None  # tratado à parte: soma nível/2 à quantidade
    # vanilla: 2/(n+2) de chance de x1; senão x2..x(n+1) com a mesma chance
    return 2 / (nivel + 2) + nivel / (nivel + 2) * (nivel + 3) / 2


def quantidade_media(bloco, fortuna):
    _, _, base, tipo = BLOCOS[bloco]
    if tipo == "soma":
        return base + fortuna / 2
    return base * multiplicador_fortuna(tipo, fortuna)


def segundos_por_bloco(dureza, eficiencia):
    """Picareta (ou machado) de diamante. Sem quebra instantânea há 5 ticks de espera entre blocos."""
    velocidade = 8 + (eficiencia ** 2 + 1 if eficiencia else 0)
    dano = velocidade / dureza / 30
    if dano >= 1:
        return 1 / 20
    return (math.ceil(1 / dano) + 5) / 20


def carregar():
    ranks = yaml.safe_load(RANKS.read_text(encoding="utf-8"))["ranks"]
    itens = yaml.safe_load(ITENS.read_text(encoding="utf-8"))["itens"]
    loja = yaml.safe_load(LOJA.read_text(encoding="utf-8"))
    minas = yaml.safe_load(MINAS.read_text(encoding="utf-8"))["minas"]
    return ranks, itens, loja, minas


def preco_equilibrio(item, unidades_por_min, loja):
    """Preço em que a queda por venda empata com a regeneração."""
    ancora = item["preco-base"] * (1 - item["margem"] / 100)
    ancora = max(item["minimo"], min(item["maximo"], ancora))
    intervalo = loja["regeneracao"]["intervalo-segundos"]
    fator = loja["regeneracao"]["fator-por-tick"]
    vendidas = unidades_por_min * intervalo / 60
    preco = fator * ancora / (fator + vendidas * item["passo"] / 100)
    return max(item["minimo"], preco), ancora


def renda_por_hora(mina, multiplicador, itens, loja, cenario):
    """R$/h de um jogador nessa mina, e os itens que dropam e a loja não compra."""
    pesos = mina["blocos"]
    total = sum(pesos.values())
    tempo_medio = sum(p / total * segundos_por_bloco(BLOCOS[b][0], cenario.eficiencia) for b, p in pesos.items())
    blocos_por_min = 60 / tempo_medio * cenario.utilizacao

    unidades = {}  # item -> unidades/min de UM jogador
    for bloco, peso in pesos.items():
        item = BLOCOS[bloco][1]
        unidades[item] = unidades.get(item, 0) + blocos_por_min * peso / total * quantidade_media(bloco, cenario.fortuna)

    renda, sem_preco = 0.0, []
    for item, por_min in unidades.items():
        if item not in itens:
            sem_preco.append(item)
            continue
        preco, _ = preco_equilibrio(itens[item], por_min * cenario.jogadores, loja)
        renda += por_min * 60 * preco
    imposto = loja["imposto"]["percentual-venda"] / 100
    bonus_vip = getattr(cenario, "bonus_vip", 0.0)
    return renda * multiplicador * (1 + bonus_vip) * (1 - imposto), sem_preco, blocos_por_min * 60


def simular(ranks, itens, loja, minas, cenario, precos=None):
    """Lista por rank: (rank alvo, mina usada, R$/h, horas pra juntar o preço)."""
    ordem = [r["id"] for r in ranks]
    linhas = []
    for i in range(1, len(ranks)):
        atual = ranks[i - 1]
        acessiveis = [(nome, m) for nome, m in minas.items() if ordem.index(m["rank"]) <= i - 1]
        melhor = max(((nome, *renda_por_hora(m, atual["multiplicador-venda"], itens, loja, cenario))
                      for nome, m in acessiveis), key=lambda x: x[1])
        preco = precos[i] if precos else ranks[i]["preco"]
        linhas.append((ranks[i], melhor[0], melhor[1], preco / melhor[1] if melhor[1] else math.inf, preco))
    return linhas


def imprimir(titulo, linhas):
    print(f"\n== {titulo} ==")
    print(f"{'rank':<11} {'preço':>12} {'mina':<10} {'R$/h':>10} {'horas':>7} {'acumulado':>9}")
    acumulado = 0
    for rank, mina, renda, horas, preco in linhas:
        acumulado += horas
        print(f"{rank['id']:<11} {preco:>12,.0f} {mina:<10} {renda:>10,.0f} {horas:>7.1f} {acumulado:>9.1f}")


def arredondar(valor):
    """Dois algarismos significativos: 123456 -> 120000."""
    if valor <= 0:
        return 0
    casas = 10 ** (int(math.log10(valor)) - 1)
    return round(valor / casas) * casas


def sugerir_precos(ranks, linhas, meta, primeiro):
    """Horas por rank crescendo em progressão geométrica, do 'primeiro' até somar a meta."""
    n = len(linhas)
    baixo, alto = 1.0001, 3.0
    for _ in range(100):
        razao = (baixo + alto) / 2
        soma = primeiro * (razao ** n - 1) / (razao - 1)
        baixo, alto = (razao, alto) if soma < meta else (baixo, razao)
    precos = [0]
    for k, (_, _, renda, _, _) in enumerate(linhas):
        precos.append(arredondar(primeiro * razao ** k * renda))
    return precos, razao


def main():
    parser = argparse.ArgumentParser(description="Simula a progressão do rankup.")
    parser.add_argument("--jogadores", type=int, help="quantos vendem o mesmo minério ao mesmo tempo")
    parser.add_argument("--eficiencia", type=int, default=3, help="nível de Eficiência da picareta (padrão 3)")
    parser.add_argument("--fortuna", type=int, help="nível de Fortuna da picareta")
    parser.add_argument("--utilizacao", type=float, default=0.5,
                        help="fração do tempo quebrando blocos; o resto é andar, vender, esperar reset (padrão 0.5)")
    parser.add_argument("--meta-horas", type=float, help="sugere preços de rank pra chegar ao último nessa quantidade de horas")
    parser.add_argument("--primeiro-rank-horas", type=float, default=1.0, help="horas do primeiro rankup na sugestão")
    args = parser.parse_args()

    ranks, itens, loja, minas = carregar()
    for nome, mina in minas.items():
        for bloco in mina["blocos"]:
            if bloco not in BLOCOS:
                raise SystemExit(f"Mina {nome}: bloco {bloco} não está na tabela BLOCOS do simular.py")

    if args.jogadores or args.fortuna is not None:
        cenarios = [(f"{args.jogadores or 1} jogador(es) por mina, fortuna {args.fortuna or 0}",
                     argparse.Namespace(jogadores=args.jogadores or 1, fortuna=args.fortuna or 0,
                                        eficiencia=args.eficiencia, utilizacao=args.utilizacao, bonus_vip=0.0))]
    else:
        ref = dict(REFERENCIA, eficiencia=args.eficiencia, utilizacao=args.utilizacao)
        cenarios = [
            ("Não-VIP: 3 por mina, Fortuna III (referência)", argparse.Namespace(**ref)),
            ("Sem Fortuna", argparse.Namespace(**dict(ref, fortuna=0))),
            ("Sozinho na mina, Fortuna III", argparse.Namespace(**dict(ref, jogadores=1))),
            ("10 por mina, Fortuna III", argparse.Namespace(**dict(ref, jogadores=10))),
            ("VIP Bronze: Fortuna III, Eficiência III, venda +3%",
             argparse.Namespace(**dict(ref, bonus_vip=BONUS_VIP["bronze"]))),
            ("VIP Prata: Fortuna III, Eficiência IV, venda +6%",
             argparse.Namespace(**dict(ref, eficiencia=4, bonus_vip=BONUS_VIP["prata"]))),
            ("VIP Ouro: Fortuna IV, Eficiência V, venda +9%",
             argparse.Namespace(**dict(ref, fortuna=4, eficiencia=5, bonus_vip=BONUS_VIP["ouro"]))),
            ("VIP Diamante: Fortuna V, Eficiência V, venda +12%",
             argparse.Namespace(**dict(ref, fortuna=5, eficiencia=5, bonus_vip=BONUS_VIP["diamante"]))),
        ]

    print(f"Eficiência {args.eficiencia}, utilização {args.utilizacao:.0%}, "
          f"imposto da loja {loja['imposto']['percentual-venda']}%")
    sem_preco = set()
    for nome, mina in minas.items():
        _, faltando, blocos_h = renda_por_hora(mina, 1.0, itens, loja, cenarios[0][1])
        sem_preco.update(faltando)
    if sem_preco:
        print("ATENÇÃO: a loja não compra " + ", ".join(sorted(sem_preco)) + " (contam como R$ 0)")

    referencia = None
    for titulo, cenario in cenarios:
        linhas = simular(ranks, itens, loja, minas, cenario)
        imprimir(titulo, linhas)
        if referencia is None or "referência" in titulo:
            referencia = (titulo, cenario, linhas)

    if args.meta_horas:
        titulo, cenario, linhas = referencia
        precos, razao = sugerir_precos(ranks, linhas, args.meta_horas, args.primeiro_rank_horas)
        imprimir(f"Preços sugeridos pra {args.meta_horas:.0f} h ({titulo}; cada rank leva {razao:.2f}x o anterior)",
                 simular(ranks, itens, loja, minas, cenario, precos))
        for titulo2, cenario2 in cenarios:
            if cenario2 is cenario:
                continue
            total = sum(h for *_, h, _ in simular(ranks, itens, loja, minas, cenario2, precos))
            print(f"  com esses preços, '{titulo2}': {total:.0f} h")


if __name__ == "__main__":
    main()
