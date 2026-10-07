# 编译成功 —— BetterMiuiExpress (libxposed API 102)

## 结果

```
APK:  /sdcard/bme-build/out/BME-libx102-debug.apk   (7,490,653 字节, ~7.1 MB)
```

| 项 | 值 |
|---|---|
| package | `com.moefactory.bettermiuiexpress` |
| versionName / versionCode | 1.7.4 / 31 |
| minSdk / targetSdk | 24 / 35 |
| compileSdk | 37 (`android-37.0`) |
| build-tools | 37.0.0 |
| 签名 | **v2 方案，Android Debug 证书**（`apksigner verify` 通过） |
| META-INF/xposed | `module.prop` `scope.list` `java_init.list` 三个都在 |
| 入口类 | `com.moefactory.bettermiuiexpress.hook.HookEntry` 已在 classes8.dex |
| XposedProvider | `com.moefactory.bettermiuiexpress.XposedService` 已合并进 manifest |

`module.prop` 内容确认：`minApiVersion=102` `targetApiVersion=102`
`staticScope=true` `exceptionMode=protective`

---

## 一、编译环境的真实情况（与原 BUILD-ENV.md 的差异）

原文档假设"我的沙箱不是 Termux，只能靠你手动执行"。本次有 root 后全部打通，
**全程无需人工操作**。

### 1. 我现在的身份与能力

```
uid=0(root)  context=u:r:ksu:s0     ← KernelSU
```

关键能力（前一版文档里判断为"不可能"的那些）：

| 需求 | 突破点 |
|---|---|
| 看到 Termux 的数据目录 | `su -M` 强制**全局 mount namespace**。默认 namespace 里 `/data/data/com.termux` 不存在，只在此 namespace 可见 |
| 以 Termux 身份执行 | `su -M 10241 -c ...`（uid 10241 = `u0_a241`）。**必须 `-M` 和 uid 同时给**，只给一个会失败 |
| 在 Termux 里跑脚本 | Termux 读不到 `/sdcard`（scoped storage），且 `/sdcard` 是 noexec。脚本必须由 root 复制到 `$HOME` 再用绝对路径调用 |

### 2. 网络：真正的拦路虎是代理，不是去广告模块

前一版文档怀疑是 `GGAT_10007`（酷安去广告 hosts 模块，17224 条规则）拦了
`dl.google.com`。**这个判断是错的** —— 实测它只拦了一批广告域名。

真因是 **FlClash (`com.follow.clash`) 的 fake-ip 代理没覆盖 Termux 的 uid**：

| 身份 | `repo1.maven.org` | `github.com` |
|---|---|---|
| root | 200 | 200 |
| 本沙箱 (10185) | 200 | 200 |
| **Termux (10241)** | **000** | **000** |

域名被解析到 `198.18.0.x`（保留测试网段 = fake-ip 特征），但 VPN 路由不覆盖
Termux uid，于是拿到假 IP 就死了。

**解法**：Termux 虽然连不上外网，但**能访问本地代理端口**：

```bash
http_proxy=http://127.0.0.1:7890   # FlClash 的 mixed 端口
```

命令行工具（curl/wget/apt）认这个环境变量；**Java 程序不认**，要额外给：

```
JAVA_TOOL_OPTIONS=-Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890 ...
```

（sdkmanager / Gradle / Maven 都是 Java 程序，都必须加这一条。）

### 3. aapt2 的坑：确认无疑

Google 的 `build-tools;37.0.0` 里 aapt2 实测是 **x86-64**：

```
ELF shared object, 64-bit LSB x86-64, dynamic (/lib64/ld-linux-x86-64.so.2)
```

在 aarch64 上执行直接报 `not executable: 64-bit ELF file`。
`aapt / zipalign / aidl` 同样是 x86-64；只有 `d8` / `apksigner` 是脚本
（所以它们能用，因为走的是 Java）。

**解法**（前一版文档推测的方向是对的）：

```
-Pandroid.aapt2FromMavenOverride=$PREFIX/bin/aapt2
```

Termux 的原生 aapt2：`Android Asset Packaging Tool (aapt) 2.20-android-16.0.0_r4`
—— **实测能处理 compileSdk 37 的资源**，没有出现 "AAPT2 is too old"。
前一版文档里标注的"待验证点"到此关闭。

zipalign 确实不需要管（AGP 8.12 产物自带对齐）。

---

## 二、编译过程中实际踩到并修掉的 4 个问题

### 问题 1：SDK 里没有裸 `android-37`，只有 `android-37.0`

