# Liga o servidor de teste. Quando ele desliga (stop), faz um backup.

. "$PSScriptRoot\comum.ps1"

if (Test-ServidorLigado) {
    Write-Host 'O servidor já está ligado.' -ForegroundColor Yellow
    exit 1
}

Set-Location -LiteralPath $Servidor
# Sem o JLine no console: no Windows, ele às vezes trava o stop (o gancho de desligamento dele
# espera a thread de log, presa escrevendo no console). O console continua aceitando comandos,
# só perde o autocompletar com Tab; terminal.ansi mantém as cores.
& $Config.java "-Xms2G" "-Xmx$($Config.memoria)" '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' `
    '-Dterminal.jline=false' '-Dterminal.ansi=true' -jar paper.jar --nogui
$codigo = $LASTEXITCODE

Write-Host ''
if ($codigo -ne 0) {
    Write-Host "O servidor saiu com código $codigo (crash?). Fazendo backup do estado atual mesmo assim." -ForegroundColor Yellow
    & "$PSScriptRoot\backup.ps1" -Motivo crash
} else {
    & "$PSScriptRoot\backup.ps1" -Motivo desligamento
}
