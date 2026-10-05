@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul
title Instalador de AutoTorque

rem ============================================================
rem  Descarga la ultima version de AutoTorque desde GitHub y la
rem  instala en el telefono conectado por USB (depuracion USB).
rem ============================================================

set "REPO=flavioteuy/auto-torque"
set "APK_URL=https://github.com/%REPO%/releases/latest/download/AutoTorque.apk"
set "PT_URL=https://dl.google.com/android/repository/platform-tools-latest-windows.zip"
set "WORK=%LOCALAPPDATA%\AutoTorque"
set "APK=%WORK%\AutoTorque.apk"
set "PKG=uy.autotorque"

echo ============================================
echo    Instalador de AutoTorque  (USB / adb)
echo ============================================
echo.

if not exist "%WORK%" mkdir "%WORK%"

rem ---------- 1) Conseguir adb ----------
set "ADB="
for /f "delims=" %%a in ('where adb 2^>nul') do if not defined ADB set "ADB=%%a"
if not defined ADB if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not defined ADB if exist "%WORK%\platform-tools\adb.exe" set "ADB=%WORK%\platform-tools\adb.exe"

if defined ADB goto :adb_ok
echo [1/4] No encontré adb. Descargando Android Platform Tools de Google...
call :descargar "%PT_URL%" "%WORK%\platform-tools.zip"
if errorlevel 1 goto :error_adb
tar -xf "%WORK%\platform-tools.zip" -C "%WORK%" >nul 2>&1
if not exist "%WORK%\platform-tools\adb.exe" powershell -NoProfile -Command "Expand-Archive -Force -LiteralPath '%WORK%\platform-tools.zip' -DestinationPath '%WORK%'"
del "%WORK%\platform-tools.zip" >nul 2>&1
if not exist "%WORK%\platform-tools\adb.exe" goto :error_adb
set "ADB=%WORK%\platform-tools\adb.exe"

:adb_ok
echo [1/4] adb listo: "%ADB%"

rem ---------- 2) Descargar el APK ----------
echo.
echo [2/4] Descargando la última versión de AutoTorque desde GitHub...
if exist "%APK%" del "%APK%" >nul 2>&1
call :descargar "%APK_URL%" "%APK%"
if errorlevel 1 goto :error_apk
if not exist "%APK%" goto :error_apk
for %%F in ("%APK%") do if %%~zF LSS 100000 goto :error_apk
echo       Descargado.

rem ---------- 3) Esperar el telefono ----------
echo.
echo [3/4] Conectá el teléfono por USB con la "Depuración USB" activada.
"%ADB%" start-server >nul 2>&1
set /a INTENTOS=0
set "ULTIMO="

:esperar
"%ADB%" get-state >"%WORK%\estado.txt" 2>&1
set "ESTADO="
set /p ESTADO=<"%WORK%\estado.txt"
if /i "%ESTADO%"=="device" goto :conectado

set "MSG=Esperando el teléfono... (¿cable de datos? ¿depuración USB activada?)"
findstr /i "unauthorized" "%WORK%\estado.txt" >nul && set "MSG=Aceptá el aviso 'Permitir depuración USB' en la pantalla del teléfono."
findstr /i /c:"more than one" "%WORK%\estado.txt" >nul && set "MSG=Hay más de un teléfono o emulador conectado. Dejá solo uno."
findstr /i "offline" "%WORK%\estado.txt" >nul && set "MSG=El teléfono aparece desconectado. Desenchufá y volvé a enchufar el cable."
if not "!MSG!"=="!ULTIMO!" (
    echo       !MSG!
    set "ULTIMO=!MSG!"
)
set /a INTENTOS+=1
if %INTENTOS% GEQ 120 goto :error_telefono
timeout /t 2 /nobreak >nul
goto :esperar

:conectado
echo       Teléfono conectado.
"%ADB%" shell pm path org.prowl.torque >"%WORK%\torque.txt" 2>&1
findstr /c:"package:" "%WORK%\torque.txt" >nul || echo       Aviso: no encontré Torque Pro en el teléfono. AutoTorque lo necesita para funcionar.

rem ---------- 4) Instalar ----------
echo.
echo [4/4] Instalando AutoTorque...
"%ADB%" install -r "%APK%" >"%WORK%\install.txt" 2>&1
findstr /c:"Success" "%WORK%\install.txt" >nul && goto :instalado
findstr /c:"INSTALL_FAILED_UPDATE_INCOMPATIBLE" /c:"signatures do not match" "%WORK%\install.txt" >nul && goto :firma
findstr /c:"INSTALL_FAILED_USER_RESTRICTED" "%WORK%\install.txt" >nul && goto :restringido
goto :error_install

:firma
echo.
echo La versión que ya tenés instalada tiene otra firma. Para instalar esta hay que
echo desinstalar la anterior (se pierde la selección de sensores).
choice /c SN /m "¿Desinstalar y volver a instalar?"
if errorlevel 2 goto :salir_error
"%ADB%" uninstall %PKG% >nul 2>&1
"%ADB%" install "%APK%" >"%WORK%\install.txt" 2>&1
findstr /c:"Success" "%WORK%\install.txt" >nul && goto :instalado
goto :error_install

:restringido
echo.
echo El teléfono bloqueó la instalación por USB.
echo  - En Xiaomi/Redmi/POCO: Opciones de desarrollador, activá "Instalar vía USB".
echo  - En otros: aceptá el aviso de instalación que aparece en la pantalla del teléfono.
echo Después volvé a ejecutar este archivo.
goto :salir_error

:instalado
echo.
echo ============================================
echo    AutoTorque se instaló correctamente.
echo ============================================
"%ADB%" shell monkey -p %PKG% -c android.intent.category.LAUNCHER 1 >nul 2>&1
echo La app se abrió en el teléfono.
echo.
echo Para verla en el auto, una sola vez: en Android Auto tocá "Versión" unas 10 veces,
echo después menú ^> Configuración para desarrolladores ^> activá "Fuentes desconocidas".
echo.
pause
exit /b 0

rem ---------- Errores ----------
:error_adb
echo.
echo ERROR: no se pudo descargar adb (Android Platform Tools).
echo Revisá la conexión a internet y volvé a intentar.
goto :salir_error

:error_apk
echo.
echo ERROR: no se pudo descargar AutoTorque desde:
echo   %APK_URL%
echo Puede que GitHub todavía esté compilando la primera versión. Probá de nuevo en unos minutos.
goto :salir_error

:error_telefono
echo.
echo ERROR: no se detectó el teléfono.
echo  1. Ajustes ^> Acerca del teléfono ^> tocá 7 veces "Número de compilación".
echo  2. Ajustes ^> Opciones de desarrollador ^> activá "Depuración USB".
echo  3. Usá un cable USB de datos (algunos solo cargan) y aceptá el aviso en el teléfono.
goto :salir_error

:error_install
echo.
echo ERROR: la instalación falló. Respuesta del teléfono:
type "%WORK%\install.txt"
goto :salir_error

:salir_error
echo.
pause
exit /b 1

rem ---------- Descarga: usa curl de Windows y, si no está, PowerShell ----------
:descargar
where curl >nul 2>&1
if errorlevel 1 goto :descargar_ps
curl -L -f -# --retry 2 -o "%~2" "%~1"
if not errorlevel 1 exit /b 0
:descargar_ps
powershell -NoProfile -Command "$ProgressPreference='SilentlyContinue'; [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -UseBasicParsing -Uri '%~1' -OutFile '%~2'"
if errorlevel 1 exit /b 1
exit /b 0