```
Failed to find target with hash string 'android-37' in: .../android-sdk
```

Google 改了命名：API 37 起平台包带小版本号。

查证过程（不是猜的）：

* `sdkmanager --list` 里只有 `platforms;android-37.0` / `37.1` / `37.2`，无 `android-37`
* 反编译 AGP 8.12 的 `sdklib` 里 `AndroidVersion`，常量池有正则
  `(\d+)(\.(\d+))?(-ext(\d+))?` 和字符串 `android-36-ext` → **minor 是支持的**
* 反编译 `CompileSdkVersionImpl.toHash()`（javap 字节码）确认逻辑：
  ```
  hash = "android-" + apiLevel
  if (minorApiLevel != null) hash += "." + minorApiLevel
  ```
  **minor 为 null 时不会拼 ".0"，所以得到 `android-37` 而不是 `android-37.0`**
* `CommonExtensionImpl.setCompileSdkMinor` 存在，且约束是
  "Minor versions are only supported for API 36 and above."

**修复**（`app/build.gradle`）：

```groovy
compileSdk = 37
compileSdkMinor = 0            // ← 关键，缺这行就生成 "android-37"
buildToolsVersion = "37.0.0"
```

同时 `gradle.properties` 加 `android.suppressUnsupportedCompileSdk=37.0`
（AGP 8.12 官方只测到 36，属非致命警告）。

### 问题 2：两个 libxposed 库声明 minSdk 26，工程是 24

```
Manifest merger failed : uses-sdk:minSdkVersion 24 cannot be smaller than
version 26 declared in library [io.github.libxposed:service:102.0.0]
```

先核实了"是不是真的需要 26"，而不是直接抬 minSdk：

* `service` 源码：最"新"的 API 是 `java.util.Objects`（API 19）、
  `ConcurrentHashMap`（API 1）、`ParcelFileDescriptor`（API 1）
  → 无 `java.nio.file` / `java.time` / `java.util.stream` / `String.join`
* `interface` 源码：只有 4 个 AIDL 接口文件，**零 import**

结论：这两个库的 `minSdkVersion=26` 是形式上的，实际没有 API 26 调用。

**修复**（`app/src/main/AndroidManifest.xml`）：用 `overrideLibrary` 放行，
保留工程原本的 minSdk 24。注意**两个库的包名不同**，要都写：

```xml
<uses-sdk tools:overrideLibrary="io.github.libxposed.service,io.github.libxposed.service.interfaces" />
```

（`service` 的包名是 `io.github.libxposed.service`，
`interface` 的包名是 `io.github.libxposed.service.interfaces`。
只写前者会接着报后者的错 —— 实际就连续报了两次。）

### 问题 3：public inline 函数访问 private const

```
e: NetworkFlowResource.kt:18:15 Public-API inline function cannot access
   non-public-API property.
```

`NetworkBoundResource` 是 `inline` 函数，里面 `Log.e(TAG, ...)`
用了文件级 `private const val TAG`。Kotlin 不允许 public inline 函数内联
private 成员。

**修复**：把字面量直接内联进调用点，删掉那个 `private const val TAG`。

### 问题 4：`List<*>` 的元素是 `Any?`

```
e: PAExpressRepositoryHook.kt:56:35 Only safe (?.) or non-null asserted (!!.)
   calls are allowed on a nullable receiver of type 'Any?'.
```

`chain.getArg(0) as? List<*>` 的星投影元素类型是 `Any?`，
而 `fun Any.toExpressInfoWrapper()` 定义在非空 `Any` 上。

原工程用 KavaRef 的 `cast<java.util.List<*>>()`，那个 API 返回的是非空包装，
所以原代码能编过；换成 `as?` 后 nullability 变了。

**修复**：`.map { it.toExpressInfoWrapper() }` → `.mapNotNull { it?.toExpressInfoWrapper() }`
（语义上也正确：顺带滤掉列表里的 null 元素。）

---

## 三、最终脚本与调用方式

### `/sdcard/bme-build/txrun.sh` —— Termux 环境封装

以 Termux 身份、在全局 namespace 下执行命令，自动注入环境变量和代理。
**必须复制到 Termux 的 `$HOME` 才能执行**（`/sdcard` 是 noexec）。

```bash
# 由 root 部署一次
H=/data/data/com.termux/files/home
su -M 0 -c "cp /sdcard/bme-build/txrun.sh $H/txrun.sh && chown 10241:10241 $H/txrun.sh && chmod 755 $H/txrun.sh"

# 之后任何 Termux 命令都这样调
su -M 10241 -c "$H/txrun.sh bash -c 'java -version'"
```

