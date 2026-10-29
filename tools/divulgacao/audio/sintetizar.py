"""
Sintetiza a trilha e os efeitos de cada reel do reels.json, sem samples de terceiros (nada de
direito autoral). Só a biblioteca padrão do Python: uns segundos por reel.

    python audio/sintetizar.py              # grava public/audio/<id>.wav de todos os reels
    python audio/sintetizar.py ReelBoss     # só um

O beat é de 120 BPM e as cenas duram múltiplos de 15 frames (meia batida), então os cortes
caem na batida. Os cortes, o fechamento e a duração saem do próprio reels.json.
Clima "sombrio": ré menor, grave e pesado. Clima "epico": ré maior, com arpejo e mais brilho.
"""
import json
import math
import random
import struct
import sys
import wave
from pathlib import Path

TAXA = 44100
FPS = 30
BATIDA = 0.5
RAIZ = Path(__file__).parent.parent
CONFIG = json.loads((RAIZ / 'reels.json').read_text(encoding='utf-8'))


def passa_baixa(amostras, corte):
    a = 1 - math.exp(-2 * math.pi * corte / TAXA)
    y, saida = 0.0, []
    for x in amostras:
        y += a * (x - y)
        saida.append(y)
    return saida


def ruido(seg):
    return [random.uniform(-1, 1) for _ in range(int(seg * TAXA))]


def kick(forte=False):
    seg = 0.5 if forte else 0.28
    fase, saida = 0.0, []
    for k in range(int(seg * TAXA)):
        t = k / TAXA
        freq = 45 + (110 if forte else 90) * math.exp(-t * 30)
        fase += 2 * math.pi * freq / TAXA
        saida.append(math.sin(fase) * math.exp(-t * (6 if forte else 12)))
    return saida


def chimbal():
    r = ruido(0.05)
    # Passa-alta pobre: ruído menos a versão suavizada dele.
    baixo = passa_baixa(r, 3000)
    return [(x - b) * math.exp(-k / TAXA * 70) for k, (x, b) in enumerate(zip(r, baixo))]


def impacto():
    """Boom grave com estalo: o 'soco' de cada texto entrando."""
    corpo = kick(forte=True)
    estalo = passa_baixa(ruido(0.5), 1800)
    return [c * 1.1 + e * 0.9 * math.exp(-k / TAXA * 9) for k, (c, e) in enumerate(zip(corpo, estalo))]


def whoosh(seg=0.45):
    """Ruído que cresce e abre o filtro, terminando no corte."""
    r = ruido(seg)
    saida, y = [], 0.0
    for k, x in enumerate(r):
        p = k / len(r)
        a = 1 - math.exp(-2 * math.pi * (300 + 5000 * p * p) / TAXA)
        y += a * (x - y)
        saida.append(y * p * p)
    return saida


def trovao(seg=3.5):
    r = passa_baixa(passa_baixa(ruido(seg), 400), 250)
    saida = []
    for k, x in enumerate(r):
        t = k / TAXA
        estrondo = 1 + 0.6 * math.sin(t * 7) * math.sin(t * 2.3)
        saida.append(x * 6 * min(1, t * 20) * math.exp(-t * 1.1) * estrondo)
    return saida


def drone(seg, notas):
    """Pad grave com dente de serra filtrado e um tremor lento."""
    saida = []
    fases = [0.0] * len(notas)
    for k in range(int(seg * TAXA)):
        t = k / TAXA
        v = 0.0
        for j, f in enumerate(notas):
            fases[j] = (fases[j] + f * (1 + 0.002 * j) / TAXA) % 1
            v += (2 * fases[j] - 1) / (j + 1)
        envelope = min(1, t / 1.5) * min(1, (seg - t) / 1.0)
        saida.append(v * envelope * (0.8 + 0.2 * math.sin(t * 1.7)))
    return passa_baixa(passa_baixa(saida, 500), 500)


def nota_dedilhada(freq, seg=0.3):
    """Nota curta de 'pluck' (triangular com decaimento), pro arpejo do clima épico."""
    fase, saida = 0.0, []
    for k in range(int(seg * TAXA)):
        fase = (fase + freq / TAXA) % 1
        tri = 4 * abs(fase - 0.5) - 1
        saida.append(tri * math.exp(-k / TAXA * 9))
    return passa_baixa(saida, 2500)


