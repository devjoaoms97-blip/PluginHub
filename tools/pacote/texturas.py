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


# ---------------------------------------------------------------- fazendas

PERGAMINHO = [(176, 140, 92, 255), (212, 180, 128, 255), (236, 212, 166, 255), (250, 236, 200, 255)]
SACO = [(96, 66, 38, 255), (128, 92, 54, 255), (160, 120, 74, 255), (190, 152, 102, 255)]
VERDE = [(40, 92, 30, 255), (70, 140, 44, 255), (120, 190, 70, 255)]


def escritura():
    """Escritura da fazenda: pergaminho enrolado nas pontas, um campo verde desenhado e o selo vinho de cera."""
    img = nova()
    retangulo(img, 3, 3, 12, 12, PERGAMINHO[2])
    retangulo(img, 3, 3, 3, 12, PERGAMINHO[1])
    retangulo(img, 2, 2, 13, 3, PERGAMINHO[1])  # rolo de cima
    retangulo(img, 2, 12, 13, 13, PERGAMINHO[1])  # rolo de baixo
    px(img, [(x, 2) for x in range(3, 13)], PERGAMINHO[3])
    px(img, [(x, 13) for x in range(3, 13)], PERGAMINHO[0])
    # o terreno desenhado: um quadrado de cerca com plantação
    retangulo(img, 5, 5, 10, 9, VERDE[1])
    px(img, [(x, 5) for x in range(5, 11)] + [(x, 9) for x in range(5, 11)] + [(5, y) for y in range(5, 10)]
       + [(10, y) for y in range(5, 10)], SACO[1])
    px(img, [(7, 6), (7, 7), (8, 7), (8, 8)], VERDE[2])
    # selo de cera com fita dourada
    retangulo(img, 9, 10, 11, 12, VINHO[2])
    px(img, [(10, 11)], VINHO[3])
    px(img, [(8, 12), (8, 13), (12, 13)], OURO[2])
    return contornar(img)


def _saco(img):
    """O saco de estopa (base da Ração e do Adubo), boca amarrada em cima."""
    linhas = {4: (6, 9), 5: (5, 10), 6: (4, 11), 7: (3, 12), 8: (3, 12), 9: (3, 12), 10: (3, 12), 11: (3, 12),
              12: (4, 11), 13: (4, 11), 14: (5, 10)}
    for y, (a, b) in linhas.items():
        for x in range(a, b + 1):
            img.putpixel((x, y), SACO[2] if x > a + 1 else SACO[1])
        img.putpixel((b, y), SACO[1])
    retangulo(img, 6, 2, 9, 3, SACO[3])  # boca
    px(img, [(5, 4), (10, 4), (7, 4), (8, 4)], OURO[1])  # cordão
    px(img, [(5, 9), (7, 11), (9, 8), (10, 12), (6, 13)], SACO[3])  # trama


def racao():
    """Ração: saco de estopa com grãos dourados transbordando e uma faixa vinho."""
    img = nova()
    _saco(img)
    retangulo(img, 3, 9, 12, 9, VINHO[2])
    px(img, [(6, 1), (8, 1), (7, 0), (9, 2), (5, 2)], OURO[2])
    px(img, [(7, 1), (6, 2)], OURO[3])
    px(img, [(6, 11), (8, 12), (9, 11)], OURO[1])
    return contornar(img)


def adubo():
    """Adubo: saco de estopa escuro com um broto verde saindo da boca."""
    img = nova()
    _saco(img)
    for x in range(3, 13):
        for y in range(5, 15):
            c = img.getpixel((x, y))
            if c[3]:
                img.putpixel((x, y), tuple(max(0, v - 30) for v in c[:3]) + (255,))
    px(img, [(8, 3), (8, 2), (8, 1)], VERDE[1])  # caule
    px(img, [(6, 0), (7, 1), (9, 1), (10, 0)], VERDE[2])  # folhas
    px(img, [(7, 0), (9, 0)], VERDE[2])
    retangulo(img, 5, 9, 10, 10, VERDE[0])  # etiqueta
    px(img, [(7, 9), (8, 10)], VERDE[2])
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


# ---------------------------------------------------------------- chaves das caixas (CaixasPlugin, 2026-11-18)

