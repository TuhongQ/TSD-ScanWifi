# TSD 扫码连接WIFI

[下载 APK / Releases](https://github.com/TuhongQ/TSD-ScanWifi/releases) · [源码仓库](https://github.com/TuhongQ/TSD-ScanWifi)

启动图标包含 TSD 三个字母，扫码界面显示“测试工程部开发”和“扫码连接WIFI”。

中文 Android 应用：打开即进入相机，识别标准 `WIFI:` 二维码后自动保存网络并发起连接。系统预装且具备 NETWORK_SETTINGS 权限时，不弹出 Wi-Fi 确认窗口；普通安装时使用系统确认窗口。

## 系统与功能

- 最低 Android 11（API 30），compileSdk / targetSdk 为 Android 17（API 37）。
- WPA/WPA2 Personal、WPA3 SAE、开放网络；支持隐藏 SSID、中文名称及二维码转义字符。
- 相机权限申请、拒绝提示、应用权限设置入口、手电筒、重新扫码、Wi-Fi 设置入口。
- 成功连接扫码目标 SSID，且该网络已取得可用 IPv4 / IPv6 地址后，显示绿色背景与 Wi-Fi 名称，3 秒后关闭应用界面。
- 请求连接后最多等待 30 秒；未确认连接、系统取消、格式错误或保存失败显示红色背景，5 秒后自动重新扫码。
- 四个按钮改为两行小按钮，文字 13sp，触控高度保留 48dp。
- WEP、企业认证、DPP 及 WPA 原始 64 位十六进制密钥会明确拒绝，不尝试错误连接。
- 密码只在内存中处理，不记录日志、不上传、不保存扫码历史；禁用备份与屏幕截图。
- 不扫描附近网络，不需要后台位置或附近设备权限。为读取实际连接的 SSID，需要前台精确位置权限和开启系统定位；仅使用 Wi-Fi 状态，不采集地理坐标。系统预装模式使用 WifiManager 的系统应用兼容接口，普通模式调用 `ACTION_WIFI_ADD_NETWORKS`。

## Android 系统连接机制

普通应用必须经过系统授权窗口。系统应用具备相应权限后可直接保存并发起连接。通过系统网络回调核实 SSID、关联完成状态及同一网络的 IP 地址后才显示绿色，不把保存成功、连接到其他 Wi-Fi 或蜂窝联网误报为成功。不要求公网验证，支持只在局域网内使用的工厂 Wi-Fi。

文档：https://developer.android.com/develop/connectivity/wifi/wifi-save-network-passpoint-config

## 构建

JDK 17、Gradle 9.3.1、Android Gradle Plugin 9.1.1、Android SDK 37.0、Build Tools 36.0.0。
在 `local.properties` 设置自己的 `sdk.dir`，然后运行：

```powershell
.\build.ps1
```

扫码组件：ZXing Android Embedded 4.3.0（Apache-2.0）。

## userdebug / eng 低弹窗预装

优先把 APK 和白名单加入测试 ROM 的同一系统分区：

```text
/system_ext/priv-app/TSDScanWifi/TSDScanWifi.apk
/system_ext/etc/permissions/privapp-permissions-tsd-scanwifi.xml
```

APK 使用 `outputs/TSD-ScanWifi-1.1.0.apk`。白名单来自 `deploy/`。不要放在 vendor 分区，NETWORK_SETTINGS 不是 vendorPrivileged 权限。

可写的 adb root 测试机也可运行以下部署脚本；必须指定设备序列号，脚本会 remount、预装并重启设备，遇到已有文件会停止，避免覆盖原有预装。

```powershell
.\deploy\install-system.ps1 -Serial 你的设备序列号
# 重启完成、解锁后，一次性预授予相机权限：
adb -s 你的设备序列号 shell pm grant cn.scanwifi.app android.permission.CAMERA
adb -s 你的设备序列号 shell pm grant cn.scanwifi.app android.permission.ACCESS_COARSE_LOCATION
adb -s 你的设备序列号 shell pm grant cn.scanwifi.app android.permission.ACCESS_FINE_LOCATION
adb -s 你的设备序列号 shell cmd location set-location-enabled true --user 0
adb -s 你的设备序列号 shell dumpsys package cn.scanwifi.app
adb -s 你的设备序列号 shell am start -n cn.scanwifi.app/.MainActivity
```

在 dumpsys 输出中核对 `android.permission.NETWORK_SETTINGS: granted=true`，应用扫码页应显示“系统预装模式”。此后相机授权及 Wi-Fi 确认都无需每次弹出；相机隐私指示和系统隐私开关仍遵守 Android 行为。若 ROM 不允许 remount，脚本停止；不自动关闭 AVB/verity 或更改 SELinux。应由 ROM 构建端纳入预装和权限白名单。

如果先前普通安装过同包名 APK，预装版本必须使用相同签名。当前为调试签名，仅用于测试系统；正式集成请使用团队自己的稳定签名。

官方说明：
- https://developer.android.com/reference/android/net/wifi/WifiManager
- https://source.android.com/docs/core/permissions/perms-allowlist

撤回预装时仅移除上面两个专用文件/目录并重启；不要删除其它系统权限文件。部署脚本尚未在真实测试机执行。

## 真机验收

1. Android 17 手机上安装 APK，首次打开允许相机，确认镜头预览与手电筒可用。
2. 扫描自己拥有或获准连接的 WPA2、WPA3、开放与隐藏网络二维码，在系统窗口确认。
3. 在系统设置核对连接到的 SSID，并实际打开网页检查联网。
4. 测试拒绝相机权限、取消系统连接、普通网址二维码、错误密码、离开应用再返回及屏幕旋转。
5. 确认无相机权限时不会崩溃，不支持的二维码不会发起连接。
6. 连接后核对绿色背景、真实 Wi-Fi 名称与 3 秒后关闭界面；错误密码等待 30 秒超时后核对红色背景和 5 秒后恢复扫码。
7. 连到其他 SSID、仅保存未连接、无 IP、连接中途断开时不应保持绿色；测试旋转和切换后台后恢复。

v1.1.0 的二维码和连接流程共 21 项单元测试通过。打包时未连接手机，未完成新版界面、真实 Wi-Fi 连接及 Android 17 真机验收。输出 APK 为调试签名的测试版，可覆盖安装旧版（包名与签名保持一致）。