def subida(seg=1.5):
    """Riser antes do fechamento: ruído e tom subindo juntos."""
    r = ruido(seg)
    fase, saida, y = 0.0, [], 0.0
    for k, x in enumerate(r):
        p = k / len(r)
        fase += 2 * math.pi * (200 + 1400 * p * p) / TAXA
        a = 1 - math.exp(-2 * math.pi * (500 + 7000 * p) / TAXA)
        y += a * (x - y)
        saida.append((y * 0.7 + math.sin(fase) * 0.25) * p ** 1.5)
    return saida


def fogo(seg):
    """Crepitar: estalos curtos aleatórios sobre um chiado baixo."""
    saida = [x * 0.15 for x in passa_baixa(ruido(seg), 900)]
    for _ in range(int(seg * 25)):
        i = random.randrange(len(saida) - 400)
        for k in range(300):
            saida[i + k] += random.uniform(-1, 1) * math.exp(-k / 40) * random.uniform(0.3, 1)
    return saida


CLIMAS = {
    'sombrio': {'pad': (36.71, 55.0, 73.42), 'fim': (73.42, 110.0, 146.83), 'arpejo': None},
    'epico': {'pad': (73.42, 92.5, 110.0), 'fim': (146.83, 185.0, 220.0),
              'arpejo': (293.66, 369.99, 440.0, 587.33, 440.0, 369.99)},
}


def gerar(id, reel):
    random.seed(id)
    clima = CLIMAS[reel['clima']]
    inicios, t = [], 0
    for cena in reel['cenas']:
        inicios.append(t / FPS)
        t += cena[2]
    fechamento = t / FPS
    duracao = (t + CONFIG['final']) / FPS
    n = int(TAXA * duracao)
    mix = [0.0] * n

    def somar(inicio, amostras, volume=1.0):
        i0 = int(inicio * TAXA)
        for k, v in enumerate(amostras):
            if 0 <= i0 + k < n:
                mix[i0 + k] += v * volume

    # Trilha: pad do começo ao fim, kick em toda batida até o fechamento, chimbal da segunda cena
    # em diante e, no último terço, kick a cada meia batida pra acelerar.
    somar(0, drone(duracao, clima['pad']), 0.35)
    acelera = inicios[-1]
    b, passo = 0.0, 0
    while b < fechamento - 0.01:
        somar(b, kick(), 0.55)
        if b >= inicios[1]:
            somar(b + BATIDA / 2, chimbal(), 0.18)
        if b >= acelera:
            somar(b + BATIDA / 2, kick(), 0.4)
        if clima['arpejo']:
            for meio in (0, BATIDA / 2):
                nota = clima['arpejo'][passo % len(clima['arpejo'])]
                somar(b + meio, nota_dedilhada(nota), 0.16)
                passo += 1
        b += BATIDA

    # Efeitos dos cortes.
    for c in inicios:
        somar(c, impacto(), 0.8)
        if c > 0:
            w = whoosh()
            somar(c - len(w) / TAXA, w, 0.5)
    if 'trovao' in reel:
        somar(inicios[reel['trovao']], trovao(), 0.7)
    r = subida()
    somar(fechamento - len(r) / TAXA, r, 0.5)
    somar(fechamento, impacto(), 1.0)
    somar(fechamento, drone(duracao - fechamento, clima['fim']), 0.25)
    somar(fechamento, fogo(duracao - fechamento), 0.35)

    # Normaliza com folga e grava em 16 bits mono.
    ganho = 0.89 / max(abs(x) for x in mix)
    saida = RAIZ / 'public' / 'audio' / f'{id}.wav'
    saida.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(saida), 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(TAXA)
        w.writeframes(b''.join(struct.pack('<h', int(max(-1, min(1, x * ganho)) * 32767)) for x in mix))
    print(f'{saida.name}: {duracao:.1f} s, {reel["clima"]}')


for id, reel in CONFIG['reels'].items():
    if len(sys.argv) < 2 or id in sys.argv[1:]:
        gerar(id, reel)
