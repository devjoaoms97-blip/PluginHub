"""
Ícones de modelos do Blockbench (.bbmodel): desenha o modelo inteiro em 3D, com as texturas dele, numa
imagem quadrada, de cima e de lado, como os ícones de item do jogo. Usado pelo gerar.py pras montarias do
FreeMinecraftModels (terceiros/frenchkrab): o pacote do FMM só tem as partes soltas (cabeça, perna...),
nenhum item com o bicho inteiro pra mostrar na loja.

    python tools/pacote/icones.py arquivo.bbmodel saida.png   # um ícone avulso, pra conferir

Pose: a de descanso (sem animação). Rotação dos ossos e das caixas na ordem do Blockbench (X, depois Y,
depois Z). A frente do bicho é o lado onde está a cabeça (osso "h_head", ou o lado mais comprido).
"""
import base64
import io
import json
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw

TAMANHO = 64  # o ícone final
SUPER = 6  # desenha maior e reduz, pra suavizar as bordas


def _rodar(p, rot, origem):
    """Gira o ponto p em volta da origem: X, depois Y, depois Z (graus)."""
    x, y, z = p[0] - origem[0], p[1] - origem[1], p[2] - origem[2]
    rx, ry, rz = (math.radians(a) for a in rot)
    y, z = y * math.cos(rx) - z * math.sin(rx), y * math.sin(rx) + z * math.cos(rx)
    x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)
    x, y = x * math.cos(rz) - y * math.sin(rz), x * math.sin(rz) + y * math.cos(rz)
    return (x + origem[0], y + origem[1], z + origem[2])


def _texturas(modelo):
    imgs = []
    for t in modelo.get("textures", []):
        fonte = t.get("source", "")
        if fonte.startswith("data:image"):
            imgs.append(Image.open(io.BytesIO(base64.b64decode(fonte.split(",", 1)[1]))).convert("RGBA"))
        else:
            imgs.append(None)
    return imgs


def _numero(v):
    try:
        return float(v)
    except (TypeError, ValueError):
        return 0.0  # expressão (molang): fica no zero


def _pose(modelo):
    """O primeiro quadro da animação de parado ("idle"): osso -> (giro, deslocamento), já no sentido do
    Blockbench (giro e deslocamento em X, e giro em Y, entram com o sinal trocado)."""
    pose = {}
    for anim in modelo.get("animations", []):
        if anim.get("name") != "idle":
            continue
        for uuid, animador in (anim.get("animators") or {}).items():
            giro, desloc = [0.0, 0.0, 0.0], [0.0, 0.0, 0.0]
            for canal in ("rotation", "position"):
                quadros = [k for k in animador.get("keyframes", []) if k.get("channel") == canal and k.get("data_points")]
                if not quadros:
                    continue
                d = min(quadros, key=lambda k: k.get("time", 0))["data_points"][0]
                v = [_numero(d.get("x")), _numero(d.get("y")), _numero(d.get("z"))]
                if canal == "rotation":
                    giro = [-v[0], -v[1], v[2]]
                else:
                    desloc = [-v[0], v[1], v[2]]
            pose[uuid] = (giro, desloc)
    return pose


def _elementos(modelo):
    """Cada elemento com a lista de transformações dos ossos (de fora pra dentro): (giro, origem, deslocamento)."""
    por_uuid = {e["uuid"]: e for e in modelo.get("elements", [])}
    pose = _pose(modelo)
    saida = []

    def andar(filhos, transf):
        for f in filhos:
            if isinstance(f, str):
                e = por_uuid.get(f)
                if e and e.get("visibility", True) is not False and e.get("type", "cube") == "cube":
                    saida.append((e, transf))
            elif isinstance(f, dict):
                if f.get("name", "").lower().startswith(("hitbox", "b_hitbox")) or f.get("visibility") is False:
                    continue
                rot = list(f.get("rotation") or [0, 0, 0])
                giro, desloc = pose.get(f.get("uuid"), ([0, 0, 0], [0, 0, 0]))
                rot = [rot[k] + giro[k] for k in range(3)]
                andar(f.get("children", []), transf + [(rot, f.get("origin", [0, 0, 0]), desloc)])

    andar(modelo.get("outliner", []), [])
    return saida


