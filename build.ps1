param([string]$Sdk = "$env:LOCALAPPDATA\Android\Sdk")
$ErrorActionPreference = 'Stop'
# Build from an ASCII path: Java 17 test workers can misread Chinese Windows paths.
$stage = Join-Path $env:TEMP ('tsd-scanwifi-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path "$stage\app" -Force | Out-Null
Copy-Item -LiteralPath "$PSScriptRoot\app\src" -Destination "$stage\app\src" -Recurse
Copy-Item -LiteralPath "$PSScriptRoot\app\build.gradle" -Destination "$stage\app"
foreach ($name in @('settings.gradle','build.gradle','gradle.properties','gradlew','gradlew.bat','gradle')) {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination $stage -Recurse
}
Set-Content -LiteralPath "$stage\local.properties" -Value ('sdk.dir=' + $Sdk.Replace('\','/')) -Encoding ascii
$gradle = Join-Path $PSScriptRoot '.tools\gradle-9.3.1\bin\gradle.bat'
if (!(Test-Path -LiteralPath $gradle)) { $gradle = Join-Path $stage 'gradlew.bat' }
& $gradle -p $stage assembleDebug testDebugUnitTest --console=plain
if ($LASTEXITCODE -ne 0) { throw "构建或测试失败，检查目录：$stage" }
$out = Join-Path $PSScriptRoot 'outputs'
New-Item -ItemType Directory -Path $out -Force | Out-Null
$apkVersion=(Get-Content -LiteralPath "$stage\app\build\outputs\apk\debug\output-metadata.json" -Raw | ConvertFrom-Json).elements[0].versionName
if ($apkVersion -notmatch '^\d+\.\d+\.\d+$') { throw '无法确定 APK 版本' }
$apkPath=Join-Path $out ("TSD-ScanWifi-" + $apkVersion + '.apk')
Copy-Item -LiteralPath "$stage\app\build\outputs\apk\debug\app-debug.apk" -Destination $apkPath -Force
New-Item -ItemType Directory -Path "$out\tests" -Force | Out-Null
Copy-Item -Path "$stage\app\build\reports\tests\testDebugUnitTest\*" -Destination "$out\tests" -Recurse -Force
Get-FileHash -LiteralPath $apkPath -Algorithm SHA256
Write-Host "构建与测试目录：$stage"
