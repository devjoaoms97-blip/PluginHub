# Restaura um backup. O servidor precisa estar desligado. Antes de restaurar, faz um backup
# do estado atual (motivo "antes-de-restaurar"), então dá pra desfazer.
#
#   restaurar.bat            -> lista os backups e pergunta qual
#   restaurar.bat -Numero 1  -> restaura o mais recente sem perguntar

param([int]$Numero = 0)

. "$PSScriptRoot\comum.ps1"

if (Test-ServidorLigado) {
    Write-Host 'Desligue o servidor (stop) antes de restaurar.' -ForegroundColor Yellow
    exit 1
}

$backups = @(Get-ChildItem -LiteralPath $Config.backups -Filter 'backup-*.zip' -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending)
if (-not $backups) {
    Write-Host "Nenhum backup em $($Config.backups)."
    exit 1
}

if ($Numero -le 0) {
    for ($i = 0; $i -lt $backups.Count; $i++) {
        Write-Host ('{0,3}. {1}  ({2:N1} MB)' -f ($i + 1), $backups[$i].BaseName, ($backups[$i].Length / 1MB))
    }
    $Numero = [int](Read-Host 'Número do backup pra restaurar (0 cancela)')
    if ($Numero -le 0) { exit 0 }
}
if ($Numero -gt $backups.Count) { throw "Não existe o backup $Numero." }
$escolhido = $backups[$Numero - 1]

& "$PSScriptRoot\backup.ps1" -Motivo antes-de-restaurar -Forcar
if ($LASTEXITCODE -ne 0) { throw 'O backup de segurança falhou; nada foi restaurado.' }

$temporaria = Join-Path $env:TEMP "pluginhub-restaurar-$(Get-Random)"
Add-Type -AssemblyName System.IO.Compression.FileSystem
[IO.Compression.ZipFile]::ExtractToDirectory($escolhido.FullName, $temporaria)

# Troca item por item. Em plugins, troca só as pastas de dados que estão no backup e mantém os jars.
foreach ($item in Get-ChildItem -LiteralPath $temporaria) {
    if ($item.Name -eq 'plugins') {
        foreach ($sub in Get-ChildItem -LiteralPath $item.FullName) {
            $alvo = Join-Path $Servidor "plugins\$($sub.Name)"
            if (Test-Path -LiteralPath $alvo) { Remove-Item -LiteralPath $alvo -Recurse -Force }
            Move-Item -LiteralPath $sub.FullName -Destination $alvo
        }
    } else {
        $alvo = Join-Path $Servidor $item.Name
        if (Test-Path -LiteralPath $alvo) { Remove-Item -LiteralPath $alvo -Recurse -Force }
        Move-Item -LiteralPath $item.FullName -Destination $alvo
    }
}
Remove-Item -LiteralPath $temporaria -Recurse -Force
Write-Host "Restaurado: $($escolhido.Name)" -ForegroundColor Green