AZUL = [(24, 70, 140, 255), (46, 110, 200, 255), (100, 170, 240, 255), (200, 230, 255, 255)]
VERMELHO = [(110, 14, 14, 255), (170, 30, 26, 255), (220, 70, 54, 255), (255, 170, 150, 255)]


def _chave(metal, gema):
    """Chave antiga na diagonal: argola com a gema da caixa no meio, haste e dentes, no metal dela."""
    img = nova()
    # argola (canto de cima, à esquerda)
    argola = [(3, 1), (4, 1), (5, 1), (2, 2), (6, 2), (1, 3), (7, 3), (1, 4), (7, 4), (1, 5), (7, 5), (2, 6), (6, 6),
              (3, 7), (4, 7), (5, 7)]
    px(img, argola, metal[1])
    px(img, [(3, 1), (2, 2), (1, 3), (1, 4)], metal[2])
    px(img, [(4, 1), (2, 3)], metal[3])
    px(img, [(2, 3), (3, 2), (3, 3), (4, 2), (4, 3), (5, 2), (5, 3), (6, 3), (2, 4), (6, 4), (2, 5), (3, 6), (4, 6), (5, 6),
             (6, 5), (3, 5), (4, 5), (5, 5), (3, 4), (5, 4)], metal[0])
    # a gema
    px(img, [(4, 3), (3, 4), (4, 4), (5, 4), (4, 5)], gema[1])
    px(img, [(4, 3), (3, 4)], gema[2])
    px(img, [(4, 4)], gema[3])
    # haste na diagonal
    for i in range(7):
        px(img, [(7 + i, 7 + i)], metal[1])
        px(img, [(7 + i, 6 + i)], metal[2])
    # dentes
    px(img, [(10, 12), (11, 13), (9, 13), (12, 10), (13, 11)], metal[1])
    px(img, [(9, 12), (12, 9)], metal[0])
    px(img, [(14, 14), (14, 13)], metal[0])
    return contornar(img)


def chave_voto():
    return _chave(OURO, AZUL)


def chave_mina():
    return _chave(ACO, PEDRA)


def chave_boss():
    return _chave(VINHO, VERMELHO)


def chave_tesouro():
    return _chave(OURO, OURO)


# ---------------------------------------------------------------- achados raros das fazendas (2026-11-18)

LARANJA_CENOURA = [(160, 70, 10, 255), (220, 110, 20, 255), (250, 160, 50, 255), (255, 210, 120, 255)]
MARROM = [(46, 30, 22, 255), (70, 46, 32, 255), (100, 68, 46, 255), (140, 100, 70, 255)]
BRANCO = [(190, 196, 206, 255), (222, 226, 232, 255), (246, 248, 250, 255)]
VERMELHO_MACA = [(120, 14, 20, 255), (180, 30, 34, 255), (226, 60, 56, 255), (255, 150, 140, 255)]
ROXO_CHORUS = [(60, 30, 80, 255), (100, 56, 130, 255), (150, 100, 180, 255), (220, 190, 240, 255)]
CIANO_LUZ = [(20, 110, 120, 255), (40, 180, 190, 255), (120, 240, 230, 255), (220, 255, 250, 255)]
BRILHO = (255, 255, 230, 255)


def _faiscas(img, pontos):
    for x, y in pontos:
        px(img, [(x, y)], BRILHO)
        px(img, [(x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)], OURO[3])


def _fita(img, x, y):
    """Fita de prêmio azul (roseta) com as pontas caindo."""
    px(img, [(x, y), (x + 1, y), (x, y + 1), (x + 1, y + 1)], AZUL[2])
    px(img, [(x - 1, y), (x + 2, y + 1), (x, y - 1), (x + 1, y + 2)], AZUL[1])
    px(img, [(x, y + 2), (x, y + 3), (x + 2, y + 3)], AZUL[0])
    px(img, [(x, y)], OURO[3])


def raro_horta():
    """Cenoura Premiada: cenoura dourada na diagonal, folhas verdes e a roseta azul."""
    img = nova()
    for i in range(8):
        x, y = 3 + i, 12 - i
        largura = 1 if i < 2 else 2 if i < 5 else 3
        for d in range(largura):
            px(img, [(x + d, y), (x, y - d)], OURO[1])
        px(img, [(x, y)], OURO[2])
    px(img, [(5, 10), (7, 8), (9, 6)], OURO[0])
    px(img, [(11, 3), (12, 2), (13, 1), (12, 4), (13, 4), (14, 3), (11, 2)], VERDE[1])
    px(img, [(12, 3), (13, 2)], VERDE[2])
    _fita(img, 2, 2)
    _faiscas(img, [(13, 9)])
    return contornar(img)


