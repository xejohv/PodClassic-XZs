# PodClassic-XZs

An iPod Classic style local music player for **Sony Xperia XZs (SO-03J, docomo)** — a fork of the PodClassic lineage, tuned into a single-purpose retro music device.

面向 **索尼 Xperia XZs（SO-03J 日版）** 的 iPod Classic 风格本地音乐播放器，由 PodClassic 一系改造而来，按「纯音乐播放器」整机场景重新调过。

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

## 和上游版本的差异 / What changed

### 外观
- 金色机身配色（对齐 Xperia XZs 金色款），黑色转盘 + 白色三角，满幅贴边
- 顶部单行标题栏：时间 / 播放状态 / 电量；标题过长时播放状态下沉隐藏（B 方案）
- 无封面时显示金色实心音符占位（不再随机抽图）
- 任何界面（含相机、视频、蓝牙、游戏）不显示状态栏与导航栏

### 播放
- 播放模式两处入口：右上角图标点击弹气泡 + 长按 MENU 弹「播放模式」菜单
- 常驻音量条（不再只在拖动进度时闪现）
- 封面、歌名、艺人、专辑、进度时间完整显示

### 手势
- **三指上滑**：任意界面回主菜单
- **长按音量下键 1.2 秒**：回桌面（从相机等外部应用逃生）
- 底部上滑 HOME / 左右边缘内滑 BACK / 上滑停顿 RECENTS

### 附加程序
- 相机入口、内嵌 PodClassic 风格蓝牙页（不跳系统设置）
- 6 个游戏：俄罗斯方块（落点描影）、贪吃蛇（五键手柄）、飞机大战（2 颗清屏炸弹）、打砖块、数独、扫雷
- 游戏全屏：下滑折叠转盘区，画面铺满；未全屏自动暂停

### 稳定性
- 修复「菜单空白 / 停止运行」：后台 HandlerThread 写自定义 LiveData 导致的 `WrongThreadException`
- 修复相机启动崩溃
- 修复播放时持续震动、菜单列表滑动过快
- 锁定仅竖屏

---

## 机身配色 / Body colors

界面配色按 Xperia XZs 官方发布配色与官方英文名做成四套主题，按自己手上的机身颜色选即可：

| 主题 | 官方名称 | 碟片 |
| --- | --- | --- |
| 暖银 | Warm Silver | 红色 |
| 暗黑 | Black | 红色 |
| 冰蓝 | Ice Blue | 红色 |
| 金桔 | Citrus（日本限定） | 深橄榄 |

入口：**附加程序 → 主题**（设置里的「机身配色」是同一页面的第二入口），选中后立即整页重绘。

主题覆盖标题栏、列表与选中条、碟片与圆心、无封面占位图案、播放页内容区、进度条与音量条、游戏底色；暗黑主题另外单独调高了明度差，避免黑底界面糊成一片。

## 适用机型 / Device

| 项 | 值 |
| --- | --- |
| 机型 | Sony SO-03J（Xperia XZs，docomo 日版） |
| 系统 | Android 8.0.0（API 26），arm64-v8a |
| 分辨率 | 1080×1920，density 480（densityDpi override 540） |
| minSdk / targetSdk | 26 / 34 |
| applicationId | `io.github.xejohv.podclassicxzs` |

其它机型未验证；UI 的 overscan、触摸区、字号均按上述分辨率校准。

---

## 部署 / Deploy

**完整部署手册（含恢复出厂后 25 分钟重建清单、验收表、排障）：[docs/DEPLOY.md](./docs/DEPLOY.md)**
**交给 AI 一键部署的提示词：[docs/AI-DEPLOY.md](./docs/AI-DEPLOY.md)**
**版本变化（自 v1.5.21 起）：[docs/CHANGELOG.md](./docs/CHANGELOG.md)**

三步概要（细节看手册）：

```powershell
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

---
## 说明 / Notes

- 本包按单机使用场景调整（隐藏系统 UI、单一桌面、USB 默认 MTP），不作为通用播放器发布。
- 与上游同签名体系无关：本分支重新签名，**不能覆盖安装上游包**，需先卸载旧包。
- 演示视频与截图见 Release 附件。
