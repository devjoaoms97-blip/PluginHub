"""Gera a voz de um reel narrado com o edge-tts (vozes neurais da Microsoft; o texto vai pro serviço
de voz deles) e o tempo de cada palavra, pra legenda acompanhar a fala.

    python narracao/gerar.py curiosidades-01

Lê narracao/<id>.json e grava:
    public/narracao/<id>.mp3        a narração inteira (as falas em sequência, com uma pausa curta)
    narracao/<id>-tempos.json       início e fim de cada fala e de cada palavra, em segundos
Precisa do edge-tts (python -m pip install --user edge-tts) e usa o ffmpeg do compositor do Remotion.
"""
import asyncio
import json
import re
import subprocess
import sys
from pathlib import Path

import edge_tts

RAIZ = Path(__file__).resolve().parent.parent
FFMPEG = next((RAIZ / 'node_modules' / '@remotion').glob('compositor-*/ffmpeg.exe'))
PAUSA = 0.35  # segundos de silêncio entre uma fala e outra


def duracao(arquivo):
    saida = subprocess.run([str(FFMPEG), '-hide_banner', '-i', str(arquivo)], capture_output=True, text=True).stderr
    h, m, s = re.search(r'Duration: (\d+):(\d+):([\d.]+)', saida).groups()
    return int(h) * 3600 + int(m) * 60 + float(s)


async def falar(texto, voz, velocidade, destino):
    """Uma fala: grava o mp3 e devolve as palavras com início e fim (segundos, a partir do começo da fala)."""
    try:
        comunicar = edge_tts.Communicate(texto, voz, rate=velocidade, boundary='WordBoundary')
    except TypeError:  # versões antigas já mandam as palavras por padrão
        comunicar = edge_tts.Communicate(texto, voz, rate=velocidade)
    palavras = []
    with open(destino, 'wb') as f:
        async for parte in comunicar.stream():
            if parte['type'] == 'audio':
                f.write(parte['data'])
            elif parte['type'] == 'WordBoundary':
                ini = parte['offset'] / 1e7
                palavras.append({'texto': parte['text'], 'ini': round(ini, 3), 'fim': round(ini + parte['duration'] / 1e7, 3)})
    # a voz devolve as palavras sem pontuação: devolve a vírgula ou o ponto que vem logo depois no
    # texto, pra legenda quebrar o bloco no fim da frase
    pos = 0
    for p in palavras:
        achou = texto.find(p['texto'], pos)
        if achou < 0:
            continue
        pos = achou + len(p['texto'])
        if pos < len(texto) and texto[pos] in '.,!?:':
            p['texto'] += texto[pos]
    return palavras


def falado(texto, pronuncia):
    """O texto que vai pra voz: cada nome em inglês trocado pela escrita de como se fala."""
    for original, dito in pronuncia.items():
        texto = re.sub(rf'(?<!\w){re.escape(original)}(?!\w)', dito, texto)
    return texto


def legendado(palavras, pronuncia):
    """Volta a grafia original na legenda (a voz devolve "Críper", a legenda mostra "Creeper");
    uma pronúncia de várias palavras vira uma palavra só, do começo da primeira ao fim da última."""
    limpa = lambda s: re.sub(r'[.,!?:]$', '', s).lower()
    trocas = sorted(((dito.split(), original) for original, dito in pronuncia.items()), key=lambda t: -len(t[0]))
    saida, i = [], 0
    while i < len(palavras):
        for partes, original in trocas:
            grupo = palavras[i:i + len(partes)]
            if len(grupo) == len(partes) and all(limpa(g['texto']) == limpa(p) for g, p in zip(grupo, partes)):
                pont = re.search(r'[.,!?:]$', grupo[-1]['texto'])
                saida.append({'texto': original + (pont.group() if pont else ''), 'ini': grupo[0]['ini'], 'fim': grupo[-1]['fim']})
                i += len(partes)
                break
        else:
            saida.append(palavras[i])
            i += 1
    return saida


async def main(id):
    roteiro = json.loads((RAIZ / 'narracao' / f'{id}.json').read_text(encoding='utf-8'))
    pronuncia = {k: v for k, v in json.loads((RAIZ / 'narracao' / 'pronuncia.json').read_text(encoding='utf-8')).items() if not k.startswith('_')}
    pronuncia.update(roteiro.get('pronuncia', {}))
    tmp = RAIZ / 'narracao' / '.tmp'
    tmp.mkdir(exist_ok=True)
    silencio = tmp / 'pausa.mp3'
    subprocess.run([str(FFMPEG), '-hide_banner', '-loglevel', 'error', '-y', '-f', 'lavfi', '-i', 'anullsrc=r=24000:cl=mono',
                    '-t', str(PAUSA), '-c:a', 'libmp3lame', '-b:a', '48k', str(silencio)], check=True)
    falas, lista, t = [], [], 0.0
    for i, fala in enumerate(roteiro['falas']):
        mp3 = tmp / f'{i:02d}.mp3'
        palavras = await falar(falado(fala['texto'], pronuncia), roteiro['voz'], roteiro.get('velocidade', '+0%'), mp3)
        palavras = legendado(palavras, pronuncia)
        dur = duracao(mp3)
        falas.append({'ini': round(t, 3), 'fim': round(t + dur + PAUSA, 3),
                      'palavras': [{**p, 'ini': round(t + p['ini'], 3), 'fim': round(t + p['fim'], 3)} for p in palavras]})
        lista += [f"file '{mp3.as_posix()}'", f"file '{silencio.as_posix()}'"]
        t += dur + PAUSA
        print(f'fala {i + 1}: {dur:.1f} s, {len(palavras)} palavras')
    (tmp / 'lista.txt').write_text('\n'.join(lista), encoding='utf-8')
    saida = RAIZ / 'public' / 'narracao' / f'{id}.mp3'
    saida.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run([str(FFMPEG), '-hide_banner', '-loglevel', 'error', '-y', '-f', 'concat', '-safe', '0',
                    '-i', str(tmp / 'lista.txt'), '-c:a', 'libmp3lame', '-b:a', '128k', str(saida)], check=True)
    (RAIZ / 'narracao' / f'{id}-tempos.json').write_text(json.dumps({'duracao': round(t, 3), 'falas': falas}, ensure_ascii=False, indent=1), encoding='utf-8')
    print(f'{saida.name}: {t:.1f} s')


if __name__ == '__main__':
    asyncio.run(main(sys.argv[1]))
