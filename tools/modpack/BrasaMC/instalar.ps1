# Instala o modpack do BrasaMC na pasta .minecraft, para jogar por qualquer launcher (o oficial, o
# SKLauncher, o TLauncher...). Não baixa o Minecraft: isso continua sendo do launcher de cada um.
#   - a versão "BrasaMC" (Fabric, herda o 26.2) em versions\, e o perfil no launcher_profiles.json;
#   - os mods em mods\ e o shader em shaderpacks\, baixados do Modrinth e conferidos pela hash;
#   - o shader ligado e o servidor na lista, só se ainda não houver configuração.
# Os mods que já estavam em mods\ vão para mods-antes-do-brasamc\ (eles são de outras versões e
# derrubariam o jogo). Rodar de novo atualiza: o que era de uma versão anterior do modpack sai.
param([string]$Pasta = (Join-Path $env:APPDATA '.minecraft'))

$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$aqui = $PSScriptRoot
$utf8 = New-Object System.Text.UTF8Encoding($false)

function Gravar([string]$caminho, [string]$texto) {
    New-Item -ItemType Directory -Force (Split-Path $caminho) | Out-Null
    [IO.File]::WriteAllText($caminho, $texto, $utf8)
}

function Sha1([string]$caminho) {
    (Get-FileHash -Algorithm SHA1 -LiteralPath $caminho).Hash.ToLower()
}

Write-Host ''
Write-Host '  Instalando o BrasaMC em' $Pasta -ForegroundColor Cyan
New-Item -ItemType Directory -Force $Pasta | Out-Null
$indice = Get-Content -Raw -Encoding UTF8 (Join-Path $aqui 'modrinth.index.json') | ConvertFrom-Json
$registro = Join-Path $Pasta 'brasamc-instalado.json'
$antes = @()
if (Test-Path $registro) { $antes = @((Get-Content -Raw -Encoding UTF8 $registro | ConvertFrom-Json).arquivos) }

# 1. mods de fora vão pra outra pasta; os da versão anterior do modpack, que não estão mais na lista, saem
$mods = Join-Path $Pasta 'mods'
New-Item -ItemType Directory -Force $mods | Out-Null
$nossos = @($indice.files | ForEach-Object { $_.path })
$guardados = 0
foreach ($jar in Get-ChildItem -LiteralPath $mods -File) {
    $rel = "mods/$($jar.Name)"
    if ($nossos -contains $rel) { continue }
    if ($antes -contains $rel) { Remove-Item -LiteralPath $jar.FullName; continue }
    $guarda = Join-Path $Pasta 'mods-antes-do-brasamc'
    New-Item -ItemType Directory -Force $guarda | Out-Null
    Move-Item -LiteralPath $jar.FullName -Destination $guarda -Force
    $guardados++
}
foreach ($rel in $antes) {
    if ($nossos -notcontains $rel -and (Test-Path (Join-Path $Pasta $rel))) { Remove-Item -LiteralPath (Join-Path $Pasta $rel) }
}
if ($guardados) { Write-Host "  $guardados mod(s) que já estavam em mods foram para mods-antes-do-brasamc" -ForegroundColor Yellow }

# 2. mods e shader, do Modrinth
$web = New-Object System.Net.WebClient
$web.Headers.Add('User-Agent', 'BrasaMC-instalador')
foreach ($f in $indice.files) {
    $destino = Join-Path $Pasta ($f.path -replace '/', '\')
    if ((Test-Path $destino) -and (Sha1 $destino) -eq $f.hashes.sha1) { Write-Host "  ok      $($f.path)"; continue }
    New-Item -ItemType Directory -Force (Split-Path $destino) | Out-Null
    Write-Host "  baixando $($f.path)"
    $web.DownloadFile($f.downloads[0], "$destino.parte")
    if ((Sha1 "$destino.parte") -ne $f.hashes.sha1) {
        Remove-Item "$destino.parte"
        throw "O arquivo $($f.path) veio diferente do esperado. Tente de novo."
    }
    Move-Item "$destino.parte" $destino -Force
}
Gravar $registro (@{ versao = $indice.versionId; arquivos = $nossos } | ConvertTo-Json)

# 3. a versão do Fabric (o launcher baixa o 26.2 e as bibliotecas ao abrir)
$perfil = Get-Content -Raw -Encoding UTF8 (Join-Path $aqui 'versao.json')
$id = ($perfil | ConvertFrom-Json).id
Gravar (Join-Path $Pasta "versions\$id\$id.json") $perfil

# 4. o perfil no launcher oficial (os outros launchers listam a pasta versions)
$perfis = Join-Path $Pasta 'launcher_profiles.json'
$dados = if (Test-Path $perfis) { Get-Content -Raw -Encoding UTF8 $perfis | ConvertFrom-Json } else { [pscustomobject]@{ profiles = [pscustomobject]@{} } }
if (-not $dados.profiles) { $dados | Add-Member -Force profiles ([pscustomobject]@{}) }
$agora = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ss.fffZ')
$novo = [pscustomobject]@{ name = 'BrasaMC'; type = 'custom'; lastVersionId = $id; icon = 'Furnace'; created = $agora; lastUsed = $agora }
$dados.profiles | Add-Member -Force 'brasamc' $novo
Gravar $perfis ($dados | ConvertTo-Json -Depth 20)

# 5. shader ligado e servidor na lista, só na primeira vez (não mexe no que o jogador já escolheu)
$iris = Join-Path $Pasta 'config\iris.properties'
if (-not (Test-Path $iris)) { Gravar $iris (Get-Content -Raw (Join-Path $aqui 'iris.properties')) }
$servidores = Join-Path $Pasta 'servers.dat'
if (-not (Test-Path $servidores)) { Copy-Item (Join-Path $aqui 'servers.dat') $servidores }
else { Write-Host '  Você já tem uma lista de servidores: adicione o BrasaMC nela pelo jogo.' -ForegroundColor Yellow }

Write-Host ''
Write-Host "  Pronto! Abra o seu launcher, escolha a versão $id (ou o perfil BrasaMC) e jogue." -ForegroundColor Green
Write-Host '  Na primeira vez, o launcher baixa o Minecraft 26.2 e o Fabric, e demora um pouco mais.'
Write-Host ''
