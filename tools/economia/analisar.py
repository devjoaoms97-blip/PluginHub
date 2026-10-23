"""
Resumo da economia de um servidor rodando os plugins do PluginHub, pra análise periódica.

Lê os arquivos que os plugins gravam:
  plugins/PainelEconomiaPlugin/historico.yml   fluxo por plugin, circulação, distribuição, atividade
  plugins/EconomyShopPlugin/vendas-diarias.yml vendas por item e preço médio
  plugins/EconomyShopPlugin/itens.yml          preços de referência da loja
  plugins/RankupPlugin/rankups.csv             horas de jogo de cada rankup
  plugins/RankupPlugin/config.yml              preços dos ranks

e compara o tempo de cada rank com o que a simulação (simular.py) espera.

Uso:
  python analisar.py                       (servidor em C:\\mc-teste, últimos 7 dias)
  python analisar.py --servidor D:\\srv --dias 14
"""
import argparse
import csv
import statistics
from datetime import date, timedelta
from pathlib import Path

import yaml

import simular

# Limites dos avisos no fim do relatório
PRECO_BAIXO = 0.6        # item vendido abaixo de 60% da âncora, na média do período
DESVIO_RANK = 0.5        # rank levando 50% mais (ou menos) tempo que o simulado
MIN_RANKUPS = 3          # só compara um rank com pelo menos isso de rankups
ALTA_POR_HORA = 0.2      # líquido por hora jogada subiu 20% da primeira pra segunda metade


def ler_yaml(caminho):
    if not caminho.exists():
        return None
    return yaml.safe_load(caminho.read_text(encoding="utf-8")) or {}


def por_data(secao):
    """Chaves de dia viram date (o YAML pode ler '2026-10-22' como texto ou como data)."""
    return {date.fromisoformat(str(k)): v for k, v in (secao or {}).items()}


def valor(v):
    return f"R$ {v:,.0f}".replace(",", ".")


def sinal(v):
    return ("+" if v >= 0 else "-") + valor(abs(v))


def resumo_diario(painel, dias):
    print("\n== Por dia ==")
    print(f"{'dia':<11}{'jog.':>5}{'horas':>7}{'entrou':>14}{'saiu':>14}{'líquido':>14}{'líq./hora':>11}"
          f"{'circulação':>15}{'mediana':>11}{'gini':>6}")
    linhas = []
    for dia in dias:
        d = painel.get(dia, {})
        origens = d.get("origens", {}) or {}
        entrou = sum(o.get("entrou", 0) for o in origens.values())
        saiu = sum(o.get("saiu", 0) for o in origens.values())
        atividade = d.get("atividade", {}) or {}
        horas = atividade.get("horas", 0)
        dist = d.get("distribuicao", {}) or {}
        por_hora = (entrou - saiu) / horas if horas >= 1 else None
        linhas.append((dia, por_hora))
        print(f"{dia.isoformat():<11}{atividade.get('jogadores', 0):>5}{horas:>7.1f}{valor(entrou):>14}{valor(saiu):>14}"
              f"{sinal(entrou - saiu):>14}{(sinal(por_hora) if por_hora is not None else '-'):>11}"
              f"{(valor(d['circulacao']) if 'circulacao' in d else '-'):>15}"
              f"{(valor(dist['mediana']) if dist else '-'):>11}{(format(dist['gini'], '.2f') if dist else '-'):>6}")
    return linhas


def fontes_e_ralos(painel, dias):
    """Líquido de cada tipo de movimentação no período. Positivo = fonte de dinheiro novo; negativo = ralo."""
    liquido = {}
    for dia in dias:
        for plugin, o in (painel.get(dia, {}).get("origens", {}) or {}).items():
            tipos = o.get("tipos") or {"(total)": o}
            for tipo, t in tipos.items():
                chave = f"{plugin} / {tipo}"
                liquido[chave] = liquido.get(chave, 0) + t.get("entrou", 0) - t.get("saiu", 0)
    print("\n== Fontes e ralos no período (líquido por tipo) ==")
    if not liquido:
        print("  Sem movimentação registrada.")
        return None
    for chave, v in sorted(liquido.items(), key=lambda e: -abs(e[1])):
        if abs(v) >= 0.5:
            print(f"  {sinal(v):>16}  {chave}")
    fontes = sum(v for v in liquido.values() if v > 0)
    ralos = -sum(v for v in liquido.values() if v < 0)
    razao = ralos / fontes if fontes else None
    print(f"  Fontes {valor(fontes)}, ralos {valor(ralos)}"
          + (f": os ralos tiram {razao:.0%} do que entra" if razao is not None else ""))
    return razao


def loja(vendas, itens, dias):
    print("\n== Loja: vendas no período ==")
    total = {}
    for dia in dias:
        for material, v in (vendas.get(dia) or {}).items():
            t = total.setdefault(material, [0, 0.0])
            t[0] += v.get("quantidade", 0)
            t[1] += v.get("valor", 0)
    if not total:
        print("  Nenhuma venda registrada.")
        return []
    baixos = []
    print(f"  {'item':<20}{'vendidos':>10}{'valor':>14}{'preço médio':>13}{'% da âncora':>13}")
    for material, (qtd, val) in sorted(total.items(), key=lambda e: -e[1][1]):
        item = itens.get(material)
        medio = val / qtd if qtd else 0
        if item:
            ancora = max(item["minimo"], min(item["maximo"], item["preco-base"] * (1 - item["margem"] / 100)))
            fracao = medio / ancora
            if fracao < PRECO_BAIXO:
                baixos.append((material, fracao))
            pct = f"{fracao:.0%}"
        else:
            pct = "-"
        print(f"  {material:<20}{qtd:>10,}{valor(val):>14}{medio:>13.2f}{pct:>13}")
    return baixos


