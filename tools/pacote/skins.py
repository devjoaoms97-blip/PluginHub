"""
Skins de armadura, ferramenta e arma (cosméticos do CreditosPlugin), desenhadas por código. Usadas pelo gerar.py.

Cada coleção tem uma paleta (metal, detalhe e cabo) e sai em 9 peças: capacete, peitoral, calça, botas,
espada, machado, picareta, pá e enxada. Cada peça tem o ícone 16x16 (o item na mão e no inventário); a
armadura tem também a textura vestida (64x32, no formato das camadas do jogo: "humanoid" pra capacete,
peitoral e botas, "humanoid_leggings" pra calça).

O desenho é o mesmo em todas as coleções: uma máscara por peça ('#' metal, 'a' detalhe, 'h' cabo) e o
relevo sai sozinho (borda de cima e da esquerda clara, de baixo e da direita escura). Mudar a paleta de
uma coleção muda as 9 peças.
"""
from PIL import Image

from texturas import contornar, nova

T = (0, 0, 0, 0)

# metal: 4 tons (escuro -> claro); detalhe: 3 tons; cabo: 2 tons
COLECOES = {
    "brasa": {"metal": [(110, 24, 10, 255), (176, 46, 16, 255), (226, 98, 28, 255), (255, 178, 70, 255)],
              "detalhe": [(200, 120, 10, 255), (255, 200, 40, 255), (255, 246, 170, 255)],
              "cabo": [(38, 26, 26, 255), (68, 46, 40, 255)]},
    "cerejeira": {"metal": [(150, 62, 102, 255), (212, 118, 158, 255), (236, 160, 192, 255), (252, 214, 230, 255)],
                  "detalhe": [(84, 130, 60, 255), (124, 176, 82, 255), (255, 255, 255, 255)],
                  "cabo": [(84, 46, 46, 255), (128, 74, 68, 255)]},
    "arcana": {"metal": [(52, 22, 82, 255), (96, 44, 140, 255), (148, 86, 196, 255), (206, 160, 240, 255)],
               "detalhe": [(150, 102, 20, 255), (230, 180, 50, 255), (255, 240, 160, 255)],
               "cabo": [(30, 20, 44, 255), (58, 40, 80, 255)]},
    "gelida": {"metal": [(38, 84, 132, 255), (84, 150, 204, 255), (156, 210, 238, 255), (232, 248, 255, 255)],
               "detalhe": [(28, 150, 170, 255), (80, 220, 230, 255), (255, 255, 255, 255)],
               "cabo": [(50, 58, 78, 255), (84, 96, 120, 255)]},
    "real": {"metal": [(150, 102, 20, 255), (206, 152, 36, 255), (244, 204, 78, 255), (255, 242, 170, 255)],
             "detalhe": [(96, 22, 32, 255), (150, 34, 48, 255), (90, 200, 230, 255)],
             "cabo": [(64, 14, 22, 255), (110, 26, 36, 255)]},
    "sombria": {"metal": [(18, 16, 22, 255), (36, 32, 42, 255), (58, 52, 66, 255), (92, 84, 104, 255)],
                "detalhe": [(130, 10, 30, 255), (220, 30, 60, 255), (255, 120, 140, 255)],
                "cabo": [(12, 10, 14, 255), (30, 26, 34, 255)]},
}

# ---------------------------------------------------------------- ícones (16x16)
# '#' metal, 'a' detalhe, 'h' cabo, '.' vazio

