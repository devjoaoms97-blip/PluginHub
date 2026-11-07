@echo off
rem O bot do Discord oficial, numa janela propria (fora do Claude Code, que encerra as tarefas dele
rem quando falta memoria). Se o bot cair, liga de novo em 10 s. Pra desligar, feche a janela.
title Bot do Discord - BrasaMC (oficial)
cd /d "%~dp0"
:ligar
call npm run oficial
echo.
echo O bot parou. Ligando de novo em 10 s (feche a janela pra desligar)...
timeout /t 10 /nobreak >nul
goto ligar
