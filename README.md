# Android VPN

安卓 VPN 应用：VpnService 客户端 + Python 隧道服务端。

本项目记录从零开始制作一个安卓 VPN 应用的**完整过程**：架构设计、服务端开发、安卓端开发、联调测试，以及所有踩过的坑（见 `docs/errors/`）。

## 架构

```
┌─────────────┐         TCP隧道          ┌─────────────┐         ┌──────────┐
│ Android App │ ◄──────────────────────► │ VPN Server  │ ──────► │ Internet │
│ (VpnService)│   自定义协议+口令认证      │ (Python+TUN) │  NAT转发 │          │
└─────────────┘                          └─────────────┘         └──────────┘
```

- **客户端**：Android `VpnService`，创建 TUN 虚拟网卡，拦截手机全部流量，经 TCP 隧道发往服务端。
- **服务端**：Python，TUN 设备收包，NAT 转发到公网，回包再经隧道发回客户端。
- **协议**：`docs/04-protocol.md`（握手 + 定长帧 + 口令认证）。

## 目录

| 目录 | 说明 |
|------|------|
| `docs/` | 全程文档：架构/服务端/客户端/协议/测试/构建日志 |
| `docs/errors/` | 错误日志：每个错误的现象、根因、修复 |
| `server/` | VPN 服务端（Python） |
| `android/` | 安卓客户端源码 |

## 快速开始

服务端（需 Linux + root）：见 `docs/02-server.md`
客户端（Android 5.0+）：见 `docs/03-android-client.md`

## 免责

本项目为学习演示用途。生产环境请使用 WireGuard/OpenVPN 等成熟方案。
