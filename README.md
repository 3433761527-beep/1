# 声屿 MUSIC V2.0（Android）

## V2.0 功能
- 在线搜索：使用 Apple iTunes Search API 搜索歌曲、歌手和专辑，可切换中国大陆、日本、美国 storefront。
- 在线试听：播放 Apple 提供的约 30 秒预览片段，并在播放器中提供对应 iTunes Store 链接及来源标注。
- 本地音乐：继续支持手机音频文件多选导入、播放、搜索和收藏。
- 在线收藏：在线曲目收藏保存在本机浏览器存储中。
- 播放控制：播放、暂停、上一首、下一首、进度控制、随机播放；在线试听结束后停止，不自动串播下一首。
- 应用已声明 Android INTERNET 权限。

## 重要限制
- 在线试听不是完整歌曲播放，预览长度通常约 30 秒。
- 该版本不包含完整正版流媒体订阅曲库。Apple Music 完整目录 API 需要开发者令牌，完整播放还涉及 MusicKit 用户授权与适用的会员/版权条件；本项目没有伪造或绕过授权。
- Apple 提供的预览和宣传素材只能按 Apple 的推广用途规则展示；播放器提供 iTunes 来源标注及指向对应商店内容的链接。参见 https://performance-partners.apple.com/search-api
- 歌词、完整排行榜、账号云同步尚未接入授权服务，因此不作为已完成的功能宣传。
- 在线搜索需要网络连接；如果设备/地区无法访问 Apple 服务，在线搜索或预览可能不可用。本地音乐仍可播放。

## 云端 APK
仓库的 GitHub Actions 工作流会在 main 分支有新提交时自动构建 APK。构建成功后：
1. 打开仓库 Actions 页面。
2. 进入最新的「Build Android APK」运行记录。
3. 下载 Artifacts 中的 `shengyu-music-debug-apk`。
4. 解压得到 `app-debug.apk` 并安装到 Android 手机。

Debug APK 用于个人测试和侧载安装，不是 Google Play 发布签名。
