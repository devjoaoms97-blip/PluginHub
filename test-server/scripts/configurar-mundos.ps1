# Cria e configura os mundos do rankup no servidor de teste, pelo RCON (o servidor precisa estar
# ligado). Pode rodar de novo: mundo que já existe não é recriado, só tem as regras reaplicadas.
#
#   spawn     vazio, com plataforma: spawn, mercado, loja e minas (sem PvP, pacífico, sempre de dia)
#   arenas    vazio, com plataforma: X1, Gladiador e boss (PvP, sem mobs naturais, sempre de dia)
#   terrenos  grade do TerrenosPlugin (sem PvP nem mobs)
#   recursos  mundo normal, resetado de tempos em tempos pelo RecursosPlugin (PvP)
#
# Não usa o setworldspawn: no 26.2 ele vale pro servidor todo, e os jogadores novos passariam a
# entrar no mundo em que ele rodou. O ponto de chegada de cada mundo fica no Multiverse.

. "$PSScriptRoot\comum.ps1"

$rcon = Connect-Rcon
if (-not $rcon) {
    Write-Host 'O servidor está desligado (ou o RCON está desligado). Ligue com iniciar.bat e rode de novo.' -ForegroundColor Yellow
    exit 1
}

function Rodar([string]$comando) {
    $resposta = (Invoke-Rcon $rcon $comando) -replace '§.', ''
    Write-Host "  > $comando" -ForegroundColor DarkGray
    if ($resposta.Trim()) { Write-Host "    $($resposta.Trim() -replace "`n", ' | ')" }
    $resposta
}

$existentes = Rodar 'mv list'
$vazio = '--world-type flat --no-structures --generator-settings {"layers":[{"block":"minecraft:air","height":1}],"biome":"minecraft:the_void"} --force-spawn-position 0.5,65,0.5:0:0'
$mundos = [ordered]@{
    spawn    = "mv create spawn normal $vazio"
    arenas   = "mv create arenas normal $vazio"
    terrenos = 'mv create terrenos normal --generator TerrenosPlugin --no-structures'
    recursos = 'mv create recursos normal'
}

Write-Host 'Criando os mundos que faltam (o de recursos pode levar um minuto)...' -ForegroundColor Cyan
foreach ($nome in $mundos.Keys) {
    if ($existentes -match "(?m)^\s*$nome - ") {
        Write-Host "  ok: $nome já existe"
        continue
    }
    Rodar $mundos[$nome] | Out-Null
}

Write-Host 'Plataformas de chegada dos mundos vazios...' -ForegroundColor Cyan
foreach ($nome in 'spawn', 'arenas') {
    Rodar "execute in minecraft:$nome run forceload add 0 0" | Out-Null
    # o forceload carrega o chunk aos poucos: tenta o fill por até ~10 s
    for ($tentativa = 0; $tentativa -lt 20; $tentativa++) {
        if ((Rodar "execute in minecraft:$nome run fill -3 64 -3 3 64 3 minecraft:stone_bricks keep") -notmatch 'not loaded') { break }
        Start-Sleep -Milliseconds 500
    }
    Rodar "execute in minecraft:$nome run forceload remove 0 0" | Out-Null
}

Write-Host 'Regras de cada mundo...' -ForegroundColor Cyan
$regras = [ordered]@{
    spawn    = @('mv modify spawn set pvp false', 'mv modify spawn set difficulty peaceful',
                 'gamerule spawn_mobs false', 'gamerule advance_time false', 'gamerule advance_weather false')
    arenas   = @('mv modify arenas set pvp true',
                 'gamerule spawn_mobs false', 'gamerule advance_time false', 'gamerule advance_weather false', 'gamerule keep_inventory false')
    terrenos = @('mv modify terrenos set pvp false', 'gamerule spawn_mobs false')
    recursos = @('mv modify recursos set pvp true')
}
foreach ($nome in $regras.Keys) {
    foreach ($regra in $regras[$nome]) {
        if ($regra -like 'gamerule *') { $regra = "execute in minecraft:$nome run $regra" }
        Rodar $regra | Out-Null
    }
}
foreach ($nome in 'spawn', 'arenas') {
    Rodar "time set 6000 $nome" | Out-Null
    Rodar "weather $nome sun" | Out-Null
}

$rcon.Tcp.Close()
Write-Host ''
Write-Host 'Mundos prontos.' -ForegroundColor Green
Write-Host '  Arenas de teste (X1 e Gladiador): node tools\bots\montar-arenas.js, no repositório.'
