# Cria (ou remove) uma tarefa no Agendador de Tarefas do Windows que roda o backup a cada X horas.
# Roda só com você logado e não precisa de administrador. Com o servidor desligado e nada mudado,
# o backup é pulado, então deixar ligado sempre não enche a pasta.
#
#   agendar-backup.bat              -> a cada 2 horas
#   agendar-backup.bat -Horas 1     -> a cada 1 hora
#   agendar-backup.bat -Remover     -> desliga o backup automático

param(
    [double]$Horas = 2,
    [switch]$Remover
)

. "$PSScriptRoot\comum.ps1"

$nome = 'PluginHub - backup do servidor de teste'

if ($Remover) {
    Unregister-ScheduledTask -TaskName $nome -Confirm:$false -ErrorAction SilentlyContinue
    Write-Host 'Backup automático desligado.'
    exit 0
}

$script = Join-Path $PSScriptRoot 'backup.ps1'
$acao = New-ScheduledTaskAction -Execute 'powershell.exe' `
    -Argument "-NoProfile -NonInteractive -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$script`" -Motivo agendado"
$gatilho = New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(5) -RepetitionInterval ([TimeSpan]::FromHours($Horas))
$opcoes = New-ScheduledTaskSettingsSet -StartWhenAvailable -DontStopIfGoingOnBatteries -AllowStartIfOnBatteries `
    -ExecutionTimeLimit ([TimeSpan]::FromMinutes(30)) -MultipleInstances IgnoreNew

Register-ScheduledTask -TaskName $nome -Action $acao -Trigger $gatilho -Settings $opcoes `
    -Description "Backup do mundo e dos dados dos plugins de $Servidor" -Force | Out-Null

Write-Host "Backup automático a cada $Horas hora(s), começando em 5 minutos." -ForegroundColor Green
Write-Host "Backups em: $($Config.backups) (mantém os $($Config.manterBackups) mais recentes)"
Write-Host "Histórico:  $(Join-Path $Config.backups 'backup.log')"
