"""
Modelos 3D dos bosses (2026-11-17), desenhados por código e salvos como .bbmodel do Blockbench, que o
FreeMinecraftModels carrega (plugins/FreeMinecraftModels/models) e põe em cima do mob do boss, que fica
invisível (BossPlugin, "fmm-modelo" de cada boss). O Wither fica como é.

Cada modelo é feito de caixas com uma cor cada; as cores dividem uma textura de paleta (64x64, um quadrado
4x4 por cor, com um pouco de ruído). Os ossos ("ossos") são os grupos do Blockbench: a cabeça começa com
"h_" (o FMM gira ela pra onde o mob olha). As animações têm os nomes que o FMM toca sozinho: idle, walk,
attack, damaged, death e spawn.

Unidades: pixels (16 = 1 bloco), chão em y = 0, frente pro -z (norte), no tamanho de um mob comum. O
BossPlugin aumenta o modelo junto com o boss (setScaleModifier com a escala do boss).

Uso: python bosses.py [pasta]   (padrão: tools/pacote/bosses)
"""
import base64
import io
import json
import random
import sys
import uuid
from pathlib import Path

from PIL import Image

ATLAS, CELULA = 64, 4
NS = uuid.UUID("6b1f3c1e-0b0a-4c2e-9a51-0f2d3b6e7a10")


def uid(*partes):
    return str(uuid.uuid5(NS, "/".join(str(p) for p in partes)))


