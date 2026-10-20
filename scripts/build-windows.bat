@echo off
setlocal
where gradle >nul 2>nul
if errorlevel 1 (
  echo Gradle bulunamadi. Android Studio ile proje klasorunu acip Build APK secenegini kullanin.
  pause
  exit /b 1
)
gradle assembleDebug
if errorlevel 1 exit /b %errorlevel%
echo APK: app\build\outputs\apk\debug\app-debug.apk
pause
