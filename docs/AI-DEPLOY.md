# 交给 AI 一键部署 / One-shot AI deploy

把下面整段复制给任意能操作终端与 adb 的 AI（豆包、Claude、Cursor…），它就能按本仓库把一台索尼 Xperia XZs 部署成 PodClassic 音乐机。
详细原理与排障见 [DEPLOY.md](./DEPLOY.md)。

---

## 提示词（直接复制）

> 你是我的设备部署助手。目标：把一台 Sony Xperia XZs（SO-03J，Android 8.0，1080×1920）部署成 PodClassic 音乐播放器。
> 仓库：`https://github.com/xejohv/PodClassic-XZs`（应用包名 `io.github.xejohv.podclassicxzs`）。
>
> **工作方式**
> 1. 先自检环境：`java -version` 必须是 17；`adb version` 可用；`adb devices` 里有且只有一个 `device`。任何一项不满足先解决，不要硬往下走。
> 2. 拉代码：`git clone <仓库地址>`；如果没有 `local.properties`，照 `docs/DEPLOY.md` 1.4 写一份（没有 keystore 就走 debug 签名，能装即可，但要提醒我"签名不同会导致以后升级必须卸载重装"）。
> 3. 构建：设好 `JAVA_HOME` / `ANDROID_HOME` / `GRADLE_USER_HOME` 后执行 `gradlew.bat :app:assembleRelease --no-daemon`，日志重定向到文件。
>    **只认日志里的 `BUILD SUCCESSFUL`**；若日志停在某个 task 后不再增长且 `java` 进程已消失，判定为被中断，原命令重跑一次。
>    R8 阶段耗时 4~6 分钟属正常，不要提前判失败。
> 4. 装机：`adb install -r app/build/outputs/apk/release/app-release.apk`。
>    报 `INSTALL_FAILED_UPDATE_INCOMPATIBLE` 时先停下来告诉我（卸载会清空存档与播放列表），不要擅自 `adb uninstall`。
> 5. 绑定无障碍（必须）：依次执行
>    `settings put secure accessibility_enabled 0`
>    `settings put secure enabled_accessibility_services io.github.xejohv.podclassicxzs/io.github.xejohv.podclassicxzs.gestures.GestureService`
>    `sleep 2` → `settings put secure accessibility_enabled 1` → `sleep 12`
>    然后 `am start -n io.github.xejohv.podclassicxzs/.activity.MainActivity`。
> 6. 验证：每一步都用 `adb shell screencap -p /sdcard/x.png` + `adb pull` 抓图自己看，不要只靠命令返回码。
>    逐项确认：① 主界面无状态栏/导航栏 ② 三指上滑回主菜单 ③ 长按音量下 1.2s 回桌面 ④ 长按电源键出现 PodClassic 风格菜单
>    ⑤ 插线后面板自动选「传输文件」且电脑能看到存储 ⑥ 播放正常 ⑦ 游戏可进可退。
> 7. 交付：给出①最终 APK 路径 ②装机后每项验证的结论 ③剩余需要我人工做的操作（例如首次 USB 调试授权、传音乐）。
>
> **约束**：不要 root、不要解锁 BL、不要改系统分区、不要卸载我指定的 App；除构建与安装外不要动我的个人文件。

---

## 提示词背后的关键参数（AI 自查用）

| 项 | 值 |
| --- | --- |
| 包名 | `io.github.xejohv.podclassicxzs` |
| 主 Activity | `io.github.xejohv.podclassicxzs/.activity.MainActivity` |
| 无障碍组件 | `io.github.xejohv.podclassicxzs/io.github.xejohv.podclassicxzs.gestures.GestureService` |
| 构建产物 | `app/build/outputs/apk/release/app-release.apk` |
| 版本 | `versionCode 105` / `versionName 1.5.71` |
| 屏幕 | 1080×1920，density 3.375（1dp≈3.375px） |

## 常用命令速查

```powershell
# 构建
cmd /c ".\gradlew.bat :app:assembleRelease --no-daemon > build.log 2>&1"
Select-String -LiteralPath build.log -Pattern "BUILD SUCCESSFUL|BUILD FAILED|^e:" | ForEach-Object { $_.Line }

# 装机 + 绑无障碍
adb install -r app\build\outputs\apk\release\app-release.apk
adb shell settings put secure accessibility_enabled 0
adb shell settings put secure enabled_accessibility_services io.github.xejohv.podclassicxzs/io.github.xejohv.podclassicxzs.gestures.GestureService
Start-Sleep -Seconds 2
adb shell settings put secure accessibility_enabled 1
Start-Sleep -Seconds 12
adb shell am start -n io.github.xejohv.podclassicxzs/.activity.MainActivity

# 抓图
adb shell screencap -p /sdcard/s.png
adb pull /sdcard/s.png .\s.png
```

## 交给 AI 之前你自己要做的三件事

1. 手机上打开「USB 调试」，并在第一次插线时点「允许」（AI 无法替你点这个系统弹窗）。
2. 把 `podclassic.keystore` 与 `local.properties` 准备好（没有也能装，但以后升级要卸载重装）。
3. 决定是否接受「卸载重装 = 清空存档」这一后果（不接受就先备份）。
