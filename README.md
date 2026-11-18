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

## 构建 / Build

需要 JDK 17 + Android SDK (compileSdk 34)。

```bash
# local.properties 需提供签名口令（该文件不入库）
pod.storePassword=***
pod.keyAlias=podclassic
pod.keyPassword=***

./gradlew assembleRelease
```

`app/build.gradle` 从 `local.properties` 读取签名口令；未提供时产出的 APK 不具备发布签名。

---

## 安装与整机配置 / Install

```bash
adb install -r PodClassicXZs_v1.5.17.apk

# 设为唯一桌面
adb shell cmd package set-home-activity --user 0 io.github.xejohv.podclassicxzs/io.github.xejohv.podclassicxzs.activity.MainActivity

# 全屏沉浸，不显示状态栏/导航栏
adb shell settings put global policy_control immersive.full=*
adb shell wm overscan 0,0,0,-162

# 手势服务
adb shell settings put secure enabled_accessibility_services io.github.xejohv.podclassicxzs/io.github.xejohv.podclassicxzs.gestures.GestureService
adb shell settings put secure accessibility_enabled 1
```

---

## 说明 / Notes

- 本包按单机使用场景调整（隐藏系统 UI、单一桌面、USB 默认 MTP），不作为通用播放器发布。
- 与上游同签名体系无关：本分支重新签名，**不能覆盖安装上游包**，需先卸载旧包。
- 演示视频与截图见 Release 附件。