def _faces(e):
    """As 6 faces da caixa: (nome, 4 cantos na ordem do uv: esquerda-cima, direita-cima, direita-baixo, esquerda-baixo)."""
    (x0, y0, z0), (x1, y1, z1) = e["from"], e["to"]
    inf = e.get("inflate", 0)
    x0, y0, z0, x1, y1, z1 = x0 - inf, y0 - inf, z0 - inf, x1 + inf, y1 + inf, z1 + inf
    return {
        "north": [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        "south": [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        "east": [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
        "west": [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        "up": [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        "down": [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
    }


LUZ = {"up": 1.0, "north": 0.8, "south": 0.8, "east": 0.6, "west": 0.6, "down": 0.5}


def _frente(modelo, elementos):
    """Pra que lado (em graus de giro) o bicho olha: o da cabeça, ou o eixo mais comprido."""
    def centro(nome):
        for g in modelo.get("outliner", []):
            pilha = [g]
            while pilha:
                n = pilha.pop()
                if isinstance(n, dict):
                    if n.get("name", "").lower() in (nome,):
                        return n.get("origin")
                    pilha.extend(n.get("children", []))
        return None

    cabeca = centro("h_head")
    if cabeca and (abs(cabeca[0]) > 0.5 or abs(cabeca[2]) > 0.5):
        return math.degrees(math.atan2(cabeca[0], cabeca[2]))
    xs = [v for e, _ in elementos for v in (e["from"][0], e["to"][0])]
    zs = [v for e, _ in elementos for v in (e["from"][2], e["to"][2])]
    return 90 if (max(xs) - min(xs)) > (max(zs) - min(zs)) else 0


def icone(caminho):
    modelo = json.loads(Path(caminho).read_text(encoding="utf-8"))
    texturas = _texturas(modelo)
    res = modelo.get("resolution", {"width": 16, "height": 16})
    elementos = _elementos(modelo)
    # câmera: de frente-lado (45° a partir da frente do bicho) e 30° de cima, como o ícone de item
    giro = math.radians(_frente(modelo, elementos) - 35)
    alto = math.radians(28)

    def ver(p):
        x, y, z = p
        x, z = x * math.cos(giro) - z * math.sin(giro), x * math.sin(giro) + z * math.cos(giro)
        y, z = y * math.cos(alto) - z * math.sin(alto), y * math.sin(alto) + z * math.cos(alto)
        return x, y, z  # z: mais perto da câmera = maior

    quadrados = []  # (profundidade, 4 pontos 2D, cor)
    for e, rots in elementos:
        for nome, cantos in _faces(e).items():
            face = e.get("faces", {}).get(nome)
            if not face or face.get("texture") is None or "uv" not in face:
                continue
            img = texturas[face["texture"]] if 0 <= face["texture"] < len(texturas) else None
            if img is None:
                continue
            # cantos no mundo: rotação da caixa, depois a dos ossos (de dentro pra fora), com a pose do idle
            pts = []
            for c in cantos:
                if e.get("rotation") and any(e["rotation"]):
                    c = _rodar(c, e["rotation"], e.get("origin", [0, 0, 0]))
                for rot, origem, desloc in reversed(rots):
                    if any(rot):
                        c = _rodar(c, rot, origem)
                    c = (c[0] + desloc[0], c[1] + desloc[1], c[2] + desloc[2])
                pts.append(ver(c))
            # face de costas pra câmera não aparece
            (ax, ay, _), (bx, by, _), (cx, cy, _) = pts[0], pts[1], pts[2]
            if (bx - ax) * (cy - ay) - (by - ay) * (cx - ax) > 0:
                continue
            u0, v0, u1, v1 = face["uv"]
            ex, ey = img.width / res["width"], img.height / res["height"]
            nu = max(1, round(abs(u1 - u0) * ex))
            nv = max(1, round(abs(v1 - v0) * ey))
            luz = LUZ[nome]
            # cada texel vira um quadradinho na face
            for i in range(nu):
                for j in range(nv):
                    tu = (u0 + (u1 - u0) * (i + 0.5) / nu) * ex
                    tv = (v0 + (v1 - v0) * (j + 0.5) / nv) * ey
                    px = img.getpixel((min(img.width - 1, max(0, int(tu))), min(img.height - 1, max(0, int(tv)))))
                    if px[3] < 128:
                        continue

                    def ponto(a, b):
                        cima = [pts[0][k] + (pts[1][k] - pts[0][k]) * a for k in range(3)]
                        baixo = [pts[3][k] + (pts[2][k] - pts[3][k]) * a for k in range(3)]
                        return [cima[k] + (baixo[k] - cima[k]) * b for k in range(3)]

                    q = [ponto(i / nu, j / nv), ponto((i + 1) / nu, j / nv), ponto((i + 1) / nu, (j + 1) / nv), ponto(i / nu, (j + 1) / nv)]
                    prof = sum(p[2] for p in q) / 4
                    cor = (int(px[0] * luz), int(px[1] * luz), int(px[2] * luz), 255)
                    quadrados.append((prof, [(p[0], p[1]) for p in q], cor))
    if not quadrados:
        return Image.new("RGBA", (TAMANHO, TAMANHO), (0, 0, 0, 0))
    xs = [p[0] for _, q, _ in quadrados for p in q]
    ys = [p[1] for _, q, _ in quadrados for p in q]
    lado = max(max(xs) - min(xs), max(ys) - min(ys))
    grande = TAMANHO * SUPER
    escala = grande * 0.92 / lado
    cx, cy = (max(xs) + min(xs)) / 2, (max(ys) + min(ys)) / 2
    img = Image.new("RGBA", (grande, grande), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for _, q, cor in sorted(quadrados, key=lambda t: t[0]):
        d.polygon([(grande / 2 + (x - cx) * escala, grande / 2 - (y - cy) * escala) for x, y in q], fill=cor, outline=cor)
    return img.resize((TAMANHO, TAMANHO), Image.LANCZOS)


if __name__ == "__main__":
    icone(sys.argv[1]).save(sys.argv[2])
    print("ok:", sys.argv[2])
