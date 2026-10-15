# v1.0.0

- 启动图标包含 TSD，扫码界面显示“测试工程部开发”“扫码连接WIFI”。
- 打开即扫码，支持标准 Wi-Fi 二维码、WPA/WPA2 Personal、WPA3 SAE、开放网络、隐藏 SSID、中文与转义字段。
- Android 11 及以上；compileSdk / targetSdk 为 Android 17（API 37）。
- userdebug / eng 测试系统预装且授予 NETWORK_SETTINGS 后，直接保存网络并发起连接；部署脚本与权限白名单在 `deploy/`。
- 一次性通过 ADB 授予 CAMERA 后，无需每次确认相机权限；普通安装使用系统 Wi-Fi 确认窗口。
- 密码仅在内存处理，不上传、不保存扫码历史。

验证：构建成功、12 项单元测试通过、APK 签名验证通过。

交付为调试签名测试版。尚未在 Android 17 真机验证相机扫码、预装授权及实际 Wi-Fi 连接；请求成功不等于已连接或已联网。

APK SHA-256：`97D11696CEFBD48EF51EC5E818331C164D95F2A19CEE066D1CDC702D38480F69`
