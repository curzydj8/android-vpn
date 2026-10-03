# 错误 002：Gradle Daemon 无法启动

日期：2026-10-03
阶段：安卓客户端构建

## 现象

执行 `gradle assembleDebug`（试过 `--no-daemon`、`-Xmx1g` 限内存）均失败：

```
FAILURE: Build failed with an exception.
* What went wrong:
Could not receive a message from the daemon.
```

Daemon 进程起不来，客户端连不上。`~/.gradle/daemon/` 下无日志。

## 根因

沙盒环境限制：Gradle Daemon 需要 fork 子进程并通过本地 socket 与客户端通信，本环境该机制不可用。Java 本身正常（`javac`/`java` 可跑），内存也有 763MB 可用，排除 JVM/内存问题。

## 修复

弃用 Gradle，改用 Android SDK build-tools 手动构建（`android/build.sh`）：

```
aapt2 compile → aapt2 link → javac → d8 → zip → zipalign → apksigner sign
```

关键细节：
- 顺序必须是**先 zipalign 后 sign**，反了 `apksigner verify` 报 "Signature stripped"。
- `apksigner` 密码格式是 `--ks-pass pass:xxx`（空格分隔，不是 `--ks-pass:xxx`）。
- `javac` 处理中文需加 `-encoding UTF-8`。
- build-tools zip 包内目录名是 `android-14`，需重命名为版本号（如 `34.0.0`）。

## 验证

`mars-vpn-v1.0.apk` 构建成功，`apksigner verify` 通过。
