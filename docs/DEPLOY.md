# PodClassic-XZs 完整部署手册

> 目标：把一台 Sony Xperia XZs（SO-03J）在 **30 分钟内**从「刚恢复出厂 / 别人的旧机」做成一台 iPod Classic 造型的纯音乐机。
> 本文同时是「AI 一键部署」的依据：按章节顺序执行即可，能复制粘贴的命令都给了。
> 给 AI 用的提示词见 [AI-DEPLOY.md](./AI-DEPLOY.md)。

- 应用包名：`io.github.xejohv.podclassicxzs`
- 主 Activity：`io.github.xejohv.podclassicxzs/.activity.MainActivity`
- 无障碍组件：`io.github.xejohv.podclassicxzs/io.github.xejohv.podclassicxzs.gestures.GestureService`
- 目标机型：SO-03J（docomo 版 Xperia XZs），Android 8.0，1080×1920，540dpi
- 不需要 root，不需要解锁 BL

---

## 0. 你将得到什么

- 开机直接进播放器主界面，没有状态栏/导航栏/桌面图标堆叠
- 转盘式操作、常驻音量条、封面与歌词、视频、相机、TXT 阅读、文件管理
- 7 个内置游戏（俄罗斯方块、贪吃蛇、飞机大战、打砖块、数独、扫雷），带存档与三档难度
- 系统级增强（由无障碍服务实现）：
  - 三指上滑回主菜单、长按音量下 1.2s 回桌面
  - 电源菜单换成 PodClassic 风格（锁屏 / 关机 / 重新启动）
  - 插数据线时自动选「传输文件」，不再停在「为此设备充电」
  - 「允许访问设备数据」MTP 授权弹窗自动点「允许」
  - 锁屏滑动解锁、自动熄屏

---

## 1. 环境准备（只做一次）

### 1.1 必须具备

| 项目 | 要求 | 说明 |
| --- | --- | --- |
| 操作系统 | Windows 10/11 x64 | 本文命令为 PowerShell 7（`pwsh`）语法 |
| JDK | **17**（如 Temurin 17 / Zulu 17） | 工程用 Gradle 9 + AGP，JDK 11 会失败 |
| Android SDK | `platforms;android-34` + `build-tools;34.x` + `platform-tools` | 用 cmdline-tools 装即可，不必装 Android Studio |
| adb | 随 platform-tools | 版本新旧都能用 |
| 数据线 | 能传输数据的 Type-C 线 | 纯充电线会识别不到设备 |

### 1.2 目录约定（示例，可自行调整）

```text
工作目录/
├─ podclassic-git/        # 本仓库
├─ podclassic/            # 打好的 APK 存放处
└─ build-env/
   ├─ jdk-17.0.21.1+2/    # JDK 17
   ├─ sdk/                # Android SDK（含 platform-tools/adb.exe）
   └─ gradle-home/        # GRADLE_USER_HOME，缓存依赖
```

### 1.3 环境变量

```powershell
$env:JAVA_HOME       = "<工作目录>\build-env\jdk-17.0.21.1+2"
$env:ANDROID_HOME    = "<工作目录>\build-env\sdk"
$env:GRADLE_USER_HOME= "<工作目录>\build-env\gradle-home"
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:Path"
java -version      # 必须显示 17.x
adb version        # 能输出版本号即 OK
```

> 换新机器加速：把旧的 `build-env/gradle-home` 整目录拷过来，可省掉首次 10+ 分钟的依赖下载。

### 1.4 签名（重要，关系到后续升级）

工程从 `local.properties` 读签名口令、从仓库根目录的 `podclassic.keystore` 读密钥：

```properties
# podclassic-git/local.properties
sdk.dir=C:/.../build-env/sdk
pod.storePassword=podclassic
pod.keyAlias=podclassic
pod.keyPassword=podclassic
```

