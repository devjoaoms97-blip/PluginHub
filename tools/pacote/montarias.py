"""
Skins de montaria (cosméticos do CreditosPlugin, usadas pelo MontariaPlugin): modelos 3D de bichos feitos
de caixas, desenhados por código. Usadas pelo gerar.py.

A montaria de verdade continua sendo o cavalo (mesma velocidade, pulo e regras), só que invisível. Em
cima dele vai um item display com o modelo daqui, que vira junto e troca de quadro (pernas, asas) enquanto anda.

Unidades: cada bicho é desenhado em pixels do mundo (16 = 1 bloco), com o chão em y = 0, o centro do
assento em x = z = 0 e a frente pra +z. O modelo do jogo só aceita coordenadas de -16 a 32, então
ele sai encolhido ESCALA vezes (o item display aumenta de novo): o chão fica no -16 do modelo.

Cores: cada caixa tem uma cor, e todas as cores dividem uma textura de paleta (64x64, um quadrado 4x4
por cor, com um pouco de ruído pra parecer pelo/pena).

Quadros: "parado" e "0".."3" (o ciclo andando). As caixas de um grupo ("pe_fe", "pe_fd", "pe_te",
"pe_td" = pé da frente/de trás, esquerdo/direito; "asa_e", "asa_d"; "onda<i>" no tapete) mexem a cada quadro.
"""
import math

from PIL import Image

ESCALA = 3  # o item display aumenta o modelo 3x (MontariaPlugin usa o mesmo número)
ATLAS = 64  # tamanho da textura de paleta
CELULA = 4  # cada cor ocupa 4x4 pixels da paleta


class Caixa:
    def __init__(self, de, ate, cor, grupo=None, rot=None):
        self.de, self.ate, self.cor, self.grupo, self.rot = de, ate, cor, grupo, rot  # rot: (eixo, ângulo, origem)


def espelho(c):
    """A mesma caixa do outro lado (x trocado de sinal), trocando _e por _d no grupo e o sinal do ângulo em z."""
    (x0, y0, z0), (x1, y1, z1) = c.de, c.ate
    grupo = c.grupo
    if grupo and grupo[-1] in "ed":
        grupo = grupo[:-1] + ("d" if grupo[-1] == "e" else "e")
    rot = c.rot
    if rot:
        eixo, ang, (ox, oy, oz) = rot
        rot = (eixo, -ang if eixo in ("z", "y") else ang, (-ox, oy, oz))
    return Caixa((-x1, y0, z0), (-x0, y1, z1), c.cor, grupo, rot)


def par(c):
    return [c, espelho(c)]


def perna(x0, x1, z0, z1, altura, cor, grupo, pe=None):
    """Perna do chão até 'altura' (o grupo gira no topo dela), com a pata de outra cor embaixo."""
    caixas = [Caixa((x0, 0, z0), (x1, altura, z1), cor, grupo)]
    if pe:
        caixas.append(Caixa((x0 - 0.2, 0, z0 - 0.2), (x1 + 0.2, 2, z1 + 0.6), pe, grupo))
    return caixas


def quatro_pernas(x0, x1, zf, zt, larg, altura, cor, pe=None):
    """Pernas da frente (z de zf a zf+larg) e de trás (zt a zt+larg), dos dois lados."""
    caixas = []
    for c in perna(x0, x1, zf, zf + larg, altura, cor, "pe_fe", pe) + perna(x0, x1, zt, zt + larg, altura, cor, "pe_te", pe):
        caixas += par(c)
    return caixas


# ---------------------------------------------------------------- os bichos

def capivara():
    MARROM, ESCURO, CLARO, FOCINHO, PRETO = (126, 88, 54), (96, 66, 42), (150, 110, 72), (84, 58, 40), (26, 20, 18)
    c = [
        Caixa((-8, 4, -13), (8, 16, 11), MARROM),
        Caixa((-7, 3.6, -12), (7, 5, 10), CLARO),
        Caixa((-6, 8, 9), (6, 19, 22), MARROM),
        Caixa((-5, 8, 22), (5, 15.5, 25.5), FOCINHO),
        Caixa((-3, 13, 25.5), (3, 14.5, 25.8), PRETO),
        Caixa((-7.5, 15, -10), (7.5, 16.6, 6), ESCURO),  # pelo mais escuro no lombo
    ]
    c += par(Caixa((6, 15, 16), (6.4, 17, 18), PRETO))  # olhos
    c += par(Caixa((3, 19, 11), (6, 21.5, 13), ESCURO))  # orelhas
    c += quatro_pernas(3, 7, 5, -11, 4, 6, ESCURO)
    return c


