# BetterMiuiExpress — libxposed API 102 移植

把 [BetterMiuiExpress](https://github.com/Robotxm/BetterMiuiExpress) 从
**YukiHookAPI / 传统 Xposed API** 移植到 **libxposed 现代 API（API 102）**，
并附上在 **Android 手机（aarch64 + Termux）上完成构建**的可复现流程。

> 这不是我的原创项目。上游代码版权归原作者所有，本仓库只做 API 移植与构建记录。
> 授权见 [LICENSE](LICENSE)（GPLv3，继承自上游）。

---

## 这个仓库是什么

| 目录 | 内容 |
|---|---|
| `src/` | 移植后的完整 Android 工程（可 `./gradlew :app:assembleDebug`） |
| `docs/` | 移植与编译的完整记录，含踩坑分析和证据 |
| `scripts/` | 在手机上构建用的脚本（Termux 环境封装、SDK 安装、编译） |
| `out/` | 已验证的构建产物 `BME-libx102-debug.apk` |

## 产物信息

### Release（推荐）

```
out/BME-libx102-1.7.4-release.apk     1,756,184 字节 (~1.67 MiB)

package            com.moefactory.bettermiuiexpress
versionName        1.7.4  (versionCode 31)
minSdk / targetSdk 24 / 35
compileSdk         37 (android-37.0)
R8 混淆            已开启（minifyEnabled + shrinkResources）
签名               v2 方案，RSA 2048，本项目自建密钥
SHA-256            a0ba524590a4bf4f5215b1da7f835a362a177fc953e3ac01c49ee3fdc26c0155

证书 SHA-256       1B:74:04:BE:87:8A:EF:D0:16:13:B6:F2:AD:93:24:A8:18:06:3B:40:9C:3B:F1:90:51:C8:98:22:22:63:42:7A
```

签名细节、自建构建流程、以及混淆后的完整性检查见
[`docs/RELEASE.md`](docs/RELEASE.md)。

### Debug

```
out/BME-libx102-debug.apk             7,490,653 字节 (~7.1 MiB)
签名               v2 方案，Android Debug 证书
```

**两者都**不是上游的正式发布版，用的是本项目自建密钥。
要正式使用请自行编译并用你自己的密钥签名。

## 移植做了什么

上游用 YukiHookAPI（基于传统 `de.robv.android.xposed.*` 接口）。
本移植改用 libxposed 现代 API，共触及 19 个文件：

**新增**
- `hook/HookEntry.kt` — 改为继承 `io.github.libxposed.api.XposedModule`，
  `onInit()` / `onHook()` 映射到 `onModuleLoaded()` / `onPackageReady()`
- `xposed/XposedServiceManager.kt` — 通过 libxposed service 注册作用域回调
- `hook/ReflectUtils.kt`、`ktx/TypeKtx.kt` — 替代 KavaRef（`resolve()` / `cast()`）
- `base/app/ModulePreferences.kt` — 改用 libxposed remote preferences
  替代 `XSharedPreferences`
- `src/main/resources/META-INF/xposed/` — 现代模块元数据三件套
  （`module.prop` / `scope.list` / `java_init.list`）

**删除**
- `ktx/YukiHookKtx.kt`、`model/` 下 YukiHookAPI 专用 wrapper

**改写**
- 两个 hook 从 `before { }` 风格改为
  `intercept { chain -> ...; chain.proceed() }`
- 入口从 `IYukiHookXposedInit` 改为 `XposedModule` 子类

## 编译过程中修掉的问题

完整分析（含反编译字节码取证）在 [`docs/BUILD-RESULT.md`](docs/BUILD-RESULT.md)。
四个问题：

1. **`Failed to find target with hash string 'android-37'`**
   SDK 里 API 37 只有 `platforms;android-37.0`，没有裸 `android-37`。
   AGP 的 hash 生成逻辑（`CompileSdkVersionImpl.toHash()`，javap 反编译确认）
   是 `"android-" + apiLevel`，**只有 minorApiLevel 非 null 时才追加 `"." + minor`**，
   所以必须显式设 `compileSdkMinor = 0`。

2. **`io.github.libxposed:service` / `:interface` 声明 minSdk 26，工程是 24**
   核实过源码：`service` 只用到 `java.util.Objects`(API 19) /
   `ParcelFileDescriptor`(API 1)；`interface` 是 4 个 AIDL 文件、零 import。
   → 用 `tools:overrideLibrary` 放行。注意**两个库包名不同**，
   必须都写（只写前者会接着报后者）。

3. **public inline 函数访问 private const**（`NetworkFlowResource.kt`）
   Kotlin 禁止 public inline 函数内联 private 成员 → 字面量直接内联。

4. **`List<*>` 元素类型是 `Any?`**（`PAExpressRepositoryHook.kt`）
   原码用 KavaRef 的 `cast<>()` 返回非空包装，换成 `as?` 后 nullability 变了
   → `.map` 改 `.mapNotNull { it?.… }`。

## 手机端构建（aarch64）

核心难点是 **Google 不提供 arm64 的 aapt2**：

```
build-tools/37.0.0/aapt2 → ELF 64-bit x86-64        ← 手机上跑不了
```

解法是把 AGP 指到 Termux 的原生 aapt2：

```bash
./gradlew :app:assembleDebug \
    -Pandroid.aapt2FromMavenOverride=$PREFIX/bin/aapt2
```

（实测 Termux 的 aapt2 2.20 能处理 compileSdk 37 的资源，无需 AndroidIDE 版本。）

`scripts/` 里的三个脚本：

| 脚本 | 用途 |
|---|---|
| `txrun.sh` | 注入 Termux 环境变量与代理，封装执行 |
| `sdkinstall.sh` | 装 platform-tools + `platforms;android-37.0` + `build-tools;37.0.0` |
| `run-build.sh` | 同步源码、写 `local.properties`、跑 `assembleDebug` |

`run-build.sh` 里两个容易漏的点：
- `gradlew` 的 shebang 是 `#!/usr/bin/env sh`，而 Android 没有 `/usr/bin/env`
  → 必须 `bash ./gradlew ...`
- Java 程序不认 `http_proxy` 环境变量，需要额外
  `-Dhttps.proxyHost=... -Dhttps.proxyPort=...`

复现步骤见 `docs/BUILD-RESULT.md` 第四节。

## 使用

1. 安装 `out/BME-libx102-debug.apk`
   （或自行编译：`cd src && ./gradlew :app:assembleDebug`）
2. 在 LSPosed 里启用模块，作用域勾 **智能助理**（`com.miui.personalassistant`）
3. **首次务必打开模块主界面完成初始化** —— 会生成 track id 写入
   remote preferences，被 hook 进程靠它读；跳过这步快递查询拿不到数据
4. 强制停止「智能助理」使其重新加载 hook
5. 之后点负一屏快递卡片就不会再跳第三方应用了

**强依赖框架支持 libxposed 现代 API（101/102）。**
官方 `LSPosed/LSPosed` 不支持（其 `LSPosedBridge` 仍是老接口），
装上去模块不会被加载。已验证可用的是 **LSPosed IT v2.2.0-it**。

## 第三方组件与协议

### 上游项目

| 项目 | 协议 | 用途 |
|---|---|---|
| [Robotxm/BetterMiuiExpress](https://github.com/Robotxm/BetterMiuiExpress) | **GPL-3.0** | 本项目的基础，本仓库是其衍生作品 |

**因为是 GPLv3 的衍生作品，本仓库整体也必须以 GPLv3 发布**（见 `LICENSE`）。
本仓库的 `src/` 不是干净重写，而是上游代码的直接修改版。

### libxposed（现代 Xposed API）

| 组件 | 协议 | 用途 |
|---|---|---|
| [libxposed/api](https://github.com/libxposed/api) | **Apache-2.0** | `compileOnly`，框架运行时提供 |
| [libxposed/service](https://github.com/libxposed/service) | **Apache-2.0** | `implementation`，打包进 APK |
| libxposed/interface | **Apache-2.0** | 传递依赖 |
| [libxposed/lint](https://github.com/libxposed/lint)（annotation） | **Apache-2.0** | 传递依赖 |

协议依据：各 artifact 的 Maven POM 中 `<license><name>Apache License 2.0</name></license>`
（`repo1.maven.org/maven2/io/github/libxposed/*/102.0.0/*.pom`）。

### 构建工具链

| 组件 | 协议 |
|---|---|
| [Android Gradle Plugin](https://developers.google.com/android) 8.12.0 | Apache-2.0 |
| [Gradle](https://gradle.org/) 8.13 | Apache-2.0 |
| [Kotlin](https://kotlinlang.org/) 2.2.20 | Apache-2.0 |
| Android SDK platform 37.0 / build-tools 37.0.0 | Apache-2.0（SDK 另有 [ToS](https://developer.android.com/studio/terms)） |
| [Termux](https://github.com/termux/termux-app) / termux-packages | GPL-3.0 / 各包自身协议 |
| OpenJDK 17（Termux `openjdk-17`） | GPL-2.0-with-classpath-exception |

### 运行时依赖

见 [`THIRD-PARTY-NOTICES.md`](THIRD-PARTY-NOTICES.md)。

## 已知限制

- 调试签名，不可用于正式分发
- 只验证了构建成功与 APK 结构正确；**未在真机端到端跑通**（需要你装上去测）
- 顺丰若使用隐私手机号，查不到详情（上游既有问题，非本次移植引入）
- 仅适用于支持 libxposed 现代 API 的框架

## 相关链接

- 上游：https://github.com/Robotxm/BetterMiuiExpress
- libxposed：https://github.com/libxposed
- 完整移植/编译记录：[`docs/BUILD-RESULT.md`](docs/BUILD-RESULT.md)