def raro_galinheiro():
    """Ovo Dourado: ovo de ouro com reflexo e brilho."""
    img = nova()
    linhas = {3: (7, 8), 4: (6, 9), 5: (5, 10), 6: (5, 10), 7: (4, 11), 8: (4, 11), 9: (4, 11), 10: (4, 11), 11: (5, 10), 12: (5, 10), 13: (6, 9)}
    for y, (a, b) in linhas.items():
        for x in range(a, b + 1):
            img.putpixel((x, y), OURO[1] if x < (a + b) / 2 + 1 else OURO[0])
    px(img, [(6, 5), (6, 6), (5, 7), (5, 8)], OURO[3])
    px(img, [(7, 4), (6, 7)], BRILHO)
    _faiscas(img, [(12, 3), (2, 11)])
    return contornar(img)


def raro_pomar():
    """Maçã Reluzente: maçã vermelha brilhante com folha e faíscas."""
    img = nova()
    linhas = {4: (5, 7), 5: (4, 11), 6: (3, 12), 7: (3, 12), 8: (3, 12), 9: (3, 12), 10: (4, 11), 11: (4, 11), 12: (5, 10), 13: (6, 9)}
    for y, (a, b) in linhas.items():
        for x in range(a, b + 1):
            img.putpixel((x, y), VERMELHO_MACA[2] if x < (a + b) / 2 else VERMELHO_MACA[1])
    px(img, [(8, 4), (8, 5)], VERMELHO_MACA[0])
    px(img, [(8, 2), (8, 3)], MARROM[2])
    px(img, [(9, 2), (10, 1), (10, 2), (11, 1)], VERDE[1])
    px(img, [(5, 6), (5, 7), (4, 8)], VERMELHO_MACA[3])
    px(img, [(6, 6)], BRILHO)
    _faiscas(img, [(13, 4), (2, 12)])
    return contornar(img)


def raro_pocilga():
    """Trufa Negra: trufa escura e rugosa, com um pedaço cortado mostrando o miolo marmorizado."""
    img = nova()
    linhas = {4: (6, 9), 5: (4, 11), 6: (3, 12), 7: (3, 12), 8: (2, 13), 9: (2, 13), 10: (3, 12), 11: (3, 12), 12: (4, 11), 13: (6, 9)}
    for y, (a, b) in linhas.items():
        for x in range(a, b + 1):
            img.putpixel((x, y), MARROM[(x * 3 + y * 5) % 3])
    for x, y in [(4, 7), (7, 5), (10, 6), (5, 10), (11, 9), (8, 12), (6, 8)]:
        px(img, [(x, y)], MARROM[3])
    # o corte: miolo claro com veios
    px(img, [(9, 8), (10, 8), (11, 8), (9, 9), (10, 9), (11, 9), (12, 9), (10, 10), (11, 10)], (200, 180, 160, 255))
    px(img, [(10, 9), (11, 8)], MARROM[1])
    _faiscas(img, [(13, 3)])
    return contornar(img)


def raro_trigal():
    """Espiga de Ouro: espiga de trigo dourada na vertical com a palha e as aristas."""
    img = nova()
    px(img, [(7, y) for y in range(9, 15)], OURO[0])
    for y in range(2, 10):
        for x in (6, 8):
            if (y + x) % 2 == 0:
                px(img, [(x, y)], OURO[2])
            else:
                px(img, [(x, y)], OURO[1])
        px(img, [(7, y)], OURO[1])
    px(img, [(5, 3), (5, 5), (5, 7), (9, 4), (9, 6), (9, 8)], OURO[1])
    px(img, [(4, 2), (4, 4), (10, 3), (10, 5), (7, 1), (6, 0)], OURO[3])
    px(img, [(8, 11), (9, 12), (6, 12), (5, 13)], OURO[0])
    _faiscas(img, [(12, 10), (3, 9)])
    return contornar(img)


