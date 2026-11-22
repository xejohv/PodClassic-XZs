# PodClassic-XZs

An iPod Classic style local music player for **Sony Xperia XZs (SO-03J, docomo)** — a fork of the PodClassic lineage, tuned into a single-purpose retro music device.

面向 **索尼 Xperia XZs（SO-03J 日版）** 的 iPod Classic 风格本地音乐播放器，由 PodClassic 一系改造而来，按「纯音乐播放器」整机场景重新调过。

**当前版本：v1.5.86（versionCode 120）** — 下载见 [Releases](https://github.com/xejohv/PodClassic-XZs/releases/latest)，逐版本变化见 [docs/CHANGELOG.md](./docs/CHANGELOG.md)。

---

## 来源与致谢 / Credits & Lineage

> 本项目是**分支（fork）**，不是原创项目，也**不是**上游的官方发布。

| 层级 | 项目 | 说明 |
| --- | --- | --- |
| 原始 | **PodClassic** | 作者 `0x1317bf7`，MIT License，最后版本 1.5（2022-06） |
| 延续 | **PodClassicMod** | `Dora-z/PodClassicMod`，承接上游继续修改 |
| 本分支 | **PodClassic-XZs** | `xejohv/PodClassic-XZs`，针对 Xperia XZs 整机改造 |

- 原始版权归 **0x1317bf7** 所有，许可证为 MIT，见 [LICENSE](./LICENSE)。
- 上游 PodClassic 的原始仓库与账号已删除（IzzyOnDroid 标记 `NoSourceSince`），本项目是该代码线的延续分支。
- 所有在本分支新增的改动版权归本分支维护者所有，同样以 MIT 授权。

---

## 主要功能 / What it does

### 外观
- 金色机身配色（对齐 Xperia XZs 金色款），黑色转盘 + 白色三角，满幅贴边
- 顶部单行标题栏：时间 / 播放状态 / 电量；标题过长时播放状态下沉隐藏
- 无封面时显示按主题着色的音符占位（不再随机抽图）
- 任何界面（含相机、视频、蓝牙、游戏）不显示状态栏与导航栏
- 四套机身配色（暖银 / 暗黑 / 冰蓝 / 金桔），覆盖标题栏、列表选中条、碟片、进度条与音量条

### 播放
- **音量就画在进度条本身**：播放页转动转盘或按机身音量键，同一根条子改画音量格数（两端时间位换成喇叭图标），条身位置与长度一格不动，因此切换不跳动；继续转/按即调音量，**停手 2 秒自动回到进度显示**；页面上不存在第二条音量条
- **物理音量键联动**：按机身音量加减键时，这根条子立即进入音量显示并同步格数，2 秒后自动回落
- **收藏只在长按**：长按中央键 = 收藏 / 取消收藏（播放页收藏当前曲目），收藏条目名前顶 ★
- **播放模式只从图标点**：播放页右上角图标切换随机 / 单曲 / 顺序与全部循环，不再有「长按切模式」的手势
- 封面、歌名、艺人、专辑、进度时间完整显示；开关打开时按 LRC 显示歌词

### 媒体与资料
- **录音（语音备忘录）**：附加程序 → 录音；开始/停止、录制实时走时、倒序列表、直接播放、长按二次确认删除；三档录音质量（高 192k / 标准 128k / 低 64k）；文件存内置 `Recordings/`，插线可拷出
- **CoverFlow**：开关打开后「音乐」菜单底部出现 CoverFlow 入口，封面流可左右转动浏览、点中间封面进入专辑
- 视频：进度条可拖动快进（拖动时暂停并浮出目标时间），半屏靠 MENU 退出、全屏靠右下角按钮收回
- 相机、TXT 阅读、文件管理、照片墙、蓝牙页（本机对外名称为 **iPod Classic**）

### 设置
设置首页只有 5 个分类，点进去才是各项开关：

| 分组 | 内容 |
| --- | --- |
| 播放与显示 | 自动播放、播放顺序、随机、重复、睡眠定时、显示歌词、显示歌手和专辑、显示时间、CoverFlow |
| 音效 | 均衡器、系统AGC、TomSteady、胆机音效、DC相位线性器，首行为「音效说明」长文 |
| 录音 | 录音质量 |
| 外观与操作 | 夜间模式、机身配色、按键反馈 |
| 系统 | 关于本机、语言、辅助功能、重置 |

- **关于本机**为只读信息页，只留「标签 + 数值」：音乐 / 视频 / 照片 / 容量 / 可用容量 / 版本 / S/N；容量明细（内部存储、SD 卡）收进「容量」二级页。版本行连点 6 次进入系统设置。
- **容量口径**：内部存储 + SD 卡合计，精确到一位小数。
- **语言**：自动 / 简体中文 / 繁体中文 / English，切换即时生效（原地重建界面）。

### 附加程序
- 相机入口、内嵌 PodClassic 风格蓝牙页（不跳系统设置）
- **6 个游戏**：俄罗斯方块（落点描影）、贪吃蛇（五键手柄）、飞机大战（2 颗清屏炸弹）、打砖块、数独、扫雷（三档难度 + 方形触控板 + 磁吸光标）
- 游戏全屏：下滑折叠转盘区，画面铺满；未全屏自动暂停；**退出只保留侧面滑出**

### 手势与系统增强（依赖无障碍服务）
- **三指上滑**：任意界面回主菜单
- **长按音量下键 1.2 秒**：回桌面（从相机等外部应用逃生）
- 插线自动选「传输文件」、MTP 授权弹窗自动点「允许」、PodClassic 风格电源菜单、锁屏滑动解锁

### 稳定性修复（v1.5.72 起累积）
- 修复「菜单空白 / 停止运行」：后台 HandlerThread 写自定义 LiveData 导致的 `WrongThreadException`
- 修复相机启动崩溃、播放时持续震动、列表滑动过快
- 修复切语言后设置无效（偏好 `commit()` 落盘 + 原地 `recreate()`）
- 修复删除正在播放的录音后返回主页闪退、打开显示歌词后播放页闪退
- 锁定仅竖屏

---

## 机身配色 / Body colors

| 主题 | 官方名称 | 碟片 |
| --- | --- | --- |
| 暖银 | Warm Silver | 红色 |
| 暗黑 | Black | 红色 |
| 冰蓝 | Ice Blue | 红色 |
| 金桔 | Citrus（日本限定） | 深橄榄 |

入口：**附加程序 → 主题**（设置里的「机身配色」是同一页面的第二入口），选中后立即整页重绘。暗黑主题另外单独调高了明度差，避免黑底界面糊成一片。

## 适用机型 / Device

| 项 | 值 |
| --- | --- |
| 机型 | Sony SO-03J（Xperia XZs，docomo 日版） |
| 系统 | Android 8.0.0（API 26），arm64-v8a |
| 分辨率 | 1080×1920，density 480（densityDpi override 540） |
| minSdk / targetSdk | 26 / 34 |
| applicationId | `io.github.xejohv.podclassicxzs` |
| 当前版本 | 1.5.86（versionCode 120） |

其它机型未验证；UI 的 overscan、触摸区、字号均按上述分辨率校准。

---

## 部署 / Deploy

**完整部署手册（含恢复出厂后 25 分钟重建清单、验收表、排障）：[docs/DEPLOY.md](./docs/DEPLOY.md)**
**交给 AI 一键部署的提示词：[docs/AI-DEPLOY.md](./docs/AI-DEPLOY.md)**
**版本变化（自 v1.5.21 起，含 v1.5.72–v1.5.86 逐版条目）：[docs/CHANGELOG.md](./docs/CHANGELOG.md)**

三步概要（细节看手册）：

```powershell
$env:JAVA_HOME        = "<工作目录>\build-env\jdk-17.0.21.1+2"
$env:ANDROID_HOME     = "<工作目录>\build-env\sdk"
$env:GRADLE_USER_HOME = "<工作目录>\build-env\gradle-home"

# 1) 构建（JDK 17 + Android SDK 34）
cmd /c ".\gradlew.bat :app:assembleRelease --no-daemon"
# 2) 装机（首次需在手机上允许 USB 调试）
adb install -r app\build\outputs\apk\release\app-release.apk
# 3) 绑定无障碍（每次重装都要重做；否则手势/电源菜单/插线应答全失效）
adb shell settings put secure accessibility_enabled 0
adb shell settings put secure enabled_accessibility_services io.github.xejohv.podclassicxzs/io.github.xejohv.podclassicxzs.gestures.GestureService
adb shell settings put secure accessibility_enabled 1
```

- 签名读 `local.properties` + 仓库根 `podclassic.keystore`（两者都不入库）。缺失时自动退回 debug 签名：能装，但与旧正式签名版本不兼容，覆盖安装会要求卸载重装（会清存档）。
- **请备份 keystore 与 local.properties**，这是长期原地升级的唯一凭证。

---

## 插线传音乐 / USB MTP

| 场景 | 是否需要人工授权 |
| --- | --- |
| 已绑定无障碍后，插到任意 Windows 10/11 电脑传歌 | **不需要**：「选择 USB 使用方式」面板会自动选「传输文件」；部分 ROM 的「允许访问设备数据」弹窗也会自动点「允许」（1.5.71 起） |
| 换一台新电脑用 adb 装包/调试 | **需要**：新电脑首次要在手机上点一次「允许 USB 调试」（系统级安全机制，不代答） |
| macOS | 装 OpenMTP / Android File Transfer，手机侧同样自动传输文件 |
| Linux | libmtp / gvfs-mtp |

前提与注意：

- 无障碍服务必须处于开启状态，否则插线会停在「为此设备充电」，电脑看不到存储。
- Android 8 的 MTP 需要**先解锁屏幕**再插线，否则电脑里看不到文件。
- 自动应答只在文案同时匹配时才动手（「为此设备充电」+「传输文件」；或「允许访问设备数据」+「拒绝」+「允许」），不会误点其它权限框。
- 录音、音乐都在内部共享存储的可见目录（`Music/`、`Recordings/`），插线可直接拷出。

---

## 尚未包含 / Roadmap

- **屏幕亮度调节**：已设计未上线，排在下一版。
- **图书阅读（LibreraReader 集成）**：作为**独立分支项目**推进（不改动本仓库、不并入本 APK），完成后单独发布。

---

## 说明 / Notes

- 本包按单机使用场景调整（隐藏系统 UI、单一桌面、USB 默认 MTP），不作为通用播放器发布。
- 与上游同签名体系无关：本分支重新签名，**不能覆盖安装上游包**，需先卸载旧包。
- 演示视频与截图见 Release 附件。
