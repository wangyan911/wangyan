# Requires JDK 17+ (jpackage), Maven 3.8+, Windows x64.
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
mvn clean package
$jar = Join-Path $PSScriptRoot 'target\equipment-manager-0.1.0-SNAPSHOT.jar'
$out = Join-Path $PSScriptRoot 'target\installer'
New-Item -ItemType Directory -Force -Path $out | Out-Null
jpackage --type exe --name SWUEquipmentManager --app-version 0.1.0 --vendor '西南大学附属中学' --description '器材室局域网管理系统' --input (Join-Path $PSScriptRoot 'target') --main-jar 'equipment-manager-0.1.0-SNAPSHOT.jar' --dest $out --win-menu --win-shortcut
Write-Host "Installer created under $out"
