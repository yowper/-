# 双击音量减键 → 点击 + 上滑

一个极简的 Android 无障碍工具：**全局双击音量减键**，自动执行「点一下屏幕 → 等 5.5 秒 → 向上滑动」。

## 原理

| 需求 | 实现 |
|---|---|
| 悬浮球单击触发 | `TYPE_ACCESSIBILITY_OVERLAY` 窗口 + 自己判拖动/点击（无需额外权限） |
| 悬浮球拖动 | `onTouchEvent` 里 `updateViewLayout`，位置写 SharedPreferences |
| 音量键双击（备选） | `onKeyEvent()` + `flagRequestFilterKeyEvents` |
| 模拟点击 | `dispatchGesture()`，80ms 的原地手势 |
| 模拟上滑 | `dispatchGesture()`，从屏幕 75% 高度滑到 25% 高度 |
| 后台常驻 | 前台服务 + 常驻通知 + 电池优化白名单 |

## 为什么不能「全屏双击」

Android 的触摸事件**只能分发给一个窗口**。想让第三方 App 监听到全屏双击，就必须铺一层全屏透明窗口去接触摸，那下面那个 App 就永远收不到手指了：进度条拉不动、按钮点不了、滑动失效。

系统能“双击截屏 / 三指下滑截屏”，是因为它是系统本身，站在触摸分发的源头，第三方 App 拿不到这个位置。

所以这里用 **悬浮球**：一个 56dp 的半透明小球浮在其他 App 上面，双击它触发，只占自己那一小块，其余屏幕完全不受影响。小球可以按住拖到顺手的位置，位置会记住。

（Android 14 新增了 `AccessibilityService.onMotionEvent`，理论上可以被动观察触摸，但版本覆盖太窄且行为不确定，没采用。）

不需要 root，不需要悬浮窗权限，**只需要打开无障碍服务**。

## 方式一：云端构建（推荐，本地不装任何东西）

本工程已经带了 `.github/workflows/build.yml`，推到 GitHub 就会自动编译出 APK。

1. 打开 https://github.com/new ，新建一个仓库，名字随意（比如 `volumeswipe`），Public / Private 都行。
2. 把 **`VolumeSwipe` 文件夹里面的内容**（不是文件夹本身）拖进网页上传：
   `app/`、`.github/`、`build.gradle`、`settings.gradle`、`gradle.properties`、`.gitignore` 要直接在仓库根目录。
3. 提交后点仓库顶部的 **Actions** 标签，会看到 `Build APK` 正在跑，约 2～4 分钟。
4. 跑完点进这次运行，页面拉到底部 **Artifacts** → 下载 `VolumeSwipe-debug.zip`。
5. 解压得到 `app-debug.apk`，传到手机安装（需允许“未知来源”）。

想重新编译：Actions → Build APK → 右上角 `Run workflow`。

> private 仓库免费额度 2000 分钟/月，这个工程一次约 3 分钟，平均每天跑 10 次也用不完。

## 方式二：本地一键编译（无需注册、无需 Android Studio）

双击 **`build_apk.bat`** 就行。脚本会自动完成：

