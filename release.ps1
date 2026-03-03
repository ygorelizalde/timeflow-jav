param(
    [Parameter(Mandatory=$true)][string]$Versao,
    [Parameter(Mandatory=$false)][string]$Descricao = "Time Flow v$Versao"
)
$ErrorActionPreference = "Stop"
$REPO = "ygorelizalde/timeflow-jav"
$TAG = "v$Versao"
$env:PATH += ";C:\Program Files\GitHub CLI"

Write-Host "[1/6] Compilando..." -ForegroundColor Cyan
mvn clean package -q
if ($LASTEXITCODE -ne 0) { Write-Host "ERRO na compilacao" -ForegroundColor Red; exit 1 }

Write-Host "[2/6] Gerando update4j-config.xml..." -ForegroundColor Cyan
$JAR_URL = "https://github.com/$REPO/releases/download/$TAG/timeflow-app.jar"
$TS = (Get-Date -Format "yyyy-MM-ddTHH:mm:ss+00:00")
$xml = "<?xml version=""1.0"" encoding=""UTF-8""?>`n<configuration timestamp=""$TS"">`n    <files>`n        <file>`n            <uri>$JAR_URL</uri>`n            <path>`${user.home}/.timeflow/app/timeflow-app.jar</path>`n            <classpath>true</classpath>`n        </file>`n    </files>`n</configuration>"
[System.IO.File]::WriteAllText("$PWD\update4j-config.xml", $xml, [System.Text.UTF8Encoding]::new($false))

Write-Host "[3/6] Commit e push..." -ForegroundColor Cyan
git add -A
git commit -m "release: $TAG - $Descricao"
git push origin master

Write-Host "[4/6] Criando Release no GitHub..." -ForegroundColor Cyan
Copy-Item "target\timeflow-app-2.0.0-shaded.jar" "target\timeflow-app.jar" -Force
gh release create $TAG "target\timeflow-app.jar#timeflow-app.jar" --repo $REPO --title "Time Flow $TAG" --notes $Descricao

Write-Host "[5/6] Atualizando Timeflow_Dist..." -ForegroundColor Cyan
New-Item -ItemType Directory -Force -Path "..\Timeflow_Dist" | Out-Null
Copy-Item "target\timeflow-app.jar" "..\Timeflow_Dist\timeflow-app.jar" -Force

Write-Host "`n============================================" -ForegroundColor Green
Write-Host "  RELEASE $TAG CONCLUIDO!" -ForegroundColor Green
Write-Host "  https://github.com/$REPO/releases/tag/$TAG" -ForegroundColor Green
Write-Host "============================================`n" -ForegroundColor Green
