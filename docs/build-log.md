# 构建日志

按时间顺序记录本项目的每一步。

## 2026-10-03

### 17:43 - 立项
Zhang 要求：在 GitHub 建新项目，做安卓 VPN 应用，服务端和应用端匹配，全程记录步骤和错误日志。
决策：自研轻量隧道（Python 服务端 + VpnService 客户端），不用 WireGuard/OpenVPN，目的是把每一层摊开讲清楚。

### 17:44 - 创建仓库
- GitHub 新建公开仓库 `curzydj8/android-vpn`，写 README（架构图）。
- `gh_push.py` 一次推送成功。

### 17:45 - 架构与协议文档
- 写 `docs/01-architecture.md`：数据流向、模块划分、安全说明。
- 写 `docs/04-protocol.md`：定长帧（LEN+TYPE+PAYLOAD），HELLO/WELCOME/DATA/PING 五种帧，IP 分配规则。

### 17:46 - 服务端开发
- 写 `server/vpn_server.py`（纯标准库，无依赖）：TUN 创建、TCP 监听、握手认证、双向转发。
- 写 `server/setup.sh`：一键开 IP 转发、iptables NAT。
- `py_compile` 语法通过。

### 17:47 - 服务端实测
- 沙盒有 `/dev/net/tun` 且为 root，TUN 创建成功。
- 启动服务端，`tun0` 拿到 `10.8.0.1/24`。
- Python 模拟客户端：正确口令握手成功（分配 `10.8.0.2`）；错误口令被断开。✓
- ICMP 隧道往返：第一次因硬编码源 IP 失败（记为错误001），修复后成功。✓✓✓

### 17:52 - 安卓客户端开发
- 写 `VpnConnectionService.java`：握手、建 TUN、双线程转发、PING 保活。
- 写 `MainActivity.java`：服务器/端口/口令输入 + 连接按钮 + VPN 授权。
- 写 `AndroidManifest.xml`：INTERNET 权限 + BIND_VPN_SERVICE。
- 生成盾牌图标（4 种密度）。

### 17:55 - APK 构建
- Gradle daemon 起不来（记为错误002），改手动 aapt2 流程，写 `android/build.sh` 固化。
- `mars-vpn-v1.0.apk` 构建成功（17KB），`apksigner verify` 通过。

### 待续
- 真机联调（需 VPS）
- 文档推送 GitHub