MASCARAS = {
    "espada": [
        "..............##",
        ".............###",
        "............###.",
        "...........#a#..",
        "..........#a#...",
        ".........#a#....",
        "........#a#.....",
        ".......#a#......",
        "......#a#.......",
        "..a..#a#........",
        "...a###.........",
        "....ha..........",
        "...hh.a.........",
        "..hh............",
        ".aa.............",
        "................",
    ],
    "machado": [
        "................",
        "......###.......",
        ".....######.....",
        "....##a####.....",
        "...##a#####h....",
        "...##a####hh....",
        "....##a##hh.....",
        ".....###hh......",
        "........hh......",
        ".......hh.......",
        "......hh........",
        ".....hh.........",
        "....hh..........",
        "...hh...........",
        "..ah............",
        "................",
    ],
    "picareta": [
        "................",
        "....########....",
        "...##aaaa####...",
        "..##.......###..",
        "..#.......hh##..",
        "..#......hh..##.",
        ".........h....#.",
        "........hh....#.",
        ".......hh.....#.",
        "......hh......#.",
        ".....hh.........",
        "....hh..........",
        "...hh...........",
        "..hh............",
        ".ah.............",
        "................",
    ],
    "pa": [
        "................",
        "...........##...",
        "..........####..",
        ".........##a###.",
        "........###a###.",
        "........###a##..",
        ".........h###...",
        "........hh......",
        ".......hh.......",
        "......hh........",
        ".....hh.........",
        "....hh..........",
        "...hh...........",
        "..hh............",
        ".ah.............",
        "................",
    ],
    "enxada": [
        "................",
        "......#######...",
        ".....##aaa###...",
        ".....##....hh...",
        "......#...hh....",
        "..........h.....",
        ".........hh.....",
        "........hh......",
        ".......hh.......",
        "......hh........",
        ".....hh.........",
        "....hh..........",
        "...hh...........",
        "..hh............",
        ".ah.............",
        "................",
    ],
    "capacete": [
        "................",
        "................",
        "................",
        "....########....",
        "...##########...",
        "...#aaaaaaaa#...",
        "...##########...",
        "...###....###...",
        "...##......##...",
        "...##......##...",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
    ],
    "peitoral": [
        "................",
        "..####....####..",
        ".######..######.",
        ".##############.",
        ".##############.",
        ".##.###aa###.##.",
        "....###aa###....",
        "....########....",
        "....########....",
        "....########....",
        "....########....",
        "....########....",
        "....aaaaaaaa....",
        "................",
        "................",
        "................",
    ],
    "calca": [
        "................",
        "................",
        "...aaaaaaaaaa...",
        "...##########...",
        "...##########...",
        "...####..####...",
        "...###....###...",
        "...###....###...",
        "...###....###...",
        "...###....###...",
        "...###....###...",
        "...###....###...",
        "...aaa....aaa...",
        "................",
        "................",
        "................",
    ],
    "botas": [
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "..aaaa....aaaa..",
        "..####....####..",
        "..####....####..",
        "..####....####..",
        ".#####....#####.",
        "######....######",
        "######....######",
        "................",
        "................",
        "................",
    ],
}

ARMADURA = ("capacete", "peitoral", "calca", "botas")
PECAS = ARMADURA + ("espada", "machado", "picareta", "pa", "enxada")


def _cheio(mascara, x, y):
    return 0 <= y < len(mascara) and 0 <= x < len(mascara[y]) and mascara[y][x] != "."


def _relevo(cheio, x, y):
    """0..3: borda de cima/esquerda clara (3), de baixo/direita escura (1), miolo 2."""
    if not cheio(x, y - 1) or not cheio(x - 1, y):
        return 3
    if not cheio(x, y + 1) or not cheio(x + 1, y):
        return 1
    return 2


def icone(colecao, peca):
    p = COLECOES[colecao]
    m = MASCARAS[peca]
    img = nova()
    for y, linha in enumerate(m):
        for x, c in enumerate(linha):
            if c == ".":
                continue
            r = _relevo(lambda a, b: _cheio(m, a, b), x, y)
            if c == "#":
                cor = p["metal"][r]
            elif c == "a":
                cor = p["detalhe"][min(r, 2) if r != 2 else 1]
            else:
                cor = p["cabo"][1 if r == 3 else 0]
            img.putpixel((x, y), cor)
    return contornar(img)


# ---------------------------------------------------------------- armadura vestida (64x32)
# Faces de cada caixa do modelo (x0, y0, largura, altura), no layout das camadas de armadura do jogo.

def _caixa(u, v, w, h, d):
    """As 6 faces de uma caixa w x h x d que começa em (u, v) na textura, com o nome de cada face."""
    return {
        "cima": (u + d, v, w, d), "baixo": (u + d + w, v, w, d),
        "direita": (u, v + d, d, h), "frente": (u + d, v + d, w, h),
        "esquerda": (u + d + w, v + d, d, h), "costas": (u + d + w + d, v + d, w, h),
    }


CABECA = _caixa(0, 0, 8, 8, 8)
CORPO = _caixa(16, 16, 8, 12, 4)
BRACO = _caixa(40, 16, 4, 12, 4)
PERNA = _caixa(0, 16, 4, 12, 4)


def _ruido(x, y):
    h = (x * 73856093) ^ (y * 19349663)
    return (h ^ (h >> 7)) % 13


