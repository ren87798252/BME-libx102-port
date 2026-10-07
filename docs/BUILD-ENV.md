# BetterMiuiExpress — libxposed API 102 手机编译

> ⚠️ **本文档已过时，请以 [`BUILD-RESULT.md`](BUILD-RESULT.md) 为准。**
>
> 拿到 root 后已完成全自动编译（产物 `out/BME-libx102-debug.apk`）。
> 本文档中以下结论**经实测是错的**，已在 BUILD-RESULT.md 中修正：
>
> 1. "只能在 /sdcard 写脚本、必须你手动在 Termux 里跑"
>    → 用 `su -M` 全局 mount namespace + `su -M 10241` 切 Termux 身份，可全程自动
> 2. "DNS 被去广告模块 `GGAT_10007` 拦了"
>    → 真因是 FlClash fake-ip 代理未覆盖 Termux uid，让 Termux 走
>      `127.0.0.1:7890` 即可，无需动去广告模块
> 3. "aapt2 版本能否支持 compileSdk 37 待验证"
>    → 已验证：Termux 的 2.20 aapt2 可用，无需 AndroidIDE 版本
> 4. 另外多了一个本文档没提到的坑：SDK 里 API 37 只有 `android-37.0`，
>    必须设 `compileSdkMinor = 0`，否则报 `Failed to find target 'android-37'`

改造后的工程：`/sdcard/bme-build/BME-libx102`

两个脚本：
- `setup-termux.sh` — 配置环境
- `build-in-termux.sh` — 编译

---

## 一、为什么不能在 Termux 里自动执行

我运行在**沙箱**里（uid 10185，普通 app），不是 Termux。实测：

- `/data/data/com.termux` 不存在，`/data` 直接 Permission denied
- `am start` 被拒：`package=com.android.shell does not belong to uid=10185`
- `/proc` 里只有我自己的 2 个进程，看不到任何 Termux 进程

所以我只能**写脚本写到 `/sdcard`**（这是我唯一能访问的共享空间），
由你在 Termux 里执行。

---

## 二、在 Termux 里执行

**必须打开 Termux 应用本身的黑色终端窗口**，不要用系统 shell、
不要用 root（`su`）、不要用其他“Android 上跑脚本”的 App。

```bash
termux-setup-storage                  # 只需一次，让 Termux 能读写 /sdcard
bash /sdcard/bme-build/setup-termux.sh     # 装环境
bash /sdcard/bme-build/build-in-termux.sh  # 编译
```

日志分别留在 `/sdcard/bme-build/setup-log.txt` 和 `/sdcard/bme-build/build-log.txt`。

### 怎么确认自己在 Termux 里

```bash
bash /sdcard/bme-build/check-shell.sh
```

输出 `==> 是 Termux，可以继续` 才对。

### 常见错误：`pkg: inaccessible or not found`

如果看到：

```
setup-termux.sh[67]: pkg: inaccessible or not found
setup-termux.sh[67]: PREFIX: parameter not set
```

说明**脚本不是在 Termux 里跑的**。判断依据：

| 现象 | 含义 |
|---|---|
| 报错格式是 `script[67]:` | 这是 **mksh**（Android 自带 `/system/bin/sh`）。bash 会写成 `line 67:` |
| `PREFIX: parameter not set` | Termux 里 `PREFIX` 一定是 `/data/data/com.termux/files/usr`；系统 shell 里没有 |
| `pkg` 找不到 | `pkg` 是 Termux 内置命令 |

**不能通过 `su` 运行**：root shell 不会继承 Termux 的 `PREFIX` 等环境变量，
即使路径对也会失败。Termux 里也**不需要 root**（只编译 APK）。

两个脚本现在会先做这个自检，不满足条件会直接给出提示并退出，
不会再刷一堆看不懂的错误。

---

## 三、aarch64 上的两个关键坑（已处理）

### 1. aapt2 没有 arm64 版本 ← 最容易踩

`com.android.tools.build:aapt2` 在 Google Maven 上**只有 x86**：

| artifact | 状态 |
|---|---|
| `aapt2-…-linux.jar` | 200 |
| `aapt2-…-linux-aarch64.jar` | **404** |
| `aapt2-…-osx.jar` | 200 |
| `aapt2-…-windows.jar` | 200 |

AGP 默认会从 Maven 拉 aapt2 → 在手机上**必然失败**。

解决办法：用 Termux 的原生 aapt2，通过 AGP 的隐藏开关指过去：

```
-Pandroid.aapt2FromMavenOverride=$(command -v aapt2)
```

（该属性名从 AGP 8.12 的 `StringOption.class` 中提取确认：
`android.aapt2FromMavenOverride`、`android.aapt2Platform`、`android.aapt2Version`）

### 2. zipalign — 不用管

我一度想加 `-Pandroid.enableApkZipalign=false`，查证后发现**这个属性不存在**。
实际控制项是 DSL 的 `zipAlignEnabled`，而在 AGP 8.12 中它已废弃且无效：

> "this property is deprecated. Changing its value has no effect
> (AGP produced artifacts are already aligned)"

所以脚本里**没有**这个参数，也不会有问题。

---

## 四、版本选择依据

| 项 | 值 | 依据 |
|---|---|---|
| JDK | 17 | AGP 8.12 要求；Termux 有 `openjdk-17` |
| compileSdk | **37** | 三个 libxposed AAR 的 `aar-metadata.properties` 均为 `minCompileSdk=37` |
| 平台包名 | `platforms;android-37.0` | 仓库中 37 **只有小版本形式**，无裸 `android-37` |
| build-tools | 37.0.0 | 与 compileSdk 匹配 |
| Gradle | 8.13 | wrapper 已固定，配 AGP 8.12.0 |

**不要 `pkg install gradle`** —— Termux 的是 9.8.0，与 AGP 8.12 不匹配；
用项目自带的 wrapper（会自动下载 8.13）。

---

## 五、一个待验证点

Termux 的 aapt2 版本是 `16.0.0.4`（来自 `github.com/termux/android-build-tools`），
这个版本号方案我无法从外部映射到对应的 build-tools 版本，
**因此不能确定它是否支持 compileSdk 37 的资源编译**。

`setup-termux.sh` 会打印 `aapt2 version` 的真实输出。
如果它太旧，AGP 会报 “AAPT2 ... is too old” —— 届时改用 AndroidIDE 的
arm64 aapt2 即可（同样用 `aapt2FromMavenOverride` 指过去）。

---

## 六、运行前提（编译之外）

1. **框架必须支持 libxposed 现代 API（101/102）。**
   官方 `LSPosed/LSPosed` 不支持（其 `LSPosedBridge` 仍是老接口），装它模块不会被加载。
2. 作用域：`com.miui.personalassistant`（已写入 `scope.list`）。
3. 首次使用要先打开模块主界面完成初始化（生成 track id 并写入
   remote preferences，供被 hook 进程读取）。
