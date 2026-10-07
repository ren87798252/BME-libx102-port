# Release 构建与签名

## 产物

```
BME-libx102-1.7.4-release.apk      1,756,184 字节 (~1.67 MiB)
SHA-256  a0ba524590a4bf4f5215b1da7f835a362a177fc953e3ac01c49ee3fdc26c0155
```

对比：同一份代码的 debug 构建为 7,490,653 字节。
release 经 R8 混淆 + 资源压缩，体积缩减约 77%。

## 签名信息

```
方案        APK Signature Scheme v2（无 v1）
算法        RSA 2048
DN          CN=BetterMiuiExpress libx102 port, OU=dev, O=personal, L=NA, ST=NA, C=CN
有效期      10950 天（30 年）
别名        bme

证书 SHA-256  1B:74:04:BE:87:8A:EF:D0:16:13:B6:F2:AD:93:24:A8:18:06:3B:40:9C:3B:F1:90:51:C8:98:22:22:63:42:7A
证书 SHA-1    A9:57:F5:5F:88:AC:52:21:27:C5:4E:C6:1E:29:E9:DD:59:D2:7D:5B
```

> ⚠️ 这是本项目**自建**的密钥，与上游 BetterMiuiExpress 的发布密钥无关。
> 无法与上游版本互相覆盖安装（签名不同），如需从上游版本升级请先卸载。
>
> 密钥库不在本仓库中。若你要自己重签名，用你自己的密钥即可。

### 验证产物

```bash
apksigner verify --print-certs -v BME-libx102-1.7.4-release.apk
# 或
keytool -printcert -jarfile BME-libx102-1.7.4-release.apk
```

## 自己构建

### 1. 生成密钥库（只需一次）

```bash
keytool -genkeypair \
  -keystore ~/bme-release.jks \
  -alias bme -keyalg RSA -keysize 2048 -validity 10950 \
  -dname "CN=Your Name, O=Your Org, C=CN"
```

### 2. 写 `local.properties`

```properties
signing.storeFile=/绝对/路径/bme-release.jks
signing.storePassword=你的库口令
signing.keyPassword=你的密钥口令
signing.keyAlias=bme
```

要点：

- **`signing.storeFile` 请用绝对路径。** `app/build.gradle` 里的
  `file(storeFilePath)` 是相对**模块目录**（`app/`）解析的，
  写相对路径很容易指错位置，且不会报错。
- `local.properties` **不要提交**（已在 `.gitignore` 中）。
- 若 `signing.storeFile` 指向的文件不存在，构建不会失败，
  只会产出 `app-release-unsigned.apk`。

### 3. 构建

```bash
cd src
bash ./gradlew :app:assembleRelease
```

产物在 `app/build/outputs/apk/release/app-release.apk`。

## 上游 `app/build.gradle` 的一个坑（本仓库已修）

原工程的 `signingConfigs` 块**定义了** `release` 配置，但 `buildTypes.release`
里**没有引用它**。AGP 只会为 `debug` 自动套用同名 signingConfig，
release 必须显式写：

```groovy
buildTypes {
    release {
        // ...
        if (signingConfigs.findByName('release') != null) {
            signingConfig signingConfigs.release
        }
    }
}
```

不写这行的表现是：配置齐全、构建成功、但产物叫
`app-release-unsigned.apk`（未签名），而且**没有任何报错或警告**，
比较隐蔽。用 `findByName` 做保护是为了在没配置密钥时仍能构建
（此时保持无签名，而不是让配置阶段失败）。

## 混淆后的完整性检查

release 开了 `minifyEnabled true`，模块入口类一旦被 R8 移除或改名，
框架就加载不了。已验证：

```
META-INF/xposed/module.prop      minApiVersion=102  targetApiVersion=102
META-INF/xposed/java_init.list   com.moefactory.bettermiuiexpress.hook.HookEntry
META-INF/xposed/scope.list       com.miui.personalassistant

classes.dex 中：
  HookEntry 保留原名（未被混淆）
  其父类仍为 Lio/github/libxposed/api/XposedModule;
```

依赖隔离也正确：

- `io.github.libxposed:api` 为 `compileOnly`，dex 中**只有引用、无类定义**
- `io.github.libxposed:service` 为 `implementation`，**已打入** dex
  （XposedProvider 经 manifest 合并，authorities =
  `com.moefactory.bettermiuiexpress.XposedService`）

这些靠 `src/app/proguard-rules.pro` 里的规则保证。