def onca():
    OURO, CREME, MANCHA, NARIZ, OLHO = (212, 152, 56), (238, 220, 172), (66, 42, 24), (70, 34, 34), (228, 196, 60)
    c = [
        Caixa((-6, 11, -14), (6, 21, 12), OURO),
        Caixa((-5.5, 10.6, -13), (5.5, 12, 11), CREME),
        Caixa((-6, 15, 10), (6, 26, 21), OURO),
        Caixa((-3.5, 15, 21), (3.5, 20, 25), CREME),
        Caixa((-1.5, 18.5, 25), (1.5, 20, 25.3), NARIZ),
        Caixa((-1.5, 17, -28), (1.5, 20, -14), OURO, rot=("x", -22.5, (0, 18.5, -14))),
        Caixa((-1.6, 16.9, -28.2), (1.6, 20.1, -24), MANCHA, rot=("x", -22.5, (0, 18.5, -14))),
    ]
    c += par(Caixa((2, 22, 21), (4.5, 24, 21.3), OLHO))
    c += par(Caixa((2.6, 22.4, 21.3), (3.6, 23.6, 21.5), (20, 16, 12)))
    c += par(Caixa((2.5, 26, 13), (5.5, 29, 16), OURO))
    c += par(Caixa((3.2, 26.6, 15.9), (4.8, 28.2, 16.2), MANCHA))
    # rosetas nos lados e no lombo
    for z in (-11, -5, 1, 7):
        for y in (13, 17):
            dz = 2 if y == 17 else 0
            c += par(Caixa((6, y, z + dz), (6.3, y + 2, z + dz + 2), MANCHA))
    for x in (-4, 1.5):
        for z in (-10, -3, 4):
            c.append(Caixa((x, 21, z + (1 if x > 0 else 0)), (x + 2, 21.3, z + 2 + (1 if x > 0 else 0)), MANCHA))
    c += quatro_pernas(1.5, 5.5, 6, -12, 4, 12, OURO, CREME)
    return c


def arara():
    VERMELHO, AMARELO, AZUL, BRANCO, BICO, BICO_BAIXO, PERNA = (
        (204, 32, 32), (244, 200, 40), (32, 92, 204), (240, 240, 240), (232, 222, 204), (44, 44, 44), (64, 64, 70))
    c = [
        Caixa((-6, 10, -8), (6, 22, 9), VERMELHO),
        Caixa((-5, 20, 6), (5, 31, 16), VERMELHO),
        Caixa((-2.5, 23, 16), (2.5, 29, 20), BICO),
        Caixa((-2, 20, 18), (2, 23, 20.5), BICO),
        Caixa((-2, 21, 15), (2, 24, 18), BICO_BAIXO),
        Caixa((-2, 12, -30), (2, 14, -8), VERMELHO, rot=("x", -22.5, (0, 13, -8))),
        Caixa((-2.1, 11.9, -30.1), (2.1, 14.1, -22), AZUL, rot=("x", -22.5, (0, 13, -8))),
    ]
    c += par(Caixa((5, 22, 10), (5.3, 28, 15), BRANCO))
    c += par(Caixa((5.3, 25, 12), (5.6, 27, 14), (20, 20, 20)))
    for asa in (Caixa((6, 17, -8), (8, 21, 8), VERMELHO, "asa_e", ("z", 0, (6, 21, 0))),
                Caixa((6, 14, -11), (8, 17, 7), AMARELO, "asa_e", ("z", 0, (6, 21, 0))),
                Caixa((6, 10, -15), (8, 14, 5), AZUL, "asa_e", ("z", 0, (6, 21, 0)))):
        c += par(asa)
    c += par(Caixa((2, 0, -1), (4, 11, 1), PERNA, "pe_fe"))  # duas pernas, uma de cada lado
    c += par(Caixa((1, 0, -1), (5, 1, 4), PERNA, "pe_fe"))  # garras
    return c


