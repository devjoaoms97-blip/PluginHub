# Abre ou fecha o servidor para quem está fora de casa, pelo túnel do playit.gg (o provedor usa CGNAT,
# então redirecionar porta no roteador não funciona). O servidor continua escutando só em 127.0.0.1:
# o serviço playitd é quem leva as conexões de fora até ele, e só enquanto estiver ligado.
# Junto com o túnel, liga/desliga a whitelist: com online-mode=false, qualquer um entraria com o nick de um OP.

param(
    [Parameter(Mandatory)][ValidateSet('abrir', 'fechar')][string]$Acao,
    [switch]$SoServico
)

. "$PSScriptRoot\comum.ps1"

$servico = 'playitd'

if ($SoServico) {
    # Ligar/desligar serviço precisa de administrador; roda numa janela elevada aberta pelo próprio script.
    if ($Acao -eq 'abrir') { Start-Service $servico } else { Stop-Service $servico }
    exit 0
}

if (-not (Get-Service $servico -ErrorAction SilentlyContinue)) {
    throw 'O playit não está instalado. Veja "Jogar com amigos de fora" no README do test-server.'
}

# 1. Whitelist antes de abrir (e só depois de fechar), para não ficar exposto nem por um instante.
function Set-Whitelist([bool]$ligada) {
    $rcon = Connect-Rcon
    if ($rcon) {
        try {
            Invoke-Rcon $rcon $(if ($ligada) { 'whitelist on' } else { 'whitelist off' }) | Out-Null
            if ($ligada) { Write-Host "Whitelist: $(Invoke-Rcon $rcon 'whitelist list')" }
        } finally {
            $rcon.Tcp.Close()
        }
    } else {
        # Desligado: o servidor lê a whitelist do server.properties quando ligar.
        $arquivo = Join-Path $Servidor 'server.properties'
        $valor = if ($ligada) { 'true' } else { 'false' }
        $linhas = Get-Content -LiteralPath $arquivo -Encoding UTF8 |
            ForEach-Object { if ($_ -like 'white-list=*') { "white-list=$valor" } else { $_ } }
        [IO.File]::WriteAllLines($arquivo, [string[]]$linhas, (New-Object Text.UTF8Encoding $false))
        if ($ligada) {
            $nomes = (Get-Content -LiteralPath (Join-Path $Servidor 'whitelist.json') -Raw -ErrorAction SilentlyContinue |
                ConvertFrom-Json) | ForEach-Object { $_.name }
            Write-Host "Whitelist: $(if ($nomes) { $nomes -join ', ' } else { '(vazia)' })"
        }
    }
}

if ($Acao -eq 'abrir') { Set-Whitelist $true }

# 2. Serviço do playit, numa janela de administrador (o Windows pede confirmação).
$processo = Start-Process powershell -Verb RunAs -Wait -PassThru -WindowStyle Hidden -ArgumentList `
    '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', "`"$PSCommandPath`"", '-Acao', $Acao, '-SoServico'
if ($processo.ExitCode -ne 0) {
    throw "Não foi possível mudar o serviço do playit (código $($processo.ExitCode))."
}

if ($Acao -eq 'fechar') { Set-Whitelist $false }

if ($Acao -eq 'abrir') {
    Write-Host ''
    Write-Host 'Túnel ABERTO. O endereço para o seu amigo está em https://playit.gg/account/tunnels' -ForegroundColor Green
    Write-Host 'Só entra quem está na whitelist (no console: whitelist add <nick>).'
    Write-Host 'Quando terminarem, rode fechar-porta.bat.' -ForegroundColor Yellow
} else {
    Write-Host 'Túnel FECHADO: só este PC alcança o servidor. Whitelist desligada.' -ForegroundColor Green
}
