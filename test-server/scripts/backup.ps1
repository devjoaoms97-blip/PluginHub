# Backup do mundo e dos dados dos plugins num .zip com data e hora.
#
# Com o servidor ligado, usa o RCON pra pausar o salvamento (save-off), forçar tudo pro disco
# (save-all flush), copiar e religar (save-on). Desligado, só copia, e pula se nada mudou desde
# o último backup. Os jars não entram (o montar.bat baixa e o atualizar-plugins.bat copia).

param(
    [string]$Motivo = 'manual',
    [switch]$Forcar
)

. "$PSScriptRoot\comum.ps1"

$pastaBackups = $Config.backups
New-Item -ItemType Directory -Force -Path $pastaBackups | Out-Null
$log = Join-Path $pastaBackups 'backup.log'
function Write-Log([string]$texto) {
    $linha = "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') [$Motivo] $texto"
    Write-Host $linha
    Add-Content -LiteralPath $log -Value $linha -Encoding UTF8
}

# O que entra no backup: mundos (world, world_nether...), plugins (sem os jars) e os arquivos do servidor.
$itens = @(Get-ChildItem -LiteralPath $Servidor -Directory -Filter 'world*') +
         @(Get-Item -LiteralPath (Join-Path $Servidor 'plugins') -ErrorAction SilentlyContinue) +
         @(Get-Item -LiteralPath (Join-Path $Servidor 'config') -ErrorAction SilentlyContinue) +
         @(Get-ChildItem -LiteralPath $Servidor -File | Where-Object { $_.Extension -in '.json', '.properties', '.yml' })
$itens = $itens | Where-Object { $_ }
if (-not $itens) {
    Write-Log 'Nada pra copiar (o servidor ainda não foi ligado?).'
    exit 0
}

$rcon = Connect-Rcon
try {
    if ($rcon) {
        Invoke-Rcon $rcon 'save-off' | Out-Null
        Invoke-Rcon $rcon 'save-all flush' | Out-Null
    } elseif (-not $Forcar) {
        $ultimo = Get-ChildItem -LiteralPath $pastaBackups -Filter '*.zip' | Sort-Object LastWriteTime -Descending | Select-Object -First 1
        if ($ultimo) {
            $mudou = $itens | ForEach-Object {
                if ($_.PSIsContainer) { Get-ChildItem -LiteralPath $_.FullName -Recurse -File -Exclude '*.jar' } else { $_ }
            } | Where-Object { $_.LastWriteTime -gt $ultimo.LastWriteTime } | Select-Object -First 1
            if (-not $mudou) {
                Write-Log "Servidor desligado e nada mudou desde $($ultimo.Name). Pulando."
                exit 0
            }
        }
    }

    $nome = "backup-$(Get-Date -Format 'yyyy-MM-dd_HH-mm-ss')-$Motivo"
    $temporaria = Join-Path $env:TEMP "pluginhub-$nome"
    New-Item -ItemType Directory -Force -Path $temporaria | Out-Null
    try {
        foreach ($item in $itens) {
            if ($item.PSIsContainer) {
                # session.lock fica travado pelo servidor; jars, remapeados do Paper e libs do LuckPerms são regeneráveis.
                robocopy $item.FullName (Join-Path $temporaria $item.Name) /E /R:2 /W:1 /NFL /NDL /NJH /NJS /NP `
                    /XF session.lock *.jar /XD .paper-remapped libs | Out-Null
                if ($LASTEXITCODE -ge 8) { throw "robocopy falhou em $($item.Name) (código $LASTEXITCODE)" }
            } else {
                Copy-Item -LiteralPath $item.FullName -Destination $temporaria
            }
        }
    } finally {
        if ($rcon) { Invoke-Rcon $rcon 'save-on' | Out-Null }
    }

    # Zip montado à mão: o CreateFromDirectory do .NET Framework grava "\" nos caminhos, e o 7-Zip
    # e o Linux leem isso como parte do nome do arquivo.
    Add-Type -AssemblyName System.IO.Compression, System.IO.Compression.FileSystem
    $zip = Join-Path $pastaBackups "$nome.zip"
    $arquivo = [IO.Compression.ZipFile]::Open($zip, [IO.Compression.ZipArchiveMode]::Create)
    try {
        $raiz = (Get-Item -LiteralPath $temporaria).FullName.TrimEnd([char]92) + [char]92
        foreach ($f in Get-ChildItem -LiteralPath $temporaria -Recurse -File) {
            $relativo = $f.FullName.Substring($raiz.Length).Replace([char]92, [char]47)
            [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($arquivo, $f.FullName, $relativo,
                [IO.Compression.CompressionLevel]::Optimal) | Out-Null
        }
    } finally {
        $arquivo.Dispose()
    }
    Remove-Item -LiteralPath $temporaria -Recurse -Force

    $tamanho = '{0:N1} MB' -f ((Get-Item -LiteralPath $zip).Length / 1MB)
    Write-Log "Backup criado: $nome.zip ($tamanho)$(if ($rcon) { ', com o servidor ligado' })"
    if ($rcon) { Invoke-Rcon $rcon "say Backup do mundo concluído ($tamanho)." | Out-Null }

    # Mantém só os N mais recentes.
    Get-ChildItem -LiteralPath $pastaBackups -Filter 'backup-*.zip' | Sort-Object LastWriteTime -Descending |
        Select-Object -Skip ([int]$Config.manterBackups) |
        ForEach-Object { Remove-Item -LiteralPath $_.FullName; Write-Log "Apagado (antigo): $($_.Name)" }
} catch {
    Write-Log "ERRO: $($_.Exception.Message)"
    exit 1
} finally {
    if ($rcon) { $rcon.Tcp.Close() }
}

# Sem isso, o código de saída seria o do último robocopy (1 = "copiou arquivos").
exit 0