- 上的两项（keystore 与 local.properties）**都被 .gitignore 排除**，不进仓库。
- 仓库内置了签名兜底：**没有 keystore / 没配密码时自动退回 debug 签名**，照样能构建、能安装。
- 代价：debug 签名与「已装的正式签名版本」不同 → 覆盖安装会报 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`，只能卸载重装（**游戏存档与播放列表会清空**）。

**结论：请务必把 `podclassic.keystore` 和 `local.properties` 一起备份。** 这是"同一台机器一辈子能升级"的唯一凭证。

---

## 2. 构建 APK

```powershell
cd <工作目录>\podclassic-git
$env:JAVA_HOME        = "<工作目录>\build-env\jdk-17.0.21.1+2"
$env:ANDROID_HOME     = "<工作目录>\build-env\sdk"
$env:GRADLE_USER_HOME = "<工作目录>\build-env\gradle-home"
cmd /c ".\gradlew.bat :app:assembleRelease --no-daemon > build.log 2>&1"
Select-String -LiteralPath build.log -Pattern "BUILD SUCCESSFUL|BUILD FAILED|^e:" | ForEach-Object { $_.Line }
```

- **判定标准只看日志里的 `BUILD SUCCESSFUL`**，不要信退出码。
- 首次全量约 10~20 分钟（下依赖）；之后增量约 **4~6 分钟**（R8 压缩是大头）。
- 产物：`app/build/outputs/apk/release/app-release.apk`
- 复制成带版本号的名字，便于回滚：

```powershell
Copy-Item app\build\outputs\apk\release\app-release.apk ..\podclassic\PodClassicXZs_v1.5.71.apk -Force
```

### 2.1 构建常见坑

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 日志停在 `:app:preReleaseBuild` 或 `compileReleaseArtProfile` 后再无输出，`Get-Process java` 为空 | 后台/长命令被系统或终端中断，Gradle 进程已死 | 直接重跑同一条命令，不要以为"还在编译" |
| `Could not determine java version` / `Unsupported class file major version` | JDK 不是 17 | 换 JDK 17，重设 `JAVA_HOME` |
| `SDK location not found` | 少了 `local.properties` 或 `sdk.dir` 写错 | 按 1.4 补文件 |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | 签名与已装版本不一致 | 用原 keystore；否则 `adb uninstall` 后重装 |
| 编译报 Kotlin 语法错 | 手改了源码 | 看 `build.log` 里的 `e:` 行定位 |

---

## 3. 装机

### 3.1 手机侧开关

1. 设置 → 关于手机 → 连点「版本号」7 次，开开发者选项
2. 开发者选项 → 打开「USB 调试」
3. 插线，弹「允许 USB 调试吗？」→ 勾选「一律允许」→ 允许
   - **这是按电脑指纹授权的**：换一台电脑第一次都要再允许一次（不会自动应答，属于系统安全机制）

```powershell
adb devices      # 必须看到 <序列号>  device（不是 unauthorized）
adb install -r ..\podclassic\PodClassicXZs_v1.5.71.apk
```

### 3.2 绑定无障碍服务（核心步骤，必须做）

没有这一步：三指上滑、长按音量下回桌面、电源菜单、插线自动应答、锁屏解锁全部失效。

```powershell
$adb = "$env:ANDROID_HOME\platform-tools\adb.exe"
$comp = "io.github.xejohv.podclassicxzs/io.github.xejohv.podclassicxzs.gestures.GestureService"
& $adb shell settings put secure accessibility_enabled 0
& $adb shell settings put secure enabled_accessibility_services $comp
Start-Sleep -Seconds 2
& $adb shell settings put secure accessibility_enabled 1
Start-Sleep -Seconds 12      # 让系统把服务真正拉起来
& $adb shell am start -n io.github.xejohv.podclassicxzs/.activity.MainActivity
```

也可以手动：设置 → 无障碍 → 已下载的服务 → PodClassic → 打开。

> 装新版 APK 后无障碍常常被系统解绑，**每次 `adb install -r` 之后都要重跑这一段**。

### 3.3 首次进入后的设备侧设置

| 项 | 路径 | 目的 |
| --- | --- | --- |
| 默认桌面/自启 | 打开一次 App 即可；桌面保留 | 开机后手动进 App，或把 App 设为默认主页 |
| 电池优化白名单 | 设置 → 电池 → 忽略优化：PodClassic | 后台播放不被杀 |
| 锁屏 | 设置 → 安全 → 屏幕锁定：滑动 | 配合 App 内的自定义解锁滑块 |
| 设备管理员 | App 内设置页激活 | 支持锁屏/自动熄屏 |
| 音乐目录 | 把音频放到 `Music/`（或 `DCIM/PodClassic` 用于相机） | 扫描入库 |
| 显示 | 关闭自动亮度、固定亮度、关闭自动旋转 | 复古机常亮手感 |

---

## 4. 插线传音乐（MTP）——换电脑需要做什么

### 4.1 结论先说

| 场景 | 是否需要额外授权 |
| --- | --- |
| 已装好 App + 无障碍生效，**插到任何 Windows 10/11 电脑传歌** | **不需要**：系统「选择 USB 使用方式」面板会被自动选成「传输文件」 |
| 同上，但有 ROM 会再弹「允许访问设备数据」 | 已被自动点「允许」（v1.5.71 起） |
| **用 adb 装包 / 抓图 / 调试**，换一台新电脑 | **需要**：新电脑首次要在手机上点一次「允许 USB 调试」 |
| macOS | 系统不认 MTP，需装 **OpenMTP** 或 **Android File Transfer**；手机侧同样自动选传输文件 |
| Linux | 装 `libmtp` / `gvfs-mtp`，文件管理器可直接浏览 |

### 4.2 自动应答的工作原理与边界

- 实现：`GestureService`（无障碍）在窗口变化时扫描系统设置面板，**只有在同时看到「为此设备充电」和「传输文件」时才点「传输文件」**；对 MTP 授权弹窗则要求标题含「允许访问设备数据」且「拒绝」「允许」两个按钮都在，才点「允许」，避免误点其它权限框。
- 依赖：**无障碍服务必须处于开启状态**。恢复出厂或重装 APK 后要先做 3.2 的绑定，否则插线会停在「为此设备充电」，电脑看不到设备。
- 不会代答的：USB 调试 RSA 授权（安全机制，必须人工点一次）；锁屏密码/指纹。

### 4.3 传歌流程

1. 插线 → 手机自动切到「传输文件」
2. Windows 资源管理器出现 `Xperia XZs` / `SO-03J` 设备
3. 打开 `内部共享存储` → `Music/` 拖入 mp3 / flac / m4a
4. 手机上退出播放器再进（或设置页里手动重新扫描），列表即刷新

### 4.4 传歌排障

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 电脑只显示「充电」 | 无障碍没开 / 面板被手动停在充电 | 重做 3.2；或手动点「传输文件」 |
| 资源管理器看不到设备 | 线是纯充电线 / 驱动问题 | 换线；设备管理器里看是否有黄色叹号；Win10+ 一般免驱 |
| 能看到设备但文件夹是空的 | 未解锁屏幕（Android 8 MTP 需解锁后才挂载） | 先解锁再插线 |
| 拷进去的歌不显示 | 列表未重扫 | 退出 App 再进，或设置里刷新媒体库 |
| 传输中断/速度极慢 | 老线材 + USB2.0 口 | 换线、换口 |

---

## 5. 恢复出厂后的重建清单（照着做，约 25 分钟）

前提：你已经备份了 `podclassic.keystore`、`local.properties`、音乐库（或音乐还在 SD 卡/电脑里）。

1. **手机**：跳过开机向导里的账号登录（不登 Google 也能用），进系统后开开发者选项 + USB 调试（3.1）
2. **电脑**：确认 JDK17/SDK/adb 可用（1.3）
3. **恢复签名**：把备份的 `podclassic.keystore` 放回仓库根目录，把 `local.properties` 写回（1.4）
4. **构建**：`assembleRelease`（2），确认 `BUILD SUCCESSFUL`
5. **装机**：`adb install -r`（3.1）
6. **绑无障碍**：跑 3.2 的四条 settings 命令，等 12s
7. **首次设置**：电池白名单、锁屏滑动、设备管理员、亮度/旋转（3.3）
8. **灌音乐**：插线传 `Music/`（4.3）
9. **验收**：按第 6 节逐项过一遍
10. **存档**：旧机器的游戏存档在 App 私有目录，恢复出厂即丢失；如需保留，重置前在 App 设置里导出（若版本支持）或在开发者选项下 `adb backup`

---

## 6. 验收清单（做完必须逐项确认）

| # | 动作 | 期望结果 |
| --- | --- | --- |
| 1 | 冷启动 App | 直接进主界面，无状态栏/导航栏，金色机身配色 |
| 2 | 三指上滑 | 任意页面回主菜单 |
| 3 | 长按音量下 1.2s | 回桌面（逃生用） |
| 4 | 长按电源键 | 弹出 PodClassic 风格菜单（锁屏/关机/重新启动），系统白底菜单被盖住 |
| 5 | 插数据线 | 面板自动切到「传输文件」，电脑能看到存储 |
| 6 | 播放一首歌 | 封面/歌名/进度/音量条正常；后台播放不被杀 |
| 7 | 进游戏 → 侧滑退出 | 只保留侧边退出；折叠态不显示多余控制带 |
| 8 | 扫雷 | 三档难度、方形触控板、磁吸光标、「开/旗」两键、结束卡标题正确 |
| 9 | 视频 | 默认无控制键，点一下出现，再点消失；全屏无悬浮退出条 |
| 10 | 照片 | 3×2 缩略图墙，点击全屏查看 |

---

## 7. 版本与发布

- 版本号在 `app/build.gradle` 的 `versionCode` / `versionName`，每次功能改动 +1。
- 发布流程：改版本 → `assembleRelease` → 复制 APK 到 `podclassic/` → 提交源码与文档 → 打 GitHub Release 附 APK。
- 当前：`versionCode 105 / versionName 1.5.71`。

---

## 8. 目录说明

```text
podclassic-git/
├─ app/src/main/java/io/github/xejohv/podclassicxzs/
│  ├─ activity/    主 Activity
│  ├─ view/        各页面（主菜单、播放器、文件、相机、视频、设置、电源菜单）
│  ├─ game/        7 个游戏 + GameHost（HUD/结束卡/控制条/存档）
│  ├─ gestures/    GestureService：三指手势、插线应答、电源菜单代点
│  ├─ media/       音频会话与焦点
│  ├─ base/        主题、配色、屏幕抽象
│  └─ values/      颜色与尺寸常量
├─ docs/           本手册与 AI 部署提示词
└─ podclassic.keystore   ← 本地存在，不进仓库
```