class Modelo:
    def __init__(self, nome, cores):
        self.nome = nome
        self.cores = cores           # nome -> (r, g, b)
        self.indice = {c: i for i, c in enumerate(cores)}
        self.ossos = {}              # nome -> dict(origem, pai, filhos)
        self.ordem = []
        self.elementos = []
        self.animacoes = []

    def osso(self, nome, origem, pai=None):
        self.ossos[nome] = {"origem": list(origem), "pai": pai, "filhos": []}
        self.ordem.append(nome)
        if pai:
            self.ossos[pai]["filhos"].append(("osso", nome))
        return nome

    def caixa(self, osso, de, ate, cor, rot=None, origem=None, nome=None):
        """rot: (eixo, graus) em volta de origem (padrão: o centro da caixa)."""
        n = len(self.elementos)
        centro = [(de[i] + ate[i]) / 2 for i in range(3)]
        e = {"nome": nome or f"{osso}_{n}", "de": list(de), "ate": list(ate), "cor": cor,
             "origem": list(origem) if origem else centro, "rot": rot, "uuid": uid(self.nome, "e", n)}
        self.elementos.append(e)
        self.ossos[osso]["filhos"].append(("elemento", e["uuid"]))
        return e

    def espelhar(self, osso, de, ate, cor, rot=None):
        """A mesma caixa dos dois lados do x."""
        self.caixa(osso, de, ate, cor, rot)
        r2 = (rot[0], -rot[1]) if rot and rot[0] in ("y", "z") else rot
        self.caixa(osso, (-ate[0], de[1], de[2]), (-de[0], ate[1], ate[2]), cor, r2)

    def anim(self, nome, duracao, quadros, loop=True):
        """quadros: {osso: {"rotation"|"position": [(tempo, (x, y, z)), ...]}}"""
        self.animacoes.append((nome, duracao, quadros, loop))

    # ------------------------------------------------------------- exportação
    def textura(self):
        img = Image.new("RGBA", (ATLAS, ATLAS), (0, 0, 0, 0))
        rnd = random.Random(self.nome)
        por_linha = ATLAS // CELULA
        for i, (nome, cor) in enumerate(self.cores.items()):
            cx, cy = (i % por_linha) * CELULA, (i // por_linha) * CELULA
            for dx in range(CELULA):
                for dy in range(CELULA):
                    f = 1 + rnd.uniform(-0.07, 0.07)
                    img.putpixel((cx + dx, cy + dy), tuple(max(0, min(255, int(c * f))) for c in cor) + (255,))
        buf = io.BytesIO()
        img.save(buf, "PNG")
        return "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()

    def uv(self, cor):
        i = self.indice[cor]
        por_linha = ATLAS // CELULA
        u, v = (i % por_linha) * CELULA, (i // por_linha) * CELULA
        return [u + 0.5, v + 0.5, u + CELULA - 0.5, v + CELULA - 0.5]

    def json(self):
        elementos = []
        for e in self.elementos:
            faces = {f: {"uv": self.uv(e["cor"]), "texture": 0} for f in ("north", "east", "south", "west", "up", "down")}
            el = {"name": e["nome"], "rescale": False, "locked": False, "from": e["de"], "to": e["ate"], "autouv": 0,
                  "color": 0, "origin": e["origem"], "faces": faces, "type": "cube", "uuid": e["uuid"]}
            if e["rot"]:
                eixo, graus = e["rot"]
                el["rotation"] = [graus if eixo == "x" else 0, graus if eixo == "y" else 0, graus if eixo == "z" else 0]
            elementos.append(el)

        def grupo(nome):
            o = self.ossos[nome]
            filhos = [grupo(f) if tipo == "osso" else f for tipo, f in o["filhos"]]
            return {"name": nome, "origin": o["origem"], "color": 0, "uuid": uid(self.nome, "o", nome), "export": True,
                    "isOpen": True, "locked": False, "visibility": True, "autouv": 0, "children": filhos}

        outliner = [grupo(n) for n in self.ordem if self.ossos[n]["pai"] is None]
        animacoes = []
        for nome, duracao, quadros, loop in self.animacoes:
            animadores = {}
            for osso, canais in quadros.items():
                kf = []
                for canal, pontos in canais.items():
                    for t, (x, y, z) in pontos:
                        kf.append({"channel": canal, "data_points": [{"x": x, "y": y, "z": z}],
                                   "uuid": uid(self.nome, nome, osso, canal, t), "time": t, "color": -1, "interpolation": "linear"})
                animadores[uid(self.nome, "o", osso)] = {"name": osso, "type": "bone", "keyframes": kf}
            animacoes.append({"uuid": uid(self.nome, "a", nome), "name": nome, "loop": "loop" if loop else "once",
                              "override": False, "length": duracao, "snapping": 24, "selected": False,
                              "anim_time_update": "", "blend_weight": "", "start_delay": "", "loop_delay": "",
                              "animators": animadores})
        return {
            "meta": {"format_version": "4.0", "model_format": "free", "box_uv": False},
            "name": self.nome, "model_identifier": "", "visible_box": [2, 3, 0],
            "variable_placeholders": "", "variable_placeholder_buttons": [], "timeline_setups": [],
            "resolution": {"width": ATLAS, "height": ATLAS},
            "elements": elementos, "outliner": outliner,
            "textures": [{"path": "", "name": f"{self.nome}.png", "folder": "", "namespace": "", "id": "0",
                          "particle": False, "render_mode": "default", "visible": True, "mode": "bitmap",
                          "saved": True, "uuid": uid(self.nome, "t"), "source": self.textura()}],
            "animations": animacoes,
        }


# ------------------------------------------------------------------ animações comuns

def balanco(ossos_pares, angulo, duracao):
    """Andar: os pares (esquerdo, direito) balançam em x, em oposição."""
    q = {}
    for esq, dir_ in ossos_pares:
        q[esq] = {"rotation": [(0, (angulo, 0, 0)), (duracao / 2, (-angulo, 0, 0)), (duracao, (angulo, 0, 0))]}
        q[dir_] = {"rotation": [(0, (-angulo, 0, 0)), (duracao / 2, (angulo, 0, 0)), (duracao, (-angulo, 0, 0))]}
    return q


def respirar(osso, altura, duracao):
    return {osso: {"position": [(0, (0, 0, 0)), (duracao / 2, (0, altura, 0)), (duracao, (0, 0, 0))]}}


def cair(osso):
    """Morte: tomba pra trás e afunda."""
    return {osso: {"rotation": [(0, (0, 0, 0)), (0.8, (-85, 0, 0))], "position": [(0, (0, 0, 0)), (0.8, (0, -6, 0)), (1.5, (0, -14, 0))]}}


def surgir(osso):
    """Spawn: sobe do chão."""
    return {osso: {"position": [(0, (0, -30, 0)), (1.0, (0, 0, 0))]}}


def tremer(osso):
    return {osso: {"rotation": [(0, (0, 0, 0)), (0.1, (6, 0, 4)), (0.2, (-4, 0, -3)), (0.3, (0, 0, 0))]}}


def juntar(*qs):
    r = {}
    for q in qs:
        for osso, canais in q.items():
            r.setdefault(osso, {}).update(canais)
    return r


# ------------------------------------------------------------------ humanoide base

def humanoide(m, pele, roupa, calca, alt=1.0, larg=1.0):
    """Corpo de gente (zumbi, esqueleto, bruxa...): raiz, tronco, cabeça, braços e pernas. Devolve as alturas."""
    a = lambda v: v * alt
    w = lambda v: v * larg
    m.osso("raiz", (0, 0, 0))
    m.osso("tronco", (0, a(12), 0), "raiz")
    m.osso("h_cabeca", (0, a(24), 0), "tronco")
    m.osso("braco_e", (w(5), a(22), 0), "tronco")
    m.osso("braco_d", (w(-5), a(22), 0), "tronco")
    m.osso("perna_e", (w(2), a(12), 0), "raiz")
    m.osso("perna_d", (w(-2), a(12), 0), "raiz")
    m.caixa("tronco", (w(-4), a(12), -2), (w(4), a(24), 2), roupa)
    m.caixa("h_cabeca", (-4, a(24), -4), (4, a(24) + 8, 4), pele)
    m.caixa("braco_e", (w(4), a(12), -2), (w(8), a(24), 2), pele)
    m.caixa("braco_d", (w(-8), a(12), -2), (w(-4), a(24), 2), pele)
    m.caixa("perna_e", (0.1, 0, -2), (w(4), a(12), 2), calca)
    m.caixa("perna_d", (w(-4), 0, -2), (-0.1, a(12), 2), calca)
    return a(24)


def anims_humanoide(m, ataque_bracos=("braco_d",), bracos_frente=False):
    base_bracos = (-80, 0, 0) if bracos_frente else (0, 0, 0)
    idle = juntar(respirar("tronco", 0.4, 2.0),
                  {b: {"rotation": [(0, base_bracos), (1.0, (base_bracos[0] - 4, 0, 0)), (2.0, base_bracos)]} for b in ("braco_e", "braco_d")})
    m.anim("idle", 2.0, idle)
    walk = juntar(balanco([("perna_e", "perna_d")], 30, 0.8),
                  {"braco_e": {"rotation": [(0, (base_bracos[0] - 20, 0, 0)), (0.4, (base_bracos[0] + 20, 0, 0)), (0.8, (base_bracos[0] - 20, 0, 0))]},
                   "braco_d": {"rotation": [(0, (base_bracos[0] + 20, 0, 0)), (0.4, (base_bracos[0] - 20, 0, 0)), (0.8, (base_bracos[0] + 20, 0, 0))]}},
                  {"tronco": {"rotation": [(0, (0, 0, 2)), (0.4, (0, 0, -2)), (0.8, (0, 0, 2))]}})
    m.anim("walk", 0.8, walk)
    ataque = {b: {"rotation": [(0, base_bracos), (0.25, (-160, 0, 0)), (0.45, (-20, 0, 0)), (0.7, base_bracos)]} for b in ataque_bracos}
    ataque["tronco"] = {"rotation": [(0, (0, 0, 0)), (0.25, (8, 0, 0)), (0.45, (-12, 0, 0)), (0.7, (0, 0, 0))]}
    m.anim("attack", 0.7, ataque, loop=False)
    m.anim("damaged", 0.3, tremer("tronco"), loop=False)
    m.anim("death", 1.5, cair("raiz"), loop=False)
    m.anim("spawn", 1.0, surgir("raiz"), loop=False)


# ------------------------------------------------------------------ os bosses

def tita():
    """Titã Sombrio (zumbi): gigante de pele cinza-azulada, armadura negra com espinhos e olhos vermelhos."""
    m = Modelo("boss_tita", {"pele": (74, 92, 98), "pele2": (58, 72, 78), "armadura": (32, 30, 38), "aco": (88, 86, 96),
                             "olho": (255, 40, 30), "boca": (20, 10, 12), "espinho": (150, 145, 160), "corrente": (110, 100, 90)})
    topo = humanoide(m, "pele", "armadura", "pele2", alt=1.15, larg=1.35)
    # ombreiras com espinhos, cinto, olhos, boca e mandíbula
    for s in (1, -1):
        b = "braco_e" if s > 0 else "braco_d"
        x0 = 5.4 * s
        m.caixa(b, (min(x0, x0 + 6 * s) - 0.5, 24.5, -3), (max(x0, x0 + 6 * s) + 0.5, 28.5, 3), "aco")
        m.caixa(b, (x0 + 2.5 * s - 1, 28.5, -1), (x0 + 2.5 * s + 1, 32, 1), "espinho", rot=("z", -15 * s))
        m.caixa(b, (min(x0, x0 + 6 * s), 13, -2.6), (max(x0, x0 + 6 * s), 15, 2.6), "corrente")
    m.caixa("tronco", (-5.6, 13.8, -2.6), (5.6, 15.8, 2.6), "corrente")
    m.caixa("tronco", (-4, 20, -2.8), (4, 26, -2), "aco")
    m.caixa("h_cabeca", (-3, topo + 4, -4.3), (-1, topo + 5, -4), "olho")
    m.caixa("h_cabeca", (1, topo + 4, -4.3), (3, topo + 5, -4), "olho")
    m.caixa("h_cabeca", (-2.5, topo + 1, -4.3), (2.5, topo + 2.5, -4), "boca")
    for s in (1, -1):
        m.caixa("h_cabeca", (2.5 * s - 0.6, topo + 8, -0.6), (2.5 * s + 0.6, topo + 11, 0.6), "espinho", rot=("z", 20 * s))
    anims_humanoide(m, ataque_bracos=("braco_e", "braco_d"), bracos_frente=True)
    return m


def carrasco():
    """Carrasco Ósseo (esqueleto): ossos, capuz negro de carrasco e um machado enorme."""
    m = Modelo("boss_carrasco", {"osso": (220, 214, 190), "osso2": (180, 172, 150), "capuz": (24, 22, 26), "olho": (120, 200, 255),
                                 "cabo": (90, 60, 36), "lamina": (170, 175, 185), "fio": (230, 235, 240), "sangue": (120, 14, 18)})
    m.osso("raiz", (0, 0, 0))
    m.osso("tronco", (0, 12, 0), "raiz")
    m.osso("h_cabeca", (0, 24, 0), "tronco")
    m.osso("braco_e", (5, 22, 0), "tronco")
    m.osso("braco_d", (-5, 22, 0), "tronco")
    m.osso("machado", (-6, 13, -1), "braco_d")
    m.osso("perna_e", (2, 12, 0), "raiz")
    m.osso("perna_d", (-2, 12, 0), "raiz")
    # costelas (fileiras finas) e a coluna
    m.caixa("tronco", (-0.75, 12, -0.75), (0.75, 24, 0.75), "osso2")
    for i, y in enumerate((14, 16.5, 19, 21.5)):
        m.caixa("tronco", (-3.5, y, -1.6), (3.5, y + 1.2, 1.6), "osso")
    m.caixa("tronco", (-4.2, 22.5, -1.8), (4.2, 24, 1.8), "osso")
    m.caixa("tronco", (-3, 12, -1.5), (3, 13.2, 1.5), "osso2")
    # cabeça: caveira com o capuz por cima
    m.caixa("h_cabeca", (-3.6, 24, -3.6), (3.6, 31, 3.6), "osso")
    m.caixa("h_cabeca", (-4.2, 27, -4.2), (4.2, 33, 4.2), "capuz")
    m.caixa("h_cabeca", (-4.2, 24, 1), (4.2, 27, 4.2), "capuz")
    m.caixa("h_cabeca", (-2.6, 28, -4.4), (-1, 29.2, -4.1), "olho")
    m.caixa("h_cabeca", (1, 28, -4.4), (2.6, 29.2, -4.1), "olho")
    m.caixa("h_cabeca", (-1.5, 24.5, -3.9), (1.5, 25.5, -3.6), "osso2")
    for s, b in ((1, "braco_e"), (-1, "braco_d")):
        m.caixa(b, (min(4 * s, 6 * s), 12, -1), (max(4 * s, 6 * s), 24, 1), "osso")
    m.caixa("perna_e", (1, 0, -1), (3, 12, 1), "osso")
    m.caixa("perna_d", (-3, 0, -1), (-1, 12, 1), "osso")
    # o machado: cabo comprido e a lâmina larga, com sangue no fio
    m.caixa("machado", (-6.6, 0, -1.6), (-5.4, 26, -0.4), "cabo")
    m.caixa("machado", (-6.4, 18, -1.4), (-5.6, 27, 8), "lamina")
    m.caixa("machado", (-6.3, 17, 7.5), (-5.7, 28, 9), "fio")
    m.caixa("machado", (-6.2, 19, 6), (-5.8, 21, 8), "sangue")
    anims_humanoide(m, ataque_bracos=("braco_d",))
    return m


def rainha():
    """Rainha Aracnídea: aranha enorme, abdome com marcas, oito olhos vermelhos, presas e coroa de ouro."""
    m = Modelo("boss_rainha", {"corpo": (40, 26, 44), "corpo2": (58, 38, 64), "marca": (150, 30, 60), "olho": (255, 30, 40),
                               "presa": (230, 225, 210), "ouro": (240, 190, 50), "joia": (90, 220, 120), "perna": (30, 20, 34)})
    m.osso("raiz", (0, 0, 0))
    m.osso("corpo", (0, 8, 0), "raiz")
    m.osso("h_cabeca", (0, 9, -7), "corpo")
    m.osso("abdome", (0, 10, 5), "corpo")
    m.caixa("corpo", (-5, 5, -7), (5, 12, 4), "corpo")
    m.caixa("h_cabeca", (-4, 5, -14), (4, 12, -7), "corpo2")
    m.caixa("abdome", (-7, 6, 4), (7, 17, 20), "corpo")
    m.caixa("abdome", (-3, 16.8, 7), (3, 17.4, 16), "marca")
    m.caixa("abdome", (-5, 10, 19.9), (5, 13, 20.3), "marca")
    for i, (x, y) in enumerate(((-2.5, 10), (2.5, 10), (-1, 11), (1, 11), (-3.2, 8.5), (3.2, 8.5), (-1.5, 8), (1.5, 8))):
        m.caixa("h_cabeca", (x - 0.6, y - 0.6, -14.3), (x + 0.6, y + 0.6, -14), "olho")
    for s in (1, -1):
        m.caixa("h_cabeca", (1.5 * s - 0.6, 3, -14.5), (1.5 * s + 0.6, 6, -13), "presa", rot=("x", 15))
    # coroa
    m.caixa("h_cabeca", (-3.5, 12, -12), (3.5, 13, -8), "ouro")
    for x in (-3, -1, 1, 3):
        m.caixa("h_cabeca", (x - 0.5, 13, -10.5), (x + 0.5, 15, -9.5), "ouro")
    m.caixa("h_cabeca", (-0.6, 13.2, -12.2), (0.6, 14.2, -11.8), "joia")
    # 8 pernas: cada uma é um osso com a coxa e a canela
    pernas = []
    for i, z in enumerate((-6, -3, 0, 3)):
        for s in (1, -1):
            nome = f"perna_{'e' if s > 0 else 'd'}{i}"
            m.osso(nome, (5 * s, 9, z), "corpo")
            m.caixa(nome, (min(5 * s, 15 * s), 9, z - 1), (max(5 * s, 15 * s), 11, z + 1), "perna", rot=("z", 25 * s), origem=(5 * s, 10, z))
            m.caixa(nome, (min(13 * s, 15 * s), 0, z - 1), (max(13 * s, 15 * s), 13, z + 1), "perna", rot=("z", -12 * s), origem=(14 * s, 12, z))
            pernas.append((nome, i, s))
    m.anim("idle", 2.0, juntar(respirar("abdome", 0.6, 2.0), {"h_cabeca": {"rotation": [(0, (0, 0, 0)), (1.0, (4, 0, 0)), (2.0, (0, 0, 0))]}}))
    walk = {}
    for nome, i, s in pernas:
        fase = (i + (0 if s > 0 else 1)) % 2
        a = 18 if fase else -18
        walk[nome] = {"rotation": [(0, (0, a, 0)), (0.3, (0, -a, 10 * s)), (0.6, (0, a, 0))]}
    walk["corpo"] = {"position": [(0, (0, 0, 0)), (0.15, (0, 0.6, 0)), (0.3, (0, 0, 0)), (0.45, (0, 0.6, 0)), (0.6, (0, 0, 0))]}
    m.anim("walk", 0.6, walk)
    ataque = {"h_cabeca": {"rotation": [(0, (0, 0, 0)), (0.2, (-25, 0, 0)), (0.4, (30, 0, 0)), (0.6, (0, 0, 0))]}}
    for nome, i, s in pernas:
        if i == 0:
            ataque[nome] = {"rotation": [(0, (0, 0, 0)), (0.2, (0, 0, 45 * s)), (0.4, (0, -30 * s, -10 * s)), (0.6, (0, 0, 0))]}
    m.anim("attack", 0.6, ataque, loop=False)
    m.anim("damaged", 0.3, tremer("corpo"), loop=False)
    m.anim("death", 1.5, {"raiz": {"rotation": [(0, (0, 0, 0)), (0.8, (0, 0, 180))], "position": [(0, (0, 0, 0)), (0.8, (0, 8, 0)), (1.5, (0, -4, 0))]}}, loop=False)
    m.anim("spawn", 1.0, surgir("raiz"), loop=False)
    return m


def acougueiro():
    """Açougueiro Infernal (piglin zumbificado): cabeça de porco podre com presas, avental sujo e um cutelo."""
    m = Modelo("boss_acougueiro", {"pele": (226, 150, 150), "podre": (110, 150, 90), "avental": (180, 170, 150), "sangue": (130, 20, 20),
                                   "ouro": (240, 190, 50), "couro": (100, 64, 40), "lamina": (190, 195, 205), "cabo": (60, 40, 26),
                                   "presa": (240, 235, 220), "olho": (255, 120, 20)})
    topo = humanoide(m, "pele", "couro", "couro", alt=1.0, larg=1.25)
    # cabeça larga de porco: focinho, presas, orelha podre
    m.caixa("h_cabeca", (-5, topo, -4.5), (5, topo + 8, 4.5), "pele")
    m.caixa("h_cabeca", (-2.5, topo + 1, -6), (2.5, topo + 4, -4.5), "pele")
    m.caixa("h_cabeca", (-1.8, topo + 2, -6.2), (-0.6, topo + 3, -5.9), "couro")
    m.caixa("h_cabeca", (0.6, topo + 2, -6.2), (1.8, topo + 3, -5.9), "couro")
    for s in (1, -1):
        m.caixa("h_cabeca", (3 * s - 0.5, topo + 1, -5.2), (3 * s + 0.5, topo + 4, -4.5), "presa")
        m.caixa("h_cabeca", (min(4.5 * s, 7 * s), topo + 6, -1), (max(4.5 * s, 7 * s), topo + 9, 1), "podre", rot=("z", -20 * s))
    m.caixa("h_cabeca", (-3.5, topo + 5, -4.8), (-1.5, topo + 6, -4.5), "olho")
    m.caixa("h_cabeca", (1.5, topo + 5, -4.8), (3.5, topo + 6, -4.5), "olho")
    m.caixa("h_cabeca", (-5.2, topo + 3, 0), (-4.8, topo + 7, 3), "podre")
    # avental com sangue, cinto e o brinco de ouro
    m.caixa("tronco", (-4.6, 8, -2.6), (4.6, 23, -2.1), "avental")
    m.caixa("tronco", (-2, 13, -2.75), (1, 16, -2.55), "sangue")
    m.caixa("tronco", (1.5, 18, -2.75), (3, 20, -2.55), "sangue")
    m.caixa("tronco", (-5.2, 12, -2.4), (5.2, 13.2, 2.4), "couro")
    m.caixa("tronco", (3.8, 12.2, -2.6), (5, 13.4, -2.3), "ouro")
    # o cutelo, na mão direita
    m.osso("cutelo", (-7, 13, -1), "braco_d")
    m.caixa("cutelo", (-7.6, 11, -2), (-6.4, 14, 0), "cabo")
    m.caixa("cutelo", (-7.4, 3, -2), (-6.6, 11, 7), "lamina")
    m.caixa("cutelo", (-7.5, 3, 5.5), (-6.5, 8, 7), "sangue")
    anims_humanoide(m, ataque_bracos=("braco_d",))
    return m


def estilhaco():
    """Estilhaço Verde (creeper): corpo de creeper rachado com cristais verdes saindo, rosto que brilha."""
    m = Modelo("boss_estilhaco", {"verde": (70, 160, 70), "verde2": (50, 120, 50), "verde3": (110, 200, 100), "rosto": (12, 20, 12),
                                  "cristal": (120, 255, 150), "cristal2": (60, 230, 200), "brilho": (220, 255, 220)})
    m.osso("raiz", (0, 0, 0))
    m.osso("corpo", (0, 6, 0), "raiz")
    m.osso("h_cabeca", (0, 18, 0), "corpo")
    for nome, x, z in (("pata_fe", 2, -2), ("pata_fd", -2, -2), ("pata_te", 2, 2), ("pata_td", -2, 2)):
        m.osso(nome, (x, 6, z), "raiz")
        m.caixa(nome, (x - 2 if x > 0 else x - 2, 0, z - 2 if z < 0 else z - 2), (x + 2, 6, z + 2), "verde2")
    m.caixa("corpo", (-4, 6, -2), (4, 18, 2), "verde")
    m.caixa("h_cabeca", (-4, 18, -4), (4, 26, 4), "verde3")
    # o rosto do creeper
    for (x0, y0, x1, y1) in ((-3, 22, -1, 24), (1, 22, 3, 24), (-1, 19, 1, 22), (-2, 18.5, -1, 21), (1, 18.5, 2, 21)):
        m.caixa("h_cabeca", (x0, y0, -4.3), (x1, y1, -4), "rosto")
    # cristais saindo das costas e da cabeça
    for i, (x, y, z, r, alt) in enumerate(((0, 12, 2, 20, 8), (-3, 9, 2, -25, 6), (3, 15, 2, 30, 7), (2, 26, 0, 15, 6), (-2, 26, 1, -20, 5))):
        cor = "cristal" if i % 2 == 0 else "cristal2"
        m.caixa("corpo" if y < 18 else "h_cabeca", (x - 1, y, z), (x + 1, y + alt, z + 2), cor, rot=("x", r), origem=(x, y, z))
    m.caixa("corpo", (-1, 10, -2.3), (1, 14, -2), "brilho")
    m.anim("idle", 1.6, juntar(respirar("corpo", 0.5, 1.6),
                                {"h_cabeca": {"rotation": [(0, (0, 0, 0)), (0.8, (0, 0, 3)), (1.6, (0, 0, 0))]}}))
    m.anim("walk", 0.6, balanco([("pata_fe", "pata_fd"), ("pata_td", "pata_te")], 25, 0.6))
    # ataque: incha (como o creeper antes de explodir) e dá a cabeçada
    m.anim("attack", 0.8, {"corpo": {"rotation": [(0, (0, 0, 0)), (0.3, (-15, 0, 0)), (0.5, (25, 0, 0)), (0.8, (0, 0, 0))]},
                           "h_cabeca": {"rotation": [(0, (0, 0, 0)), (0.3, (-10, 0, 0)), (0.5, (20, 0, 0)), (0.8, (0, 0, 0))]}}, loop=False)
    m.anim("damaged", 0.3, tremer("corpo"), loop=False)
    m.anim("death", 1.5, cair("raiz"), loop=False)
    m.anim("spawn", 1.0, surgir("raiz"), loop=False)
    return m


def feiticeira():
    """Feiticeira Maldita: bruxa de manto roxo, chapéu pontudo torto, nariz verruguento e cajado com orbe."""
    m = Modelo("boss_feiticeira", {"pele": (130, 170, 110), "manto": (70, 30, 100), "manto2": (50, 20, 74), "chapeu": (30, 24, 36),
                                   "faixa": (150, 40, 160), "olho": (255, 230, 60), "cajado": (80, 50, 30), "orbe": (180, 80, 255),
                                   "orbe2": (230, 180, 255), "verruga": (90, 120, 70)})
    m.osso("raiz", (0, 0, 0))
    m.osso("tronco", (0, 12, 0), "raiz")
    m.osso("h_cabeca", (0, 24, 0), "tronco")
    m.osso("braco_e", (5, 22, 0), "tronco")
    m.osso("braco_d", (-5, 22, 0), "tronco")
    m.osso("chapeu", (0, 32, 0), "h_cabeca")
    m.osso("cajado", (-6, 13, 0), "braco_d")
    # o manto vai até o chão (sem pernas aparentes): saia larga
    m.caixa("raiz", (-5, 0, -4), (5, 12, 4), "manto2")
    m.caixa("tronco", (-4, 12, -2.5), (4, 24, 2.5), "manto")
    m.caixa("tronco", (-4.2, 15, -2.7), (4.2, 16, 2.7), "faixa")
    m.caixa("h_cabeca", (-4, 24, -4), (4, 32, 4), "pele")
    m.caixa("h_cabeca", (-1, 26, -6), (1, 29, -4), "pele")
    m.caixa("h_cabeca", (0.2, 26.5, -6.3), (0.9, 27.2, -6), "verruga")
    m.caixa("h_cabeca", (-3, 29, -4.3), (-1.5, 30, -4), "olho")
    m.caixa("h_cabeca", (1.5, 29, -4.3), (3, 30, -4), "olho")
    # chapéu pontudo e torto
    m.caixa("chapeu", (-6, 32, -6), (6, 33, 6), "chapeu")
    m.caixa("chapeu", (-4, 33, -4), (4, 36, 4), "chapeu")
    m.caixa("chapeu", (-4.2, 33, -4.2), (4.2, 34, 4.2), "faixa")
    m.caixa("chapeu", (-2.5, 36, -2.5), (2.5, 39, 2.5), "chapeu", rot=("z", 12), origem=(0, 36, 0))
    m.caixa("chapeu", (0, 38.5, -1), (2, 42, 1), "chapeu", rot=("z", 30), origem=(0, 39, 0))
    for s, b in ((1, "braco_e"), (-1, "braco_d")):
        m.caixa(b, (min(4 * s, 8 * s), 13, -2), (max(4 * s, 8 * s), 24, 2), "manto")
        m.caixa(b, (min(4.5 * s, 7.5 * s), 11, -1.5), (max(4.5 * s, 7.5 * s), 13, 1.5), "pele")
    # cajado com o orbe
    m.caixa("cajado", (-6.7, -2, -0.7), (-5.3, 26, 0.7), "cajado")
    m.caixa("cajado", (-8, 26, -1.5), (-4, 30, 2.5), "orbe")
    m.caixa("cajado", (-7, 27, -0.5), (-5, 29, 1.5), "orbe2")
    m.anim("idle", 2.4, juntar(respirar("tronco", 0.5, 2.4),
                                {"cajado": {"rotation": [(0, (0, 0, 0)), (1.2, (0, 20, 0)), (2.4, (0, 0, 0))]},
                                 "chapeu": {"rotation": [(0, (0, 0, 0)), (1.2, (0, 0, -4)), (2.4, (0, 0, 0))]}}))
    m.anim("walk", 0.9, juntar({"raiz": {"rotation": [(0, (0, 0, 3)), (0.45, (0, 0, -3)), (0.9, (0, 0, 3))]}},
                                {"braco_e": {"rotation": [(0, (-15, 0, 0)), (0.45, (15, 0, 0)), (0.9, (-15, 0, 0))]}}))
    m.anim("attack", 0.8, {"braco_d": {"rotation": [(0, (0, 0, 0)), (0.3, (-150, 0, 0)), (0.5, (-60, 0, 0)), (0.8, (0, 0, 0))]},
                           "braco_e": {"rotation": [(0, (0, 0, 0)), (0.3, (-90, 0, -30)), (0.8, (0, 0, 0))]}}, loop=False)
    m.anim("damaged", 0.3, tremer("tronco"), loop=False)
    m.anim("death", 1.5, cair("raiz"), loop=False)
    m.anim("spawn", 1.0, surgir("raiz"), loop=False)
    return m


MODELOS = [tita, carrasco, rainha, acougueiro, estilhaco, feiticeira]


def main():
    pasta = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).with_name("bosses")
    pasta.mkdir(parents=True, exist_ok=True)
    for f in MODELOS:
        m = f()
        caminho = pasta / f"{m.nome}.bbmodel"
        caminho.write_text(json.dumps(m.json(), ensure_ascii=False), encoding="utf-8")
        print(f"{caminho.name}: {len(m.elementos)} caixas, {len(m.ossos)} ossos, {len(m.animacoes)} animações")


if __name__ == "__main__":
    main()
