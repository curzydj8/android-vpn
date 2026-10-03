# 03 - 安卓客户端

日期：2026-10-03

## 功能

- 输入服务器地址/端口/口令，一键连接/断开
- `VpnService` 创建 TUN，拦截手机全部流量（`0.0.0.0/0` 路由）
- 后台双线程：TUN→TCP 上行、TCP→TUN 下行
- 30 秒 PING 保活

## 代码结构

```
android/app/src/main/
├── AndroidManifest.xml          # INTERNET 权限 + VpnService 声明
├── java/com/marsgeek/vpn/
│   ├── MainActivity.java        # UI：输入框 + 连接按钮 + VPN 授权
│   └── VpnConnectionService.java# 核心：握手/TUN/双向转发
└── res/                         # 图标 + 字符串
```

## 关键实现点

1. **VPN 授权**：`VpnService.prepare()`，首次会弹系统授权框，用户同意后才能建 TUN。
2. **防回环**：`protect(socket)` 让隧道自身的 TCP 不走 VPN，否则死循环。
3. **IP 来自服务端**：WELCOME 帧里的 IP 用于 `Builder.addAddress()`（错误001 的教训）。
4. **DNS**：`8.8.8.8` + `114.114.114.114`，避免 DNS 泄漏到本地。

## 构建

```bash
cd android && ./build.sh
# 输出 mars-vpn-v1.0.apk（已签名，可直接安装）
```

构建用手动 aapt2 流程（原因见 `docs/errors/error-002-gradle-daemon.md`）。

## 安装

- Android 5.0+，允许"未知来源"安装。
- 首次点"连接"会弹 VPN 授权，点确定。
