# Monta (ou atualiza) o servidor de teste do PluginHub numa pasta fora do repositório.
# Pode rodar de novo quantas vezes quiser: baixa só o que falta, mantém mundo, dados e configs.
#
#   montar.bat                          -> monta em C:\mc-teste
#   montar.bat -Destino D:\outro        -> monta em outra pasta
#   montar.bat -SobrescreverConfigs     -> volta os configs de teste aos daqui do repositório

param(
    [string]$Destino = 'C:\mc-teste',
    [switch]$SobrescreverConfigs
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$base = $PSScriptRoot
$repo = Split-Path $base -Parent

# Versões fixas: trocar aqui (URL + sha256) pra atualizar o servidor de teste.
# "remover" apaga as versões antigas do mesmo plugin na pasta plugins.
$downloads = @(
    @{ arquivo = 'paper.jar'
       url = 'https://fill-data.papermc.io/v1/objects/3cce0a4922baf79bd8e1275ee5ff9f073f24675770e09b0d4be9442d513e7a6b/paper-26.2-134.jar'
       sha256 = '3cce0a4922baf79bd8e1275ee5ff9f073f24675770e09b0d4be9442d513e7a6b' },
    @{ arquivo = 'plugins\VaultUnlocked-2.20.3.jar'; remover = 'VaultUnlocked-*.jar'
       url = 'https://cdn.modrinth.com/data/ayRaM8J7/versions/qZgRzoYs/VaultUnlocked-2.20.3.jar'
       sha256 = 'fbc6651aca11e13376c115df66eeffa79592e79517be398299aa6beb49c22695' },
    @{ arquivo = 'plugins\EssentialsX-2.22.0.jar'; remover = 'EssentialsX-*.jar'
       url = 'https://cdn.modrinth.com/data/hXiIvTyT/versions/nY6VN1XH/EssentialsX-2.22.0.jar'
       sha256 = 'bda4685105977fca2e209820a9f0ad24275bd103390a03236f38e59bfdac58e6' },
    @{ arquivo = 'plugins\LuckPerms-Bukkit-5.5.71.jar'; remover = 'LuckPerms-Bukkit-*.jar'
       url = 'https://cdn.modrinth.com/data/Vebnzrzj/versions/b0mk8uS6/LuckPerms-Bukkit-5.5.71.jar'
       sha256 = '49cecb66fa1fd22a133039a490e9c1e5095a238e7cd66eb9d2a16fe6c897550d' }
)

function Get-Sha256([string]$caminho) {
    (Get-FileHash -Algorithm SHA256 -LiteralPath $caminho).Hash.ToLower()
}

function Find-Java {
    $candidatos = @()
    if ($env:JAVA_HOME) { $candidatos += Join-Path $env:JAVA_HOME 'bin\java.exe' }
    $candidatos += Get-ChildItem 'C:\Program Files\Eclipse Adoptium', 'C:\Program Files\Java', 'C:\Program Files\Microsoft' `
        -Directory -Filter 'jdk-2*' -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending | ForEach-Object { Join-Path $_.FullName 'bin\java.exe' }
    $doPath = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($doPath) { $candidatos += $doPath.Source }

    foreach ($java in $candidatos) {
        if (-not (Test-Path -LiteralPath $java)) { continue }
        $versao = (cmd /c "`"$java`" -version 2>&1" | Select-Object -First 1)
        if ($versao -match 'version "(\d+)' -and [int]$Matches[1] -ge 25) { return $java }
    }
    throw 'Java 25 ou mais novo não encontrado. Instale o Temurin 25 (adoptium.net) e rode de novo.'
}

# Troca (ou acrescenta) chaves do server.properties sem mexer no resto.
function Set-Propriedades([string]$arquivo, [hashtable]$valores) {
    $linhas = [System.Collections.Generic.List[string]]::new()
    if (Test-Path -LiteralPath $arquivo) {
        Get-Content -LiteralPath $arquivo -Encoding UTF8 | ForEach-Object { $linhas.Add($_) }
    }
    foreach ($chave in $valores.Keys) {
        $linha = "$chave=$($valores[$chave])"
        $i = -1
        for ($j = 0; $j -lt $linhas.Count; $j++) {
            if ($linhas[$j] -like "$chave=*") { $i = $j; break }
        }
        if ($i -ge 0) { $linhas[$i] = $linha } else { $linhas.Add($linha) }
    }
    [IO.File]::WriteAllLines($arquivo, $linhas, (New-Object Text.UTF8Encoding $false))
}

function Read-Propriedades([string]$arquivo) {
    $mapa = @{}
    if (Test-Path -LiteralPath $arquivo) {
        Get-Content -LiteralPath $arquivo -Encoding UTF8 | Where-Object { $_ -match '^([^#=]+)=(.*)$' } |
            ForEach-Object { $mapa[$Matches[1]] = $Matches[2] }
    }
    $mapa
}

Write-Host "Montando o servidor de teste em $Destino" -ForegroundColor Cyan
New-Item -ItemType Directory -Force -Path (Join-Path $Destino 'plugins'), (Join-Path $Destino 'scripts') | Out-Null

$java = Find-Java
Write-Host "Java: $java"

