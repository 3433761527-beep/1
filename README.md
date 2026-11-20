# 声屿 MUSIC V4.0（Android）

## 核心能力
- Audius 开放音乐网络：在线搜索、每周热门、风格入口、完整公开曲目在线播放。
- 后台播放：Android Media3 ExoPlayer + MediaSessionService；支持应用退到后台后继续播放。
- 锁屏/通知栏控制：系统媒体通知、播放/暂停/上一首/下一首，以及媒体元数据。
- 蓝牙与耳机媒体按键：通过 Android MediaSession 接收系统媒体控制。
- 音频焦点：由 ExoPlayer/Android 音频框架处理焦点切换。
- 播放队列：连续播放、上一首/下一首、随机播放、单曲循环。
- 同步歌词：通过 LRCLIB 获取逐行时间轴歌词；找不到同步歌词时回退普通歌词。
- 个人歌单：本地创建歌单、添加在线/本地曲目、播放歌单。
- 最近播放：本机保存播放历史。
- 睡眠定时：15/30/45/60 分钟，或当前歌曲结束后停止；后台计时由播放服务执行。
- 播放速度：0.75×、1×、1.25×、1.5×、2×。
- 音效均衡器：均衡、低音、人声、高音、流行、摇滚预设（依赖设备 Equalizer 支持；主要作用于 Android 原生在线播放）。
- 离线下载：仅当 Audius 将曲目标记为可下载时显示下载按钮；通过 Android DownloadManager 保存到 Music/Shengyu。
- 本地音乐：支持从手机多选导入并播放常见音频格式；保留本地收藏。
- 数据存储：歌单、历史、在线收藏和歌词缓存保存在本机；本地导入音频存储在 IndexedDB。

## 曲库、版权与音质
- 在线完整曲目来自 Audius 创作者开放音乐网络。仅显示公开、可流式播放且未标记为 gated/unlisted 的曲目。
- 这不是全球所有商业唱片公司的授权曲库，不能保证包含任意主流歌手或唱片公司的完整专辑。
- Audius 的流媒体质量取决于平台转码和源文件；Audius 官方说明流媒体最高可达 320 kbps / 48 kHz，但不是每首歌都保证达到此规格。Lossless 下载仅对权利人开放下载的内容可用。
- 应用不抓取、解密、破解或绕过 Spotify、网易云音乐、QQ 音乐等商业平台的会员或版权保护。
- 歌词由 LRCLIB 提供，可能出现无匹配、歌词缺失或同步偏差；遵守服务的请求频率限制。
- 离线下载按钮只针对 API 明确标记可下载的内容，下载后的使用仍应遵守创作者许可和当地法律。

## 官方技术资料
- Audius API: https://docs.audius.co/api/
- Audius 支持音频格式与音质说明: https://help.audius.co/product/supported-files
- Android Media3: https://developer.android.com/media/media3
- LRCLIB API: https://lrclib.net/docs

## 构建
GitHub Actions 在 main 分支提交后自动构建 APK。打开仓库 Actions 最新成功运行记录，下载 Artifacts 中的 `shengyu-music-debug-apk`，解压得到 `app-debug.apk`。

Debug APK 用于个人安装测试，不是 Google Play 发布签名。首次安装 V4.0 时，如果旧版本调试签名不一致，需先备份本地歌曲文件，再卸载旧版本。
