@echo off
rem Instala o modpack do BrasaMC na pasta .minecraft (veja instalar.ps1).
rem Aberto de dentro do zip (WinRAR, 7-Zip), o .bat sai sozinho numa pasta temporária, sem o resto.
if not exist "%~dp0instalar.ps1" (
    echo.
    echo   Extraia o zip antes: botao direito no zip, "Extrair tudo" ou "Extrair aqui",
    echo   e de dois cliques no "Instalar BrasaMC" da pasta extraida.
    echo.
    pause
    exit /b 1
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0instalar.ps1" %*
pause