def dragao():
    VINHO, VINHO_ESC, OURO, CHIFRE, OLHO, MEMBRANA = (
        (122, 26, 36), (86, 18, 26), (220, 170, 60), (236, 226, 200), (255, 204, 40), (164, 44, 54))
    c = [
        Caixa((-8, 12, -14), (8, 25, 10), VINHO),
        Caixa((-7, 11.6, -13), (7, 13, 9), OURO),
        Caixa((-4, 20, 8), (4, 32, 16), VINHO, rot=("x", 22.5, (0, 20, 12))),
        Caixa((-5, 29, 15), (5, 37, 24), VINHO),
        Caixa((-3.5, 28, 24), (3.5, 33, 31), VINHO),
        Caixa((-3, 27.6, 23), (3, 29, 30.5), OURO),
        Caixa((-4, 15, -28), (4, 22, -14), VINHO),
        Caixa((-2.5, 16, -42), (2.5, 20, -28), VINHO, rot=("x", -22.5, (0, 18, -28))),
        Caixa((-0.5, 19.5, -44), (0.5, 23, -38), OURO, rot=("x", -22.5, (0, 18, -28))),
    ]
    c += par(Caixa((5, 33, 19), (5.3, 35, 21.5), OLHO))
    c += par(Caixa((2, 36, 16), (4, 42, 19), CHIFRE, rot=("x", -22.5, (3, 37, 17.5))))
    for z in (-12, -6, 0, 6):  # espinhos do lombo
        c.append(Caixa((-1, 25, z), (1, 28, z + 2), OURO))
    for z in (-26, -20):
        c.append(Caixa((-1, 22, z), (1, 24.5, z + 2), OURO))
    asa_rot = ("z", 22.5, (8, 27, 0))
    c += par(Caixa((8, 26, 3), (26, 28.5, 7), VINHO_ESC, "asa_e", asa_rot))
    c += par(Caixa((8, 26.6, -13), (30, 27.6, 4), MEMBRANA, "asa_e", asa_rot))
    c += par(Caixa((14, 26.2, -12), (15.5, 28, 4), VINHO_ESC, "asa_e", asa_rot))
    c += par(Caixa((21, 26.2, -10), (22.5, 28, 4), VINHO_ESC, "asa_e", asa_rot))
    c += quatro_pernas(3, 8, 3, -13, 6, 13, VINHO_ESC, CHIFRE)
    return c


def lobo_de_gelo():
    BRANCO, AZULADO, JUBA, GELO, GELO_CLARO, OLHO, PRETO = (
        (206, 228, 242), (140, 180, 212), (232, 244, 252), (80, 210, 230), (200, 250, 250), (120, 240, 255), (30, 34, 44))
    c = [
        Caixa((-5, 11, -13), (5, 21, 11), BRANCO),
        Caixa((-4.5, 10.6, -12), (4.5, 12, 10), AZULADO),
        Caixa((-6, 11, 4), (6, 23, 12), JUBA),
        Caixa((-4.5, 15, 11), (4.5, 24, 19), BRANCO),
        Caixa((-2.5, 15, 19), (2.5, 19, 25), AZULADO),
        Caixa((-1.5, 17.5, 25), (1.5, 19, 25.3), PRETO),
        Caixa((-2, 16, -24), (2, 20, -13), JUBA, rot=("x", 22.5, (0, 18, -13))),
        Caixa((-2.1, 15.9, -24.1), (2.1, 20.1, -21), GELO_CLARO, rot=("x", 22.5, (0, 18, -13))),
    ]
    c += par(Caixa((1.5, 20.5, 19), (3.5, 22, 19.3), OLHO))
    c += par(Caixa((1.5, 24, 13), (4, 28, 15.5), AZULADO))
    for z in (-9, -3, 3):  # cristais de gelo no lombo
        c.append(Caixa((-1, 21, z), (1, 26, z + 2), GELO))
        c += par(Caixa((1.5, 20.5, z + 1), (3, 24, z + 2.5), GELO_CLARO, rot=("z", -22.5, (2, 21, z + 1.5))))
    c += quatro_pernas(1, 4.5, 5, -11, 4, 12, AZULADO, GELO)
    return c


