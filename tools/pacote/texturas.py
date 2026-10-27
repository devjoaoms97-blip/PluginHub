"""
Texturas do servidor (16x16), desenhadas por código pra combinar com o spawn: pedra cinza, vinho
(tijolo do nether vermelho), carmesim, cerejeira e dourado. Usadas pelo gerar.py.

Cada função devolve uma imagem RGBA 16x16. Paleta fixa no topo: mudar uma cor aqui muda o pacote
inteiro.
"""
from PIL import Image

# ---------------------------------------------------------------- paleta
T = (0, 0, 0, 0)
CONTORNO = (34, 26, 30, 255)
PEDRA = [(92, 92, 96, 255), (112, 112, 116, 255), (132, 132, 136, 255), (152, 152, 156, 255)]
VINHO = [(64, 14, 22, 255), (96, 22, 32, 255), (128, 34, 44, 255), (160, 52, 62, 255)]
CARMESIM = [(92, 24, 44, 255), (124, 36, 62, 255), (156, 56, 84, 255)]
CEREJA = [(214, 120, 160, 255), (236, 160, 192, 255), (252, 206, 226, 255)]
OURO = [(150, 102, 20, 255), (210, 156, 36, 255), (246, 206, 78, 255), (255, 240, 160, 255)]
ACO = [(70, 82, 98, 255), (104, 118, 136, 255), (148, 162, 180, 255), (200, 212, 226, 255)]
CIANO = [(16, 92, 112, 255), (28, 150, 170, 255), (80, 210, 220, 255), (200, 250, 250, 255)]
VIDRO = (180, 220, 230, 160)
LARANJA = [(150, 50, 10, 255), (220, 100, 20, 255), (255, 170, 40, 255), (255, 230, 140, 255)]
ROXO = [(70, 30, 100, 255), (110, 50, 150, 255), (160, 90, 200, 255), (210, 160, 240, 255)]


def nova():
    return Image.new("RGBA", (16, 16), T)


def px(img, pontos, cor):
    for x, y in pontos:
        if 0 <= x < 16 and 0 <= y < 16:
            img.putpixel((x, y), cor)


def retangulo(img, x0, y0, x1, y1, cor):
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            img.putpixel((x, y), cor)


def contornar(img):
    """Contorno escuro em volta do desenho (estilo dos itens do Minecraft)."""
    base = img.copy()
    for x in range(16):
        for y in range(16):
            if base.getpixel((x, y))[3] != 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < 16 and 0 <= ny < 16 and base.getpixel((nx, ny))[3] != 0 and base.getpixel((nx, ny)) != CONTORNO:
                    img.putpixel((x, y), CONTORNO)
                    break
    return img


# ---------------------------------------------------------------- produtos

def tecido_fino():
    """Rolo de tecido vinho com faixa dourada, visto de lado (diagonal)."""
    img = nova()
    for i in range(10):  # corpo do rolo em diagonal
        x, y = 3 + i, 11 - i
        for w in range(-2, 3):
            cor = VINHO[3] if w == -2 else VINHO[2] if w == -1 else VINHO[1] if w <= 1 else VINHO[0]
            px(img, [(x + w, y + w)], cor)
    for i in range(10):  # faixa dourada no meio
        px(img, [(3 + i, 11 - i)], OURO[2] if i % 2 == 0 else OURO[1])
    # ponta do rolo (espiral)
    px(img, [(12, 3), (13, 2), (13, 4), (14, 3)], VINHO[3])
    px(img, [(13, 3)], VINHO[0])
    # pedaço desenrolado embaixo
    px(img, [(1, 12), (2, 13), (3, 13), (4, 14), (2, 12)], VINHO[2])
    return contornar(img)


def barra_de_aco():
    """Lingote de aço azulado com brilho e um rebite vinho (marca do servidor)."""
    img = nova()
    # topo do lingote (trapézio) e lateral
    retangulo(img, 3, 6, 12, 7, ACO[3])
    retangulo(img, 2, 8, 13, 10, ACO[2])
    retangulo(img, 1, 11, 14, 12, ACO[1])
    retangulo(img, 1, 13, 14, 13, ACO[0])
    px(img, [(4, 6), (5, 6), (3, 8), (2, 11)], (240, 248, 255, 255))  # brilho
    px(img, [(11, 9), (12, 9)], VINHO[2])  # selo
    px(img, [(11, 10)], VINHO[1])
    return contornar(img)


