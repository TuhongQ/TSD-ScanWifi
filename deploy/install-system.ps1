param([Parameter(Mandatory=$true)][string]$Serial)
$ErrorActionPreference = 'Stop'
$apk = Join-Path $PSScriptRoot '..\outputs\TSD-ScanWifi-1.2.0.apk'
if (!(Test-Path -LiteralPath $apk)) { throw '请先执行 build.ps1 生成 APK。' }
function Run-Adb {
    & adb -s $Serial @args
    if ($LASTEXITCODE -ne 0) { throw ('ADB 操作失败：' + $args[0]) }
}
$buildType = (& adb -s $Serial shell getprop ro.build.type).Trim()
if ($buildType -notin @('userdebug','eng')) { throw '仅用于授权的 userdebug / eng 测试设备。' }
Run-Adb @('root')
Run-Adb @('wait-for-device')
if ((& adb -s $Serial shell id -u).Trim() -ne '0') { throw '此设备不支持 adb root。' }
Run-Adb @('remount')
& adb -s $Serial shell test -e /system_ext/priv-app/TSDScanWifi
if ($LASTEXITCODE -eq 0) { throw '预装目录已存在；为保留现有版本，请使用相同签名的 adb install -r 更新，或手动备份后再操作。' }
& adb -s $Serial shell test -e /system_ext/etc/permissions/privapp-permissions-tsd-scanwifi.xml
if ($LASTEXITCODE -eq 0) { throw '权限文件已存在，请先人工核对。' }
Run-Adb @('shell','mkdir','-p','/system_ext/priv-app/TSDScanWifi')
Run-Adb @('push',$apk,'/system_ext/priv-app/TSDScanWifi/TSDScanWifi.apk')
Run-Adb @('push',"$PSScriptRoot\privapp-permissions-tsd-scanwifi.xml",'/system_ext/etc/permissions/privapp-permissions-tsd-scanwifi.xml')
Run-Adb @('shell','chmod','0755','/system_ext/priv-app/TSDScanWifi')
Run-Adb @('shell','chmod','0644','/system_ext/priv-app/TSDScanWifi/TSDScanWifi.apk','/system_ext/etc/permissions/privapp-permissions-tsd-scanwifi.xml')
Run-Adb @('shell','restorecon','-RF','/system_ext/priv-app/TSDScanWifi','/system_ext/etc/permissions/privapp-permissions-tsd-scanwifi.xml')
Run-Adb @('reboot')
Write-Host '重启完成并解锁后执行：'
Write-Host "adb -s $Serial shell pm grant cn.scanwifi.app android.permission.CAMERA"
Write-Host "adb -s $Serial shell pm grant cn.scanwifi.app android.permission.ACCESS_COARSE_LOCATION"
Write-Host "adb -s $Serial shell pm grant cn.scanwifi.app android.permission.ACCESS_FINE_LOCATION"
Write-Host "adb -s $Serial shell cmd location set-location-enabled true --user 0"
Write-Host "adb -s $Serial shell dumpsys package cn.scanwifi.app"
Write-Host "adb -s $Serial shell am start -n cn.scanwifi.app/.MainActivity"