# 1. Paper e plugins de terceiros
foreach ($d in $downloads) {
    $alvo = Join-Path $Destino $d.arquivo
    if ($d.remover) {
        Get-ChildItem (Join-Path $Destino 'plugins') -Filter $d.remover -File |
            Where-Object { $_.Name -ne (Split-Path $alvo -Leaf) } |
            ForEach-Object { Write-Host "  removendo versão antiga: $($_.Name)"; Remove-Item -LiteralPath $_.FullName }
    }
    if ((Test-Path -LiteralPath $alvo) -and (Get-Sha256 $alvo) -eq $d.sha256) {
        Write-Host "  ok: $($d.arquivo)"
        continue
    }
    Write-Host "  baixando: $($d.arquivo)"
    $tmp = "$alvo.baixando"
    Invoke-WebRequest -Uri $d.url -OutFile $tmp -UseBasicParsing
    $hash = Get-Sha256 $tmp
    if ($hash -ne $d.sha256) {
        Remove-Item -LiteralPath $tmp
        throw "Hash diferente em $($d.arquivo): esperado $($d.sha256), veio $hash"
    }
    Move-Item -LiteralPath $tmp -Destination $alvo -Force
}

# 2. Scripts e atalhos (sempre a versão do repositório)
Copy-Item (Join-Path $base 'scripts\*') (Join-Path $Destino 'scripts') -Force
Copy-Item (Join-Path $base 'atalhos\*') $Destino -Force

# 3. Configuração dos scripts
$arquivoConfig = Join-Path $Destino 'scripts\pluginhub.json'
$config = [ordered]@{ repo = $repo; java = $java; memoria = '4G'; backups = (Join-Path $Destino 'backups'); manterBackups = 20 }
if (Test-Path -LiteralPath $arquivoConfig) {
    $antigo = Get-Content -LiteralPath $arquivoConfig -Raw -Encoding UTF8 | ConvertFrom-Json
    foreach ($chave in 'memoria', 'backups', 'manterBackups') {
        if ($antigo.$chave) { $config[$chave] = $antigo.$chave }
    }
}
[IO.File]::WriteAllText($arquivoConfig, ($config | ConvertTo-Json), (New-Object Text.UTF8Encoding $false))

# 4. EULA e server.properties. Rodar este script = aceitar o EULA do Minecraft (aka.ms/MinecraftEULA).
[IO.File]::WriteAllText((Join-Path $Destino 'eula.txt'), "eula=true`n")
$props = Join-Path $Destino 'server.properties'
$valores = @{}
Get-Content (Join-Path $base 'server.properties') -Encoding UTF8 |
    Where-Object { $_ -match '^([^#=]+)=(.*)$' } | ForEach-Object { $valores[$Matches[1]] = $Matches[2] }
$senhaAtual = (Read-Propriedades $props)['rcon.password']
if ($senhaAtual) {
    $valores['rcon.password'] = $senhaAtual
} else {
    $valores['rcon.password'] = -join ((48..57) + (65..90) + (97..122) | Get-Random -Count 24 | ForEach-Object { [char]$_ })
}
Set-Propriedades $props $valores

# 5. Nossos plugins
& (Join-Path $Destino 'scripts\atualizar-plugins.ps1')

# 6. Configs de teste dos plugins, com subpastas (só cria; -SobrescreverConfigs troca os que já existem).
#    Inclui os grupos de VIP do LuckPerms (configs\LuckPerms\yaml-storage\groups), que dão os perks do /vip.
$configs = Join-Path $base 'configs'
Get-ChildItem $configs -File -Recurse | ForEach-Object {
    $relativo = $_.FullName.Substring($configs.Length + 1)
    $alvo = Join-Path $Destino "plugins\$relativo"
    if ($SobrescreverConfigs -or -not (Test-Path -LiteralPath $alvo)) {
        New-Item -ItemType Directory -Force -Path (Split-Path $alvo) | Out-Null
        Copy-Item -LiteralPath $_.FullName -Destination $alvo -Force
        Write-Host "  config: $relativo"
    }
}

# 7. LuckPerms em YAML: o banco H2 padrão fica travado com o servidor ligado e o backup não consegue copiar.
$lp = Join-Path $Destino 'plugins\LuckPerms\config.yml'
if (-not (Test-Path -LiteralPath $lp)) {
    New-Item -ItemType Directory -Force -Path (Split-Path $lp) | Out-Null
    [IO.File]::WriteAllText($lp, "storage-method: yaml`n")
    Write-Host '  config: LuckPerms (storage-method: yaml)'
} elseif ((Get-Content -LiteralPath $lp -Raw) -match '(?m)^storage-method: h2') {
    $texto = (Get-Content -LiteralPath $lp -Raw -Encoding UTF8) -replace '(?m)^storage-method: h2', 'storage-method: yaml'
    [IO.File]::WriteAllText($lp, $texto, (New-Object Text.UTF8Encoding $false))
    Write-Host '  config: LuckPerms trocado de h2 pra yaml (grupos e permissões antigos ficam no .mv.db e não são migrados)' -ForegroundColor Yellow
}

Write-Host ''
Write-Host 'Pronto!' -ForegroundColor Green
Write-Host "  Ligar:             $Destino\iniciar.bat"
Write-Host "  Backup manual:     $Destino\backup.bat"
Write-Host "  Restaurar backup:  $Destino\restaurar.bat"
Write-Host "  Backup automático: $Destino\agendar-backup.bat"