def _placa(img, p, face, de=0, ate=None):
    """Pinta a face (só as linhas de 'de' até 'ate', contadas do topo) com relevo e um pouco de ruído."""
    x0, y0, w, h = face
    ate = h if ate is None else ate
    for y in range(y0 + de, y0 + ate):
        for x in range(x0, x0 + w):
            if y == y0 + de or x == x0:
                cor = p["metal"][3]
            elif y == y0 + ate - 1 or x == x0 + w - 1:
                cor = p["metal"][0]
            else:
                n = _ruido(x, y)
                cor = p["metal"][1] if n == 0 else p["metal"][3] if n == 1 else p["metal"][2]
            img.putpixel((x, y), cor)


def _linha(img, cor, face, linha):
    x0, y0, w, _ = face
    for x in range(x0, x0 + w):
        img.putpixel((x, y0 + linha), cor)


def _pontos(img, cor, face, pontos):
    x0, y0, _, _ = face
    for x, y in pontos:
        img.putpixel((x0 + x, y0 + y), cor)


def humanoid(colecao):
    """Capacete (cabeça), peitoral (corpo e braços) e botas (pé da perna) numa textura só."""
    p = COLECOES[colecao]
    escuro = (16, 12, 18, 255)
    img = Image.new("RGBA", (64, 32), T)
    # capacete: elmo fechado, com faixa de detalhe em volta e viseira em T na frente
    for nome, face in CABECA.items():
        _placa(img, p, face)
        if nome not in ("cima", "baixo"):
            _linha(img, p["detalhe"][1], face, 1)
    _pontos(img, escuro, CABECA["frente"], [(x, 4) for x in range(1, 7)] + [(3, 5), (4, 5), (3, 6), (4, 6)])
    _pontos(img, p["metal"][3], CABECA["frente"], [(x, 3) for x in range(1, 7)])  # aba em cima da viseira
    _pontos(img, p["detalhe"][2], CABECA["frente"], [(3, 2), (4, 2)])  # pedra na testa
    # peitoral: placa do peito (mais clara, com borda escura), a pedra no meio e o cinto
    for nome, face in CORPO.items():
        _placa(img, p, face)
    _pontos(img, p["metal"][3], CORPO["frente"], [(x, y) for x in range(2, 6) for y in range(1, 7)])
    _pontos(img, p["metal"][1], CORPO["frente"], [(1, y) for y in range(1, 8)] + [(6, y) for y in range(1, 8)] + [(x, 7) for x in range(1, 7)])
    _pontos(img, p["detalhe"][0], CORPO["frente"], [(3, 3), (4, 3), (3, 4), (4, 4)])
    _pontos(img, p["detalhe"][2], CORPO["frente"], [(3, 3)])
    for face in (CORPO["frente"], CORPO["costas"], CORPO["direita"], CORPO["esquerda"]):
        _linha(img, p["detalhe"][1], face, 9)
        _linha(img, p["detalhe"][0], face, 10)
    # braço: ombreira (mais clara, com a borda de detalhe) e manga até o cotovelo (o jogo espelha o esquerdo)
    for nome, face in BRACO.items():
        _placa(img, p, face, 0, face[3] if nome in ("cima", "baixo") else 8)
        if nome not in ("cima", "baixo"):
            _pontos(img, p["metal"][3], face, [(x, y) for x in range(face[2]) for y in range(0, 3)])
            _linha(img, p["detalhe"][1], face, 3)
            _linha(img, p["metal"][0], face, 7)
    # botas: só a parte de baixo da perna, com o cano de detalhe
    for nome, face in PERNA.items():
        if nome == "cima":
            continue
        if nome == "baixo":
            _placa(img, p, face)
        else:
            _placa(img, p, face, 7, 12)
            _linha(img, p["detalhe"][1], face, 7)
    return img


def humanoid_leggings(colecao):
    """Calça: cintura (parte de baixo do corpo) e as pernas até a canela."""
    p = COLECOES[colecao]
    img = Image.new("RGBA", (64, 32), T)
    for nome, face in CORPO.items():
        if nome == "cima":
            continue
        if nome == "baixo":
            _placa(img, p, face)
        else:
            _placa(img, p, face, 7, 12)
            _linha(img, p["detalhe"][1], face, 7)
    for nome, face in PERNA.items():
        if nome == "baixo":
            continue
        if nome == "cima":
            _placa(img, p, face)
        else:
            _placa(img, p, face, 0, 10)
            _linha(img, p["detalhe"][0], face, 9)
    return img