def essencia_destilada():
    """Frasco de vidro com essência rosa (cerejeira) e rolha carmesim."""
    img = nova()
    retangulo(img, 6, 1, 9, 2, CARMESIM[1])  # rolha
    retangulo(img, 6, 3, 9, 4, VIDRO)  # gargalo
    for y in range(5, 15):  # corpo arredondado
        meio = 7.5
        largura = 5 if 7 <= y <= 12 else 4 if y in (6, 13) else 3
        for x in range(16):
            if abs(x - meio) <= largura:
                img.putpixel((x, y), CEREJA[0] if y >= 8 else VIDRO)
    for y in range(8, 15):
        px(img, [(4, y)], CEREJA[1])
    px(img, [(5, 9), (5, 10)], CEREJA[2])  # brilho
    px(img, [(9, 11), (10, 12)], CARMESIM[2])  # bolhas
    return contornar(img)


def gema_lapidada():
    """Gema facetada roxa e rosa, com reflexos."""
    img = nova()
    linhas = {3: (5, 10), 4: (4, 11), 5: (3, 12), 6: (2, 13), 7: (2, 13), 8: (3, 12), 9: (4, 11), 10: (5, 10), 11: (6, 9),
              12: (7, 8)}
    for y, (a, b) in linhas.items():
        for x in range(a, b + 1):
            cor = ROXO[2] if y <= 6 else ROXO[1]
            if (x + y) % 4 == 0:
                cor = ROXO[3] if y <= 6 else ROXO[0]
            img.putpixel((x, y), cor)
    px(img, [(5, 4), (6, 4), (4, 5)], (255, 230, 250, 255))
    px(img, [(8, 6), (9, 7), (10, 6)], CEREJA[1])
    return contornar(img)


def cristal_de_energia():
    """Cristal alto ciano brilhando, com base dourada."""
    img = nova()
    for y in range(1, 13):
        largura = min(y, 13 - y, 3)
        for x in range(8 - largura, 8 + largura):
            img.putpixel((x, y), CIANO[2] if x < 8 else CIANO[1])
    px(img, [(7, 2), (6, 4), (7, 5), (6, 7)], CIANO[3])
    retangulo(img, 4, 13, 11, 14, OURO[1])
    px(img, [(4, 13), (5, 13)], OURO[3])
    px(img, [(3, 6), (12, 9), (2, 10), (13, 4)], CIANO[3])  # faíscas
    return contornar(img)


def fragmento_arcano():
    """Fragmento Arcano (cai raramente nas minas): lasca de cristal roxo torta, com núcleo carmesim e brilho dourado."""
    img = nova()
    # lasca inclinada, mais larga embaixo
    linhas = {2: (9, 10), 3: (8, 11), 4: (8, 11), 5: (7, 11), 6: (6, 11), 7: (6, 10), 8: (5, 10), 9: (5, 10),
              10: (4, 9), 11: (4, 9), 12: (5, 8), 13: (6, 7)}
    for y, (a, b) in linhas.items():
        for x in range(a, b + 1):
            cor = ROXO[2] if x - a < (b - a + 1) / 2 else ROXO[1]
            if x == a:
                cor = ROXO[3]
            img.putpixel((x, y), cor)
    px(img, [(7, 7), (7, 8), (6, 9), (7, 9), (6, 10)], CARMESIM[2])  # núcleo
    px(img, [(9, 3), (8, 5), (7, 6)], (250, 230, 255, 255))  # reflexo
    px(img, [(13, 3), (3, 6), (12, 11)], OURO[3])  # faíscas
    px(img, [(13, 2), (13, 4), (12, 3), (14, 3)], OURO[2])
    return contornar(img)


def celula_combustivel():
    """Célula de combustível: cilindro escuro com faixa vinho e chama laranja."""
    img = nova()
    retangulo(img, 5, 1, 10, 2, PEDRA[3])  # tampa
    retangulo(img, 4, 3, 11, 14, PEDRA[0])
    retangulo(img, 4, 3, 5, 14, PEDRA[1])
    retangulo(img, 4, 5, 11, 5, VINHO[2])
    retangulo(img, 4, 12, 11, 12, VINHO[2])
    # chama
    px(img, [(8, 7), (7, 8), (8, 8), (9, 8), (7, 9), (8, 9), (9, 9), (7, 10), (8, 10), (9, 10)], LARANJA[1])
    px(img, [(8, 8), (8, 9), (8, 10)], LARANJA[2])
    px(img, [(8, 9)], LARANJA[3])
    return contornar(img)