1. 下载 OpenJDK 17（190MB，华为云镜像）→ 解到 `jdk-17\`
2. 下载 Android cmdline-tools（130MB，腾讯镜像）→ 解到 `android-sdk\`
3. 下载 Gradle 8.7（130MB，腾讯镜像）→ 解到 `gradle-8.7\`
4. 自动接受许可协议，安装 `platforms;android-34` + `build-tools;34.0.0`
5. `gradle assembleDebug`
6. 把 `app-debug.apk` 拷到桌面，改名 `VolumeSwipe-debug.apk`

首次总共下载约 450MB（含 Gradle 依赖），慢慢等；之后改代码再跑就很快。

如果你想自己下，下载地址如下（均已实测可访问）：

| 包 | 国内镜像 | 官方 |
|---|---|---|
| JDK 17 | `mirrors.huaweicloud.com/openjdk/17.0.2/openjdk-17.0.2_windows-x64_bin.zip` | adoptium.net |
| cmdline-tools | `mirrors.cloud.tencent.com/AndroidSDK/commandlinetools-win-11076708_latest.zip` | dl.google.com |
| Gradle 8.7 | `mirrors.cloud.tencent.com/gradle/gradle-8.7-bin.zip` | services.gradle.org |

手动下载的话，把 JDK 解到 `jdk-17\`（里面要有 `bin\java.exe`），cmdline-tools 解到 `android-sdk\cmdline-tools\latest\`，Gradle 解到 `gradle-8.7\`，再双击 bat。

## 方式三：本地用 Android Studio

1. 下载安装：https://developer.android.com/studio （含 JDK 和 Android SDK）
2. `File → Open…` → 选中 `VolumeSwipe` 文件夹 → 等底部 Gradle 同步完成
3. 手机开 USB 调试连电脑，顶部设备列表出现你的手机后点绿色 ▶；
   或 `Build → Build Bundle(s)/APK(s) → Build APK(s)`

## 怎么用

1. 打开 App，点 ① 开启无障碍服务
2. 退出 App，屏幕上会出现一个**小蓝球**
3. **单击小球** → 立即执行：点击屏幕中间（暂停）→ 等 5.5 秒 → 向上滑
4. 把小球按住拖到顺手的位置（位置会记住）
5. 不想看到小球就取消勾选「显示悬浮球」，改用双击音量减键

## 装到手机之后

按界面上的 ①②③④ 顺序走一遍。不想用了点 ⑤ 退出。

## 后台能不能一直跑？

先明确一件事：**无障碍服务是系统托管的，不是 Activity 的附属**。你把 App 从最近任务里划掉，服务通常**照样活着**，因为它是 `AccessibilityManager` 绑定起来的，生命周期归系统管。所以「退到后台」本身不是问题。

真正会停掉它的只有三种情况：

| 情况 | 结果 | 处理 |
|---|---|---|
| 被 ROM 省电策略回收 | 服务静默丢失 | 开 ③ 常驻 + ④ 忽略电池优化 |
| 手动「强行停止」 | 无障碍开关会被系统强制关闭 | 用 App 内的 ⑤ 退出，别用系统那一项 |
| 重启手机 | 无障碍服务系统会自动恢复 | 无需处理（常驻服务会在 `onServiceConnected` 里自动拉起） |

### 怎么退出（App 内 ⑤）

点 ⑤ 退出会弹一个确认框，两个选项：

| 选项 | 做了什么 | 下次怎么恢复 |
|---|---|---|
| **退出** | `enabled=false` 写入配置、停掉常驻服务（通知消失）、关窗口。无障碍开关保留。 | 重新打开 App 自动恢复 |
| **彻底停用** | 在上一项基础上再调 `disableSelf()`，把系统里的无障碍开关一起关掉 | 到系统设置里重新开启 |

为什么不全用一个？因为 `disableSelf()` 是单向的：Android 不允许程序自己把无障碍开关**打开**（必须用户手动确认）。所以默认走「退出」，保留开关，下次一键恢复；只有你确实不想再用了，才选「彻底停用」。

注意：不要用系统设置里的「强行停止」。它会直接杀死进程，无障碍开关会变成“开着但实际已死”的状态，反而更难排查。

### 本工程做了哪些加固

1. **前台常驻服务** `KeepService`：带一条低优先级常驻通知，让系统把本进程当「前台进程」，回收优先级降到最低。同时你能从状态栏一眼看出它还在不在。
2. **电池优化白名单**：界面 ④ 直接弹系统授权框（`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`），这是 MIUI / 鸿蒙 / ColorOS 不杀的关键。
3. **开机自动恢复**：无障碍服务本身由系统在开机时重启，`AutoService.onServiceConnected()` 里会顺手把 `KeepService` 再拉起来，不需要 `BOOT_COMPLETED` 广播。
4. **状态自检**：App 首页三行分别显示「无障碍 / 常驻服务 / 电池优化」是否就绪，哪一项掉了直接点对应按钮。

### 还需要手动做的

系统设置 → 应用管理 → 本应用：

- 允许「自启动」
- 允许「后台弹出界面」/「后台运行」
- 电池 → 无限制 / 允许后台活动

这三项每个 ROM 叫法不同，但都是同一个意思：别在后台把我掐了。

## 参数

| 参数 | 默认 | 说明 |
|---|---|---|
| 双击间隔 | 500 ms | 两次按下的最大间隔，超过就算两次单击 |
| 点击后等待 | 5500 ms | 点击（暂停）之后等多久再上滑，5~6 秒就填 5000~6000 |
| 上滑时长 | 300 ms | 数值越小滑得越"甩"，越大越"拖" |
| 点击 X/Y | 50 / 50 | 屏幕百分比坐标，用来命中"暂停"按钮 |

改完点「② 测试一次」会自动保存并立刻执行一次。

## 常见问题

- **通知栏没有「运行中」那条**：点 ③。Android 13+ 首次会弹通知权限，要允许；如果之前拒过，去系统设置里手动打开通知。
- **过一会儿就失效了**：看首页第二行是不是变成「未运行」，以及第四项电池优化是不是「未忽略」。
- **开了服务没反应**：MIUI / 鸿蒙 / ColorOS 会限制无障碍服务，需要在系统设置里放行后台运行，并关掉电池优化。
- **音量还是会掉一格**：`onKeyEvent` 故意 `return false` 不吞按键（否则你平时就没法调音量了），触发后代码会把媒体音量还原。
- **上滑位置不对**：改 `AutoService.runSequence()` 里的 `0.75f` / `0.25f`，或者把这两个也做成参数。
- **等太久/太快**：改界面上的「点击后等多久再上滑」，默认 5500ms（5.5 秒）。
- **连续双击会叠加吗**：不会。一轮动作没走完（含等待）时，新的双击会被忽略。

## 目录结构

```
VolumeSwipe/
├── build.gradle
├── settings.gradle
├── gradle.properties
└── app/
    ├── build.gradle
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/example/volumeswipe/
        │   ├── MainActivity.java
        │   ├── AutoService.java      ← 双击识别 + 手势
        │   └── KeepService.java      ← 前台常驻
        └── res/
            ├── layout/activity_main.xml
            ├── values/strings.xml
            └── xml/accessibility_config.xml
```