def raro_curral():
    """Leite Premiado: garrafa de leite com tampa vermelha e a roseta azul de primeiro lugar."""
    img = nova()
    retangulo(img, 6, 2, 9, 3, VERMELHO_MACA[1])
    px(img, [(6, 2)], VERMELHO_MACA[3])
    retangulo(img, 7, 4, 8, 5, BRANCO[1])
    retangulo(img, 5, 6, 10, 14, BRANCO[1])
    retangulo(img, 5, 6, 6, 14, BRANCO[2])
    retangulo(img, 9, 7, 10, 14, BRANCO[0])
    retangulo(img, 5, 9, 10, 10, AZUL[1])
    _fita(img, 10, 10)
    _faiscas(img, [(3, 4)])
    return contornar(img)


def raro_ovelhas():
    """Lã de Ouro: novelo de lã dourada com os fios enrolados."""
    img = nova()
    for y in range(3, 14):
        for x in range(3, 14):
            if (x - 8) ** 2 + (y - 8) ** 2 <= 25:
                img.putpixel((x, y), OURO[1])
    for x, y in [(5, 6), (6, 5), (7, 5), (9, 6), (10, 7), (11, 8), (5, 9), (6, 10), (7, 11), (9, 11), (10, 10), (8, 8), (7, 8), (9, 9)]:
        px(img, [(x, y)], OURO[2])
    for x, y in [(6, 7), (7, 7), (10, 9), (8, 10), (11, 10), (6, 12)]:
        px(img, [(x, y)], OURO[0])
    px(img, [(12, 12), (13, 13), (14, 13), (14, 14)], OURO[2])  # o fio solto
    px(img, [(6, 5)], OURO[3])
    _faiscas(img, [(3, 3)])
    return contornar(img)


def raro_apiario():
    """Favo Real: pedaço de favo hexagonal dourado com geleia real (roxa) no meio."""
    img = nova()
    linhas = {3: (6, 9), 4: (5, 10), 5: (4, 11), 6: (3, 12), 7: (3, 12), 8: (3, 12), 9: (3, 12), 10: (4, 11), 11: (5, 10), 12: (6, 9)}
    for y, (a, b) in linhas.items():
        for x in range(a, b + 1):
            img.putpixel((x, y), OURO[1])
    for x, y in [(5, 5), (8, 5), (11, 7), (5, 8), (8, 8), (11, 10), (5, 11), (8, 11)]:
        px(img, [(x, y), (x + 1, y)], OURO[0])
    px(img, [(7, 7), (8, 7), (7, 8), (8, 9), (7, 9)], ROXO[2])
    px(img, [(7, 7)], ROXO[3])
    px(img, [(6, 3), (5, 4)], OURO[3])
    _faiscas(img, [(13, 3)])
    return contornar(img)


def raro_aboboras():
    """Abóbora Premiada: abóbora laranja gomada com talo verde e a roseta azul."""
    img = nova()
    for y in range(5, 14):
        for x in range(2, 14):
            if ((x - 7.5) / 6) ** 2 + ((y - 9) / 4.6) ** 2 <= 1:
                gomo = (x - 2) % 4
                img.putpixel((x, y), LARANJA[2] if gomo in (1, 2) else LARANJA[1])
    px(img, [(5, y) for y in range(6, 13)] + [(9, y) for y in range(6, 13)], LARANJA[0])
    px(img, [(7, 3), (7, 4), (8, 3)], VERDE[0])
    px(img, [(8, 2), (9, 2)], VERDE[1])
    px(img, [(4, 7), (4, 8)], LARANJA[3])
    _fita(img, 11, 3)
    return contornar(img)


def raro_cogumelos():
    """Cogumelo Luminoso: cogumelo de chapéu ciano brilhante com pintas claras e o pé claro."""
    img = nova()
    linhas = {3: (6, 9), 4: (4, 11), 5: (3, 12), 6: (2, 13), 7: (2, 13), 8: (3, 12)}
    for y, (a, b) in linhas.items():
        for x in range(a, b + 1):
            img.putpixel((x, y), CIANO_LUZ[1] if y > 4 else CIANO_LUZ[2])
    px(img, [(5, 5), (9, 4), (11, 6), (4, 7), (7, 6)], CIANO_LUZ[3])
    px(img, [(x, 8) for x in range(4, 12)], CIANO_LUZ[0])
    retangulo(img, 7, 9, 8, 14, BRANCO[1])
    px(img, [(7, y) for y in range(9, 15)], BRANCO[2])
    px(img, [(6, 14), (9, 14)], BRANCO[0])
    _faiscas(img, [(1, 2), (14, 11)])
    return contornar(img)


