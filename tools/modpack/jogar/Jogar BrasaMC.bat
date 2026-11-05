@echo off
rem Dois cliques e joga (conta original): na primeira vez importa o modpack no Prism portátil desta pasta,
rem que baixa o Minecraft, o Fabric, os mods e o shader; depois, abre o jogo já entrando no servidor.
cd /d "%~dp0"
rem Aberto de dentro do zip, o .bat sai sozinho numa pasta temporária, sem o launcher.
if not exist "launcher\prismlauncher.exe" (
    echo.
    echo   Extraia o zip antes: botao direito no zip, "Extrair tudo" ou "Extrair aqui",
    echo   e de dois cliques no "Jogar BrasaMC" da pasta extraida.
    echo.
    pause
    exit /b 1
)
if not exist "launcher\instances\BrasaMC\" (
    start "" "launcher\prismlauncher.exe" --import "%~dp0BrasaMC.mrpack"
) else (
    start "" "launcher\prismlauncher.exe" --launch BrasaMC --server __SERVIDOR__
)
