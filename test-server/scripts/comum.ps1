# Funções compartilhadas pelos scripts do servidor de teste. Carregar com: . "$PSScriptRoot\comum.ps1"

$ErrorActionPreference = 'Stop'
$Servidor = Split-Path $PSScriptRoot -Parent
$Config = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'pluginhub.json') -Raw -Encoding UTF8 | ConvertFrom-Json

function Read-Propriedades {
    $mapa = @{}
    $arquivo = Join-Path $Servidor 'server.properties'
    if (-not (Test-Path -LiteralPath $arquivo)) { return $mapa }
    Get-Content -LiteralPath $arquivo -Encoding UTF8 |
        Where-Object { $_ -match '^([^#=]+)=(.*)$' } | ForEach-Object { $mapa[$Matches[1]] = $Matches[2] }
    $mapa
}

# Cliente RCON mínimo (protocolo do Source/Minecraft). Devolve $null se o servidor estiver desligado.
function Connect-Rcon {
    $props = Read-Propriedades
    if ($props['enable-rcon'] -ne 'true') { return $null }
    $tcp = New-Object Net.Sockets.TcpClient
    try {
        $tcp.Connect('127.0.0.1', [int]$props['rcon.port'])
    } catch {
        return $null
    }
    $rcon = [pscustomobject]@{ Tcp = $tcp; Stream = $tcp.GetStream(); Id = 0 }
    $rcon.Stream.ReadTimeout = 60000
    $resposta = Send-RconPacote $rcon 3 $props['rcon.password']
    if ($resposta.Id -eq -1) {
        $tcp.Close()
        throw 'Senha do RCON recusada. Confira rcon.password no server.properties.'
    }
    $rcon
}

function Send-RconPacote($rcon, [int]$tipo, [string]$corpo) {
    $rcon.Id++
    $bytes = [Text.Encoding]::UTF8.GetBytes($corpo)
    $tamanho = 10 + $bytes.Length
    $pacote = New-Object byte[] (4 + $tamanho)
    [BitConverter]::GetBytes([int]$tamanho).CopyTo($pacote, 0)
    [BitConverter]::GetBytes([int]$rcon.Id).CopyTo($pacote, 4)
    [BitConverter]::GetBytes([int]$tipo).CopyTo($pacote, 8)
    $bytes.CopyTo($pacote, 12)
    $rcon.Stream.Write($pacote, 0, $pacote.Length)

    $cabecalho = Read-RconBytes $rcon 4
    $resto = Read-RconBytes $rcon ([BitConverter]::ToInt32($cabecalho, 0))
    [pscustomobject]@{
        Id    = [BitConverter]::ToInt32($resto, 0)
        Corpo = [Text.Encoding]::UTF8.GetString($resto, 8, $resto.Length - 10)
    }
}

function Read-RconBytes($rcon, [int]$quantidade) {
    $buffer = New-Object byte[] $quantidade
    $lidos = 0
    while ($lidos -lt $quantidade) {
        $n = $rcon.Stream.Read($buffer, $lidos, $quantidade - $lidos)
        if ($n -le 0) { throw 'O servidor fechou a conexão RCON.' }
        $lidos += $n
    }
    $buffer
}

function Invoke-Rcon($rcon, [string]$comando) {
    (Send-RconPacote $rcon 2 $comando).Corpo
}

function Test-ServidorLigado {
    $rcon = Connect-Rcon
    if ($rcon) { $rcon.Tcp.Close(); return $true }
    $false
}
