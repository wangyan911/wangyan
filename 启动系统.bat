@echo off
chcp 65001 >nul
cd /d "%~dp0"
where java >nul 2>nul
if errorlevel 1 (
  echo 未找到 Java 17。请先安装 JDK 17 并加入 PATH。
  pause
  exit /b 1
)
if not exist target\equipment-manager-0.1.0-SNAPSHOT.jar (
  echo 未找到 JAR，请先在项目根目录运行 mvn clean package。
  pause
  exit /b 1
)
java -jar target\equipment-manager-0.1.0-SNAPSHOT.jar
pause
