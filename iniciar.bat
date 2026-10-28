@echo off
cd /d "%~dp0backend"
where java >nul 2>&1
if errorlevel 1 (
  echo No se encontro Java. Instala JDK 17 o 21 y configura JAVA_HOME y PATH.
  pause
  exit /b 1
)
where mvn >nul 2>&1
if errorlevel 1 (
  echo No se encontro Maven. Instala Maven 3.9 y agrega su carpeta bin al PATH.
  pause
  exit /b 1
)
if not exist config\application-local.properties (
  echo Copia config\application-local.properties.example como application-local.properties.
  echo Configura tu usuario y clave de PostgreSQL antes de iniciar.
  pause
  exit /b 1
)
echo Iniciando El Sabor Iqueno...
echo Cuando aparezca Started SaborIquenoApplication abre http://localhost:8080/login.html
call mvn spring-boot:run
pause
