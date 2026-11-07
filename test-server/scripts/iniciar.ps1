# Liga o servidor. Quando ele desliga (stop), faz um backup e para. Se ele cair (crash: o Java saiu com
# erro), faz o backup e liga de novo em 10 s; 3 quedas em 10 minutos e ele desiste (algo está errado,
# e religar em loop só piora). Pra não religar, Ctrl+C na contagem.

. "$PSScriptRoot\comum.ps1"

if (Test-ServidorLigado) {
    Write-Host 'O servidor já está ligado.' -ForegroundColor Yellow
    exit 1
}

Set-Location -LiteralPath $Servidor
$quedas = New-Object System.Collections.Generic.List[datetime]

while ($true) {
    # Sem o JLine no console: no Windows, ele às vezes trava o stop (o gancho de desligamento dele
    # espera a thread de log, presa escrevendo no console). O console continua aceitando comandos,
    # só perde o autocompletar com Tab; terminal.ansi mantém as cores.
    & $Config.java "-Xms2G" "-Xmx$($Config.memoria)" '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' `
        '-Dterminal.jline=false' '-Dterminal.ansi=true' -jar paper.jar --nogui
    $codigo = $LASTEXITCODE

    Write-Host ''
    if ($codigo -eq 0) {
        & "$PSScriptRoot\backup.ps1" -Motivo desligamento
        break
    }

    Write-Host "O servidor caiu (código $codigo). Fazendo backup do estado atual." -ForegroundColor Yellow
    & "$PSScriptRoot\backup.ps1" -Motivo crash
    $agora = Get-Date
    $quedas.Add($agora)
    $quedas.RemoveAll({ param($q) ($agora - $q).TotalMinutes -gt 10 }) | Out-Null
    if ($quedas.Count -ge 3) {
        Write-Host '3 quedas em 10 minutos: não vou religar. Veja o logs\latest.log e os crash-reports.' -ForegroundColor Red
        break
    }
    Write-Host 'Ligando de novo em 10 s (Ctrl+C pra não religar)...' -ForegroundColor Yellow
    Start-Sleep -Seconds 10
}