def rankups(csv_arquivo, ranks, itens, loja_cfg, minas):
    print("\n== Rankups: horas de jogo em cada rank ==")
    if not csv_arquivo.exists():
        print("  Nenhum rankup registrado ainda.")
        return []
    # horas_jogadas é o total do jogador no momento do rankup; o tempo no rank é a diferença
    # entre o rankup que tirou ele de lá e o que colocou ele lá (ou o zero, no primeiro rank)
    por_jogador = {}
    with csv_arquivo.open(encoding="utf-8") as f:
        for linha in csv.DictReader(f):
            por_jogador.setdefault(linha["uuid"], []).append(linha)
    observadas = {}
    for linhas in por_jogador.values():
        linhas.sort(key=lambda l: float(l["horas_jogadas"]))
        entrou_no_rank = {ranks[0]["id"]: 0.0}
        for l in linhas:
            horas = float(l["horas_jogadas"])
            if l["de"] in entrou_no_rank:
                observadas.setdefault(l["para"], []).append(horas - entrou_no_rank[l["de"]])
            entrou_no_rank[l["para"]] = horas

    cenario = argparse.Namespace(**simular.REFERENCIA)
    esperadas = {rank["id"]: horas for rank, _, _, horas, _ in simular.simular(ranks, itens, loja_cfg, minas, cenario)}
    desvios = []
    print(f"  {'rank':<12}{'rankups':>8}{'mediana':>9}{'simulado':>10}")
    for rank in ranks[1:]:
        lista = observadas.get(rank["id"], [])
        mediana = statistics.median(lista) if lista else None
        esperado = esperadas.get(rank["id"])
        print(f"  {rank['id']:<12}{len(lista):>8}{(f'{mediana:.1f} h' if mediana is not None else '-'):>9}"
              f"{(f'{esperado:.1f} h' if esperado else '-'):>10}")
        if mediana is not None and esperado and len(lista) >= MIN_RANKUPS and abs(mediana / esperado - 1) > DESVIO_RANK:
            desvios.append((rank["id"], mediana, esperado, len(lista)))
    return desvios


def main():
    parser = argparse.ArgumentParser(description="Resumo da economia pra análise periódica.")
    parser.add_argument("--servidor", default=r"C:\mc-teste", help="pasta do servidor (padrão C:\\mc-teste)")
    parser.add_argument("--dias", type=int, default=7, help="quantos dias analisar, até hoje (padrão 7)")
    args = parser.parse_args()

    plugins = Path(args.servidor) / "plugins"
    hoje = date.today()
    dias = [hoje - timedelta(days=i) for i in range(args.dias - 1, -1, -1)]
    print(f"Economia de {plugins.parent} de {dias[0]} a {dias[-1]}")

    painel = por_data((ler_yaml(plugins / "PainelEconomiaPlugin/historico.yml") or {}).get("dias"))
    vendas = por_data((ler_yaml(plugins / "EconomyShopPlugin/vendas-diarias.yml") or {}).get("dias"))
    itens = (ler_yaml(plugins / "EconomyShopPlugin/itens.yml") or {}).get("itens", {})
    loja_cfg = ler_yaml(plugins / "EconomyShopPlugin/config.yml")
    rank_cfg = ler_yaml(plugins / "RankupPlugin/config.yml")
    if not painel:
        print("AVISO: sem historico.yml do painel — o PainelEconomiaPlugin está instalado?")

    por_hora = resumo_diario(painel, dias)
    razao = fontes_e_ralos(painel, dias)
    baixos = loja(vendas, itens, dias)
    desvios = []
    if rank_cfg and loja_cfg and itens:
        minas = yaml.safe_load(simular.MINAS.read_text(encoding="utf-8"))["minas"]
        desvios = rankups(plugins / "RankupPlugin/rankups.csv", rank_cfg["ranks"], itens, loja_cfg, minas)

    print("\n== Pontos de atenção ==")
    avisos = []
    validos = [v for _, v in por_hora if v is not None]
    if len(validos) >= 4:
        metade = len(validos) // 2
        antes, depois = statistics.mean(validos[:metade]), statistics.mean(validos[metade:])
        if antes > 0 and depois / antes - 1 > ALTA_POR_HORA:
            avisos.append(f"O líquido por hora jogada subiu de {sinal(antes)} pra {sinal(depois)}: "
                          "cada hora de jogo está colocando mais dinheiro novo. Veja as fontes acima.")
    if razao is not None and razao < 0.5:
        avisos.append(f"Os ralos tiram só {razao:.0%} do dinheiro novo; o resto se acumula. "
                      "Normal no começo de um rankup, preocupante se a mediana dos saldos não para de subir.")
    for material, fracao in baixos:
        avisos.append(f"{material} foi vendido em média a {fracao:.0%} da âncora: a oferta está acima do que "
                      "o passo e a regeneração absorvem (farm?).")
    for rank, mediana, esperado, n in desvios:
        avisos.append(f"Subir pra {rank} está levando {mediana:.1f} h (mediana de {n}); a simulação esperava "
                      f"{esperado:.1f} h.")
    if not avisos:
        avisos.append("Nada fora do esperado (ou ainda não há dados suficientes).")
    for aviso in avisos:
        print(f"  - {aviso}")


if __name__ == "__main__":
    main()
