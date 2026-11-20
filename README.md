# 声屿 MUSIC（Android）

一款深色风格的 Android 本地音乐播放器原型。

## 当前功能
- 从 Android 系统文件选择器多选导入音频
- 播放、暂停、上一首、下一首、播放进度拖动
- 歌曲搜索、收藏、随机播放、单曲循环
- IndexedDB 本地保存导入的音频与收藏状态，应用重启后尝试恢复
- WebView 封装为独立 Android 应用，应用名为「声屿 MUSIC」

## 当前版本边界
当前版本播放用户自行导入的本地音频，不包含在线正版音乐曲库，也不提供账号/云同步。在线曲库需要另行接入具有合法播放授权的服务。

## 云端构建
仓库已配置 GitHub Actions。每次向 main 分支推送代码，或手动运行 Android APK 工作流，都会自动构建可安装的 debug APK，并上传为 Actions Artifact。

构建完成后：
1. 打开仓库的 Actions 页面。
2. 选择最新的「Build Android APK」运行记录。
3. 在 Artifacts 区域下载 `shengyu-music-debug-apk`。
4. 解压后取得 `app-debug.apk`，发送到 Android 手机安装。

Debug APK 使用 Android Gradle Plugin 自动生成的 debug 签名，适合个人测试与侧载安装，不是 Google Play 发布签名。