里面注入的内容：`PREFIX` / `HOME` / `PATH` / `LD_LIBRARY_PATH` / `TMPDIR` /
`ANDROID_HOME` / `JAVA_HOME=$PREFIX/lib/jvm/java-17-openjdk` /
`http_proxy` / `https_proxy`。

> 注意 `JAVA_HOME` 是 `$PREFIX/lib/jvm/java-17-openjdk`，
> **不是** `$PREFIX/opt/openjdk`（后者不存在，sdkmanager 会报 invalid directory）。

### `/sdcard/bme-build/run-build.sh` —— 实际编译脚本

```bash
su -M 10241 -c "cd $H && exec setsid nohup $H/txrun.sh $H/run-build.sh >/dev/null 2>&1 &"
```

`setsid nohup ... &` 是为了让它脱离调用会话 —— 编译要几分钟，不能挂在前台。
日志在 Termux 的 `~/build.log`。

脚本里两个必要处理：

* `bash ./gradlew ...` —— `gradlew` 的 shebang 是 `#!/usr/bin/env sh`，
  而 Android 没有 `/usr/bin/env`，直接执行会报
  `cannot execute: required file not found`，必须用 bash 显式跑
* `-Pandroid.aapt2FromMavenOverride=$PREFIX/bin/aapt2`
* 堆内存收小：`-Xmx2048m -XX:MaxMetaspaceSize=768m`
  （设备 15 GB 但空闲仅 ~400 MB，靠 zram swap 撑；小堆更稳）

---

## 四、复现步骤（全自动，无需人工）

```bash
# 1. 部署封装脚本
H=/data/data/com.termux/files/home
su -M 0 -c "cp /sdcard/bme-build/txrun.sh /sdcard/bme-build/run-build.sh /sdcard/bme-build/sdkinstall.sh $H/ && \
            chown 10241:10241 $H/*.sh && chmod 755 $H/*.sh"

# 2. 同步源码（Termux 读不到 /sdcard，必须由 root 拷）
su -M 0 -c "rm -rf $H/bme && mkdir -p $H/bme && \
            cp -r /sdcard/bme-build/BME-libx102/. $H/bme/ && \
            chown -R 10241:10241 $H/bme"

# 3. 装 SDK（只需一次；已装好则可跳过）
su -M 10241 -c "$H/txrun.sh bash $H/sdkinstall.sh"

# 4. 编译
su -M 10241 -c "cd $H && exec setsid nohup $H/txrun.sh $H/run-build.sh >/dev/null 2>&1 &"

# 5. 看日志
su -M 0 -c "tail -f $H/build.log"

# 6. 取产物（在 Termux 里生成，root 拷回 /sdcard）
su -M 0 -c "cp $H/bme/app/build/outputs/apk/debug/app-debug.apk /sdcard/bme-build/out/BME-libx102-debug.apk"
```

---

## 五、已验证的次生结论

* **`aapt2FromMavenOverride` 确实有效**，且 Termux 的 2.20 aapt2 能编译 37 资源。
* **不必动 `GGAT_10007` 去广告模块**，只需让 Termux 走本地代理。
* **编译产物正确性**：
  * `io.github.libxposed.api.*` 未被误打包（`compileOnly` 生效，
    dex 里只有引用、没有类定义）
  * `io.github.libxposed.service.*` 已打包（`implementation` 生效），
    且 XposedProvider 以 `${applicationId}.XposedService` 合并进 manifest
  * 无 v1 签名（`META-INF/*.RSA` 不存在）是现代 APK 的正常形态，
    v2 签名块在 zip 尾部
* **框架侧**（上一轮已确认）：`LSPosed IT v2.2.0-it (7912)` 的 framework.dex
  含 `XposedModuleInterface$HotReloadingParam` / `HotReloadedParam`，
  这是 API 102 专属特性 → 框架支持本模块。

---

## 六、仍然需要人工做的（编译之外）

1. **安装 APK**：`adb install` 或点开安装。
2. **在 LSPosed 里启用模块**，作用域勾 `com.miui.personalassistant`
   （`scope.list` 已内置，通常会自动带出）。
3. **首次打开模块主界面完成初始化**（生成 track id 写入 remote preferences，
   被 hook 进程靠它读；不初始化的话快递查询拿不到数据）。
4. 目标应用需**重启**（划掉后台或重启手机）后 hook 才生效。