def grifo():
    LEAO, LEAO_ESC, PENA, BICO, ASA, PONTA, PRETO = (
        (192, 142, 72), (150, 104, 50), (242, 238, 226), (242, 192, 42), (124, 82, 46), (240, 236, 226), (24, 20, 18))
    c = [
        Caixa((-6, 12, -14), (6, 23, 10), LEAO),
        Caixa((-6.3, 12, 2), (6.3, 24, 11), PENA),
        Caixa((-4.5, 22, 8), (4.5, 32, 17), PENA),
        Caixa((-2, 24, 17), (2, 29, 21), BICO),
        Caixa((-1.5, 22, 19), (1.5, 25, 21), BICO),
        Caixa((-1, 18, -28), (1, 20, -14), LEAO, rot=("x", -22.5, (0, 19, -14))),
        Caixa((-1.8, 17.2, -30), (1.8, 20.8, -26), LEAO_ESC, rot=("x", -22.5, (0, 19, -14))),
    ]
    c += par(Caixa((4.5, 27, 13), (4.8, 29, 15), PRETO))
    c += par(Caixa((2, 31, 9), (3.5, 34, 11), PENA))  # tufos
    asa_rot = ("z", 0, (6, 24, 0))
    c += par(Caixa((6, 16, -10), (9, 24, 8), ASA, "asa_e", asa_rot))
    c += par(Caixa((6.3, 14, -16), (8.7, 20, -8), PONTA, "asa_e", asa_rot))
    frente = perna(2, 5, 4, 8, 13, BICO, "pe_fe")
    tras = perna(2, 5, -12, -8, 13, LEAO, "pe_te", LEAO_ESC)
    for p in frente + tras:
        c += par(p)
    return c


def tapete():
    VINHO, OURO, CARMESIM, OURO_CLARO = (128, 34, 44), (210, 156, 36), (156, 56, 84), (246, 206, 78)
    c = []
    n = 8
    for i in range(n):
        z0 = -20 + i * 5
        g = f"onda{i}"
        c.append(Caixa((-11, 6, z0), (11, 7.5, z0 + 5), OURO if i in (0, n - 1) else VINHO, g))
        c += par(Caixa((11, 6, z0), (13, 7.5, z0 + 5), OURO, g))
        if 2 <= i <= 5:  # losango do meio
            larg = 3 + 3 * min(i - 2, 5 - i)
            c.append(Caixa((-larg, 7.5, z0 + 1), (larg, 7.8, z0 + 4), CARMESIM, g))
            c.append(Caixa((-1, 7.6, z0 + 2), (1, 7.9, z0 + 3), OURO_CLARO, g))
    for x in (-10, -5, 0, 5, 10):  # franjas
        c.append(Caixa((x - 0.5, 4.5, 20), (x + 0.5, 7, 22), OURO, f"onda{n - 1}"))
        c.append(Caixa((x - 0.5, 4.5, -22), (x + 0.5, 7, -20), OURO, "onda0"))
    # flutuando: sobe tudo até o assento (o cavalo, com escala 0,5, senta o jogador a ~10,5 px do chão)
    return [Caixa((a[0], a[1] + 3, a[2]), (b[0], b[1] + 3, b[2]), k.cor, k.grupo, k.rot) for k in c for a, b in [(k.de, k.ate)]]


# id -> (desenho, tipo de animação)
BICHOS = {
    "arara": (arara, "ave"),
    "capivara": (capivara, "quadrupede"),
    "onca": (onca, "quadrupede"),
    "dragao": (dragao, "ave"),
    "lobo_de_gelo": (lobo_de_gelo, "quadrupede"),
    "grifo": (grifo, "ave"),
    "tapete": (tapete, "onda"),
}
QUADROS = ("parado", "0", "1", "2", "3")


