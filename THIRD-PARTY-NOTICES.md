# 第三方组件声明

本项目（BetterMiuiExpress — libxposed API 102 移植）包含或依赖以下第三方组件。
所有协议信息取自各组件官方仓库或其 Maven POM。

---

## 1. 上游项目（本项目是其衍生作品）

### BetterMiuiExpress

- 仓库：https://github.com/Robotxm/BetterMiuiExpress
- 作者：Robotxm 及贡献者（@YifePlayte、@dreamy06、@wlt233 等，见上游 README 鸣谢）
- 协议：**GNU General Public License v3.0**
- 完整文本：见本仓库 [`LICENSE`](LICENSE)

**许可证义务说明**：GPLv3 具有传染性。本仓库的 `src/` 是上游源码的直接修改版
（非独立重写），因此本仓库整体以 GPLv3 发布。任何再分发或修改同样受 GPLv3 约束，
须提供完整源码。

```
BetterMiuiExpress
Copyright (C) Robotxm and contributors

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
```

---

## 2. libxposed —— 现代 Xposed API

本项目从传统 Xposed API 移植到 libxposed API 102，依赖以下 artifact。
均为 **Apache License 2.0**。

| Artifact | 版本 | 引入方式 | 协议 |
|---|---|---|---|
| `io.github.libxposed:api` | 102.0.0 | `compileOnly`（不打入 APK） | Apache-2.0 |
| `io.github.libxposed:service` | 102.0.0 | `implementation`（打入 APK） | Apache-2.0 |
| `io.github.libxposed:interface` | 102.0.0 | `service` 的传递依赖 | Apache-2.0 |
| `io.github.libxposed:annotation` | 1.0.0 | 传递依赖 | Apache-2.0 |

- 仓库：https://github.com/libxposed/api 、 https://github.com/libxposed/service 、
  https://github.com/libxposed/lint
- 项目主页：https://libxposed.github.io
- 协议全文：https://www.apache.org/licenses/LICENSE-2.0

**协议依据**（各 artifact 的 Maven POM 声明）：

```
$ curl -s https://repo1.maven.org/maven2/io/github/libxposed/api/102.0.0/api-102.0.0.pom
  <licenses>
    <license>
      <name>Apache License 2.0</name>
      <url>https://github.com/libxposed/api/blob/master/LICENSE</url>
    </license>
  </licenses>
```

**注意**：Apache-2.0 与 GPLv3 兼容（Apache-2.0 代码可并入 GPLv3 作品）。
本仓库整体仍为 GPLv3。

**注意**：`service` 打包进 APK，因此 APK 内含有 Apache-2.0 的代码。
Apache-2.0 要求保留版权声明与许可声明 —— 上游 AAR 的 `META-INF` 中已包含，
构建产物未剥离。

---

## 3. 构建工具链

| 组件 | 版本 | 协议 |
|---|---|---|
| Android Gradle Plugin (AGP) | 8.12.0 | Apache-2.0 |
| Gradle | 8.13 | Apache-2.0 |
| Kotlin / kotlin-gradle-plugin | 2.2.20 | Apache-2.0 |
| Android SDK Platform | android-37.0 | Apache-2.0 + [Android SDK ToS](https://developer.android.com/studio/terms) |
| Android SDK Build-Tools | 37.0.0 | 同上 |
| Android SDK Platform-Tools | 37.0.1 | 同上 |
| [Termux](https://github.com/termux/termux-app) | 0.118.3 | GPL-3.0 |
| [termux-packages](https://github.com/termux/termux-packages)（aapt2、openjdk-17 等） | — | 各包自身协议 |
| OpenJDK 17（Termux `openjdk-17`） | 17.0.20 | GPL-2.0-with-classpath-exception |

构建工具不进入 APK 产物，此处列出仅为记录构建环境。

---

## 4. 运行时依赖（打包进 APK）

| 组件 | 版本 | 协议 |
|---|---|---|
| [AndroidX Core KTX](https://developer.android.com/jetpack/androidx) | 1.17.0 | Apache-2.0 |
| [AndroidX AppCompat](https://developer.android.com/jetpack/androidx) | 1.7.1 | Apache-2.0 |
| [AndroidX ConstraintLayout](https://developer.android.com/jetpack/androidx) | 2.2.1 | Apache-2.0 |
| [AndroidX Fragment KTX](https://developer.android.com/jetpack/androidx) | 1.8.9 | Apache-2.0 |
| [AndroidX Activity KTX](https://developer.android.com/jetpack/androidx) | 1.11.0 | Apache-2.0 |
| [AndroidX Lifecycle](https://developer.android.com/jetpack/androidx) (viewmodel-ktx / livedata-ktx) | 2.9.4 | Apache-2.0 |
| [Material Components for Android](https://github.com/material-components/material-components-android) | 1.13.0 | Apache-2.0 |
| [Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines) | 1.10.2 | Apache-2.0 |
| [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) | 1.9.0 | Apache-2.0 |
| [OkHttp](https://github.com/square/okhttp) | 4.12.0 | Apache-2.0 |
| [Retrofit](https://github.com/square/retrofit) | 3.0.0 | Apache-2.0 |
| [retrofit2-kotlinx-serialization-converter](https://github.com/JakeWharton/retrofit2-kotlinx-serialization-converter) | 1.0.0 | Apache-2.0 |
| [TimelineView](https://github.com/vipulasri/Timeline-View) | 1.2.2 | Apache-2.0 |
| [BRV](https://github.com/liangjingkanji/BRV) | 1.6.1 | Apache-2.0 |

多数 AndroidX / Material 组件的版权声明随 AAR 一起打包，
可在 `META-INF/` 下找到对应的 `LICENSE.txt`。

---

## 5. 测试依赖（不进入 APK）

| 组件 | 版本 | 协议 |
|---|---|---|
| JUnit 4 | 4.x | Eclipse Public License 1.0 |
| AndroidX Test Ext JUnit | 1.3.0 | Apache-2.0 |
| Espresso | 3.7.0 | Apache-2.0 |

---

## 6. 框架（运行环境，非本项目分发内容）

| 组件 | 协议 | 说明 |
|---|---|---|
| [LSPosed](https://github.com/LSPosed/LSPosed) | Apache-2.0 | 官方版**不支持** libxposed 现代 API |
| LSPosed IT (ren87798252 分支) | Apache-2.0 | v2.2.0-it，已验证支持 API 102 |

Xposed / LSPosed 相关名称与接口归各自权利人所有。本项目是独立模块，
与 LSPosed 项目无隶属关系。

---

## 7. 数据来源

本模块查询快递信息时调用第三方 API：

| 服务 | 说明 |
|---|---|
| 快递100 (kuaidi100.com) | 查询接口。使用条款受该服务方约束；本模块需用户自行完成界面初始化后使用 |

本仓库不分发上述服务的任何内容，仅含客户端调用代码。