def raro_verrugas():
    """Verruga Ancestral: verruga do Nether antiga, vermelho-escura, com cristais de brilho."""
    img = nova()
    base = [(4, 9), (5, 8), (6, 9), (7, 7), (8, 8), (9, 6), (10, 7), (11, 8), (6, 6), (8, 5), (10, 4), (5, 11), (7, 10), (9, 10),
            (11, 11), (8, 12), (6, 12), (10, 12), (7, 13), (9, 13), (8, 11), (4, 10), (12, 10)]
    for x, y in base:
        px(img, [(x, y), (x + 1, y)], VINHO[2])
    for x, y in [(6, 6), (8, 5), (10, 4), (5, 8), (9, 6)]:
        px(img, [(x, y)], VINHO[3])
    for x, y in [(7, 10), (9, 12), (5, 11)]:
        px(img, [(x, y)], VINHO[0])
    px(img, [(8, 2), (8, 3), (7, 3), (9, 3), (8, 4)], CIANO_LUZ[2])  # cristal
    px(img, [(8, 3)], CIANO_LUZ[3])
    _faiscas(img, [(12, 3), (3, 5)])
    return contornar(img)


def raro_chorus():
    """Fruta do Vazio: fruta do coro roxa, estourada, com estrelinhas do End em volta."""
    img = nova()
    linhas = {4: (6, 9), 5: (4, 11), 6: (3, 12), 7: (3, 12), 8: (3, 12), 9: (3, 12), 10: (4, 11), 11: (5, 10), 12: (6, 9)}
    for y, (a, b) in linhas.items():
        for x in range(a, b + 1):
            img.putpixel((x, y), ROXO_CHORUS[1])
    for x, y in [(5, 6), (8, 5), (10, 7), (6, 9), (9, 10), (7, 7), (11, 9), (4, 8)]:
        px(img, [(x, y)], ROXO_CHORUS[2])
    for x, y in [(7, 6), (9, 8), (5, 10)]:
        px(img, [(x, y)], ROXO_CHORUS[3])
    px(img, [(6, 8), (8, 9), (10, 6)], ROXO_CHORUS[0])
    px(img, [(13, 2), (2, 3), (13, 13), (1, 12)], (240, 220, 255, 255))
    px(img, [(12, 2), (14, 2), (13, 1), (13, 3)], ROXO_CHORUS[3])
    return contornar(img)


RAROS = {
    "horta": raro_horta, "galinheiro": raro_galinheiro, "pomar": raro_pomar, "pocilga": raro_pocilga,
    "trigal": raro_trigal, "curral": raro_curral, "ovelhas": raro_ovelhas, "apiario": raro_apiario,
    "aboboras": raro_aboboras, "cogumelos": raro_cogumelos, "verrugas": raro_verrugas, "chorus": raro_chorus,
}


PRODUTOS = {
    "tecido_fino": tecido_fino, "barra_de_aco": barra_de_aco, "essencia_destilada": essencia_destilada,
    "gema_lapidada": gema_lapidada, "cristal_de_energia": cristal_de_energia, "celula_combustivel": celula_combustivel,
    "fragmento_arcano": fragmento_arcano,
    # FazendasPlugin: a Escritura, a Ração e o Adubo
    "escritura": escritura, "racao": racao, "adubo": adubo,
    # CaixasPlugin: as chaves das caixas
    "chave_voto": chave_voto, "chave_mina": chave_mina, "chave_boss": chave_boss, "chave_tesouro": chave_tesouro,
}
# FazendasPlugin: os 12 achados raros, "raro_<fazenda>"
PRODUTOS.update({f"raro_{k}": v for k, v in RAROS.items()})
FRENTES = {
    "tear": frente_tear, "forja": frente_forja, "destilaria": frente_destilaria,
    "lapidadora": frente_lapidadora, "reator": frente_reator,
}