def _animar(caixas, tipo, quadro):
    """As caixas do quadro: as dos grupos ganham a rotação do momento (perna ±22,5°, asa batendo, onda)."""
    perna = {"parado": 0, "0": 22.5, "1": 0, "2": -22.5, "3": 0}[quadro]
    asa = {"parado": 0, "0": 22.5, "1": 0, "2": -22.5, "3": 0}[quadro]
    fase = {"parado": 0, "0": 0, "1": 1, "2": 2, "3": 3}[quadro]
    saida = []
    for c in caixas:
        g = c.grupo
        if not g:
            saida.append(c)
            continue
        (x0, y0, z0), (x1, y1, z1) = c.de, c.ate
        if g.startswith("pe_"):
            ang = perna if g in ("pe_fe", "pe_td") else -perna
            # a pata vai junto com a perna: gira no topo da perna (altura do grupo inteiro)
            topo = max(cc.ate[1] for cc in caixas if cc.grupo == g and abs(cc.de[0] - x0) < 1)
            meio_z = (z0 + z1) / 2
            saida.append(Caixa(c.de, c.ate, c.cor, None, ("x", ang, ((x0 + x1) / 2, topo, meio_z)) if ang else None))
        elif g.startswith("asa_"):
            eixo, base, origem = c.rot
            ang = base + (asa if g == "asa_e" else -asa)
            ang = max(-45, min(45, ang))
            saida.append(Caixa(c.de, c.ate, c.cor, None, (eixo, ang, origem) if ang else None))
        elif g.startswith("onda"):
            i = int(g[4:])
            dy = 0.7 * math.sin(2 * math.pi * (i / 8 + fase / 4))
            saida.append(Caixa((x0, y0 + dy, z0), (x1, y1 + dy, z1), c.cor))
        else:
            saida.append(c)
    return saida


# ---------------------------------------------------------------- paleta e modelo

def _ruido(x, y):
    h = (x * 73856093) ^ (y * 19349663)
    return (h ^ (h >> 7)) % 7 - 3


def paleta():
    """Todas as cores dos bichos, cada uma num quadrado 4x4 da textura. Devolve (imagem, cor -> índice)."""
    cores = []
    for desenho, _ in BICHOS.values():
        for c in desenho():
            if c.cor not in cores:
                cores.append(c.cor)
    por_linha = ATLAS // CELULA
    assert len(cores) <= por_linha * por_linha, "cores demais pra paleta"
    img = Image.new("RGBA", (ATLAS, ATLAS), (0, 0, 0, 0))
    for i, (r, g, b) in enumerate(cores):
        cx, cy = i % por_linha * CELULA, i // por_linha * CELULA
        for dx in range(CELULA):
            for dy in range(CELULA):
                n = _ruido(cx + dx, cy + dy) * 4
                img.putpixel((cx + dx, cy + dy), (max(0, min(255, r + n)), max(0, min(255, g + n)), max(0, min(255, b + n)), 255))
    return img, {cor: i for i, cor in enumerate(cores)}


def _m(p):
    """Pixel do mundo -> coordenada do modelo (encolhida ESCALA vezes, chão no -16, centro no 8)."""
    x, y, z = p
    return [round(8 + x / ESCALA, 4), round(-16 + y / ESCALA, 4), round(8 + z / ESCALA, 4)]


def modelo(id_, quadro, indices):
    desenho, tipo = BICHOS[id_]
    caixas = _animar(desenho(), tipo, quadro)
    por_linha = ATLAS // CELULA
    unidade = 16 / (ATLAS // CELULA)  # tamanho de uma célula em coordenadas de uv (0..16)
    elementos = []
    for c in caixas:
        i = indices[c.cor]
        u, v = i % por_linha * unidade, i // por_linha * unidade
        uv = [u + 0.1, v + 0.1, u + unidade - 0.1, v + unidade - 0.1]
        e = {"from": _m(c.de), "to": _m(c.ate),
             "faces": {f: {"uv": uv, "texture": "#0"} for f in ("north", "east", "south", "west", "up", "down")}}
        if c.rot and c.rot[1]:
            eixo, ang, origem = c.rot
            e["rotation"] = {"angle": ang, "axis": eixo, "origin": _m(origem)}
        elementos.append(e)
    # no inventário (ícone da loja): o bicho inteiro, centralizado
    xs = [v for e in elementos for v in (e["from"][0], e["to"][0])]
    ys = [v for e in elementos for v in (e["from"][1], e["to"][1])]
    zs = [v for e in elementos for v in (e["from"][2], e["to"][2])]
    tamanho = max(max(xs) - min(xs), max(ys) - min(ys), max(zs) - min(zs))
    s = round(min(1.0, 15 / tamanho), 3)
    centro_y = (max(ys) + min(ys)) / 2
    gui = {"rotation": [30, 225, 0], "translation": [0, round((8 - centro_y) * s, 2), 0], "scale": [s, s, s]}
    return {"textures": {"0": "servidor:item/montaria_paleta", "particle": "servidor:item/montaria_paleta"},
            "elements": elementos, "display": {"gui": gui}}
