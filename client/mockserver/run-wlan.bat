@echo off
setlocal
set "IP="

for /f "tokens=1,* delims=:" %%A in ('netsh interface ipv4 show addresses "WLAN"') do (
    echo %%A | findstr /i /c:"IP 地址" /c:"IP Address" >nul && (
        for /f "tokens=* delims= " %%I in ("%%B") do set "IP=%%I"
    )
)

if not defined IP (
    echo 未获取到 WLAN 的 IPv4 地址。
    exit /b 1
)

set "PUBLIC_BASE=http://%IP%:8787"
echo PUBLIC_BASE=%PUBLIC_BASE%

endlocal & set "PUBLIC_BASE=%PUBLIC_BASE%"

node server.mjs