# ---------------------------------------------------------------- máquinas (faces de bloco, sem transparência)

def _moldura(img, cor_borda=None):
    """Chapa de pedra com moldura vinho e rebites dourados nos cantos (todas as máquinas)."""
    for x in range(16):
        for y in range(16):
            # chapa lisa com pouco ruído (sempre o mesmo desenho)
            h = (x * 73856093) ^ (y * 19349663)
            h = (h ^ (h >> 7)) % 11
            img.putpixel((x, y), PEDRA[1] if h == 0 else PEDRA[3] if h == 1 else PEDRA[2])
    borda = cor_borda or VINHO
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            img.putpixel((x, y), borda[1])
        for (x, y) in ((i, 1), (1, i)):
            img.putpixel((x, y), borda[2])
        for (x, y) in ((i, 14), (14, i)):
            img.putpixel((x, y), borda[0])
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        img.putpixel((x, y), OURO[2])
    return img


def maquina_lado():
    img = _moldura(Image.new("RGBA", (16, 16)))
    for y in (5, 7, 9):  # grelha de ventilação
        retangulo(img, 4, y, 11, y, PEDRA[0])
    return img


def maquina_topo():
    img = _moldura(Image.new("RGBA", (16, 16)))
    retangulo(img, 5, 5, 10, 10, PEDRA[0])  # escotilha
    retangulo(img, 6, 6, 9, 9, PEDRA[3])
    px(img, [(7, 7), (8, 8)], OURO[1])
    return img


def frente_tear():
    img = _moldura(Image.new("RGBA", (16, 16)))
    for x in range(4, 12):  # fios
        retangulo(img, x, 3, x, 12, CEREJA[0] if x % 2 else VINHO[2])
    retangulo(img, 3, 7, 12, 8, (120, 82, 48, 255))  # pente de madeira
    return img


def frente_forja():
    img = _moldura(Image.new("RGBA", (16, 16)))
    retangulo(img, 4, 6, 11, 12, (30, 20, 20, 255))  # boca
    retangulo(img, 5, 9, 10, 12, LARANJA[1])
    retangulo(img, 6, 10, 9, 12, LARANJA[2])
    px(img, [(7, 11), (8, 11)], LARANJA[3])
    retangulo(img, 4, 4, 11, 5, VINHO[1])  # verga
    return img


def frente_destilaria():
    img = _moldura(Image.new("RGBA", (16, 16)))
    retangulo(img, 5, 3, 10, 12, (150, 200, 210, 255))  # tanque de vidro
    retangulo(img, 5, 7, 10, 12, CEREJA[0])
    retangulo(img, 6, 8, 6, 11, CEREJA[2])
    retangulo(img, 4, 3, 4, 12, PEDRA[0])
    retangulo(img, 11, 3, 11, 12, PEDRA[0])
    return img


def frente_lapidadora():
    img = _moldura(Image.new("RGBA", (16, 16)))
    for x in range(16):  # disco de corte
        for y in range(16):
            if (x - 7.5) ** 2 + (y - 7.5) ** 2 <= 16:
                img.putpixel((x, y), ACO[2] if (x + y) % 3 else ACO[3])
    retangulo(img, 7, 7, 8, 8, ROXO[2])
    px(img, [(7, 7)], ROXO[3])
    return img


def frente_reator():
    img = _moldura(Image.new("RGBA", (16, 16)), CARMESIM)
    for x in range(16):  # núcleo brilhando
        for y in range(16):
            d = (x - 7.5) ** 2 + (y - 7.5) ** 2
            if d <= 20:
                img.putpixel((x, y), CIANO[1])
            if d <= 9:
                img.putpixel((x, y), CIANO[2])
            if d <= 2:
                img.putpixel((x, y), CIANO[3])
    return img


PRODUTOS = {
    "tecido_fino": tecido_fino, "barra_de_aco": barra_de_aco, "essencia_destilada": essencia_destilada,
    "gema_lapidada": gema_lapidada, "cristal_de_energia": cristal_de_energia, "celula_combustivel": celula_combustivel,
    "fragmento_arcano": fragmento_arcano,
}
FRENTES = {
    "tear": frente_tear, "forja": frente_forja, "destilaria": frente_destilaria,
    "lapidadora": frente_lapidadora, "reator": frente_reator,
}
