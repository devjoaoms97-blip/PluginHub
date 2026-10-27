"""
Gera o ícone da BrasaMC em pixel art: uma chama sobre um monte de brasas, em 32x32,
ampliado sem suavizar (512 px pro Discord, 1024 px pro app do bot).

    python tools/marca/icone.py            # grava brasamc-512.png e brasamc-1024.png aqui do lado

A chama é a soma de três gotas (a do meio e duas línguas laterais). Cada pixel recebe um
"calor" de 0 a 1 pela distância até a borda da gota, e o calor vira faixa de cor. O que
encosta na chama por fora vira contorno escuro.
"""
import math
from pathlib import Path

from PIL import Image

AQUI = Path(__file__).parent
N = 32

FUNDO = (24, 14, 12)
FUNDO_BRILHO = (120, 44, 18)
CONTORNO = (36, 10, 8)
FAIXAS = [  # (calor mínimo, cor)
    (0.80, (255, 246, 196)),
    (0.58, (255, 196, 54)),
    (0.30, (247, 116, 30)),
    (0.00, (196, 42, 24)),
]
CARVAO = [(44, 40, 40), (58, 52, 50), (34, 30, 30)]
BRASA = [(255, 140, 40), (230, 70, 24), (255, 200, 80)]

# (centro x, centro y, raio da base, altura da ponta, inclinação da ponta)
GOTAS = [
    (15.5, 18.0, 7.6, 16.0, -1.5),
    (10.0, 19.5, 4.2, 9.0, -2.5),
    (21.5, 19.0, 4.6, 11.0, 2.0),
]


def calor_gota(x, y, cx, cy, r, h, inclinacao):
    if y >= cy:
        d = math.hypot(x - cx, (y - cy) * 1.25) / r
        return 1 - d
    t = (cy - y) / h
    if t >= 1:
        return 0
    meia_largura = r * (1 - t) ** 1.25
    d = abs(x - cx - inclinacao * t * t) / meia_largura
    return (1 - d) * (1 - 0.45 * t)


def calor(x, y):
    return max(calor_gota(x + 0.5, y + 0.5, *g) for g in GOTAS)


def cor_da_chama(c):
    for minimo, cor in FAIXAS:
        if c >= minimo:
            return cor
    return None


def monte_de_brasas(x, y):
    """Monte elíptico na base: pedaços de carvão de 3x2 com as frestas acesas e a borda de cima
    em brasa viva. Padrão fixo, sem sorteio."""
    cx, cy, rx, ry = 15.5, 24.5, 10.0, 3.4
    if ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2 > 1:
        return None
    acima = ((x + 0.5 - cx) / rx) ** 2 + ((y - 0.5 - cy) / ry) ** 2 > 1
    if acima:
        return BRASA[2] if abs(x + 0.5 - cx) < 6 else BRASA[0]
    linha = y // 2
    coluna = (x + linha) // 3
    fresta = (x + linha) % 3 == 0 or y % 2 == 0
    pedaco = (coluna * 5 + linha * 3) % 7
    if fresta and pedaco in (0, 2, 5):
        return BRASA[(coluna + linha) % 2]
    return CARVAO[pedaco % 3]


def gerar():
    img = Image.new("RGB", (N, N), FUNDO)
    px = img.load()
    for y in range(N):
        for x in range(N):
            d = math.hypot(x + 0.5 - 15.5, y + 0.5 - 16) / 15
            if d < 1:  # brilho em volta da chama
                k = (1 - d) ** 1.5
                px[x, y] = tuple(round(f + (b - f) * k) for f, b in zip(FUNDO, FUNDO_BRILHO))

    chama = {(x, y) for y in range(N) for x in range(N) if calor(x, y) > 0}
    for x, y in chama:
        px[x, y] = cor_da_chama(calor(x, y))
    for y in range(N):
        for x in range(N):
            cor = monte_de_brasas(x, y)
            if cor and ((x, y) not in chama or y >= 23):
                px[x, y] = cor
    for y in range(N):
        for x in range(N):
            if (x, y) in chama or monte_de_brasas(x, y):
                continue
            vizinhos = [(x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)]
            if any(v in chama for v in vizinhos):
                px[x, y] = CONTORNO
    return img


if __name__ == "__main__":
    base = gerar()
    for tamanho in (512, 1024):
        base.resize((tamanho, tamanho), Image.NEAREST).save(AQUI / f"brasamc-{tamanho}.png")
    print("Gravado em", AQUI)
