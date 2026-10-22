# Copia os jars compilados do repositório (*/target/*.jar) pra pasta plugins.
# Rode com o servidor desligado: trocar um jar com ele ligado pode corromper o plugin carregado.

. "$PSScriptRoot\comum.ps1"

if (Test-ServidorLigado) {
    Write-Host 'Desligue o servidor (stop) antes de atualizar os plugins.' -ForegroundColor Yellow
    exit 1
}

$jars = Get-ChildItem -LiteralPath $Config.repo -Recurse -Filter '*.jar' -File |
    Where-Object { $_.Directory.Name -eq 'target' }
if (-not $jars) {
    Write-Host "Nenhum jar compilado em $($Config.repo). Rode mvn package nos plugins primeiro." -ForegroundColor Yellow
    exit 1
}
foreach ($jar in $jars) {
    Copy-Item -LiteralPath $jar.FullName -Destination (Join-Path $Servidor 'plugins') -Force
    Write-Host "  plugin: $($jar.Name)"
}
