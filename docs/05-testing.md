# 05 - 测试记录

日期：2026-10-03
环境：Linux 沙盒（root，有 /dev/net/tun）

## 服务端启动

```bash
sudo python3 vpn_server.py --password test123 --port 51820
```

结果：`tun0` 创建成功，`ip addr` 显示 `10.8.0.1/24`。✓

## 测试 1：握手（正确口令）

Python 模拟客户端：TCP 连接 → 发送 HELLO(`test123`) → 收到 WELCOME。

结果：`TYPE=0x2 PAYLOAD=10.8.0.2`，分配成功。✓

## 测试 2：握手（错误口令）

发送 HELLO(`wrongpass`)。

结果：服务端直接断开 TCP，无 WELCOME。✓（符合协议设计）

## 测试 3：隧道数据往返（ICMP）

构造 ICMP Echo Request（`分配IP → 10.8.0.1`），经 DATA 帧发送，等待回包。

- 第一次失败：测试代码硬编码源 IP `10.8.0.2`，实际分配到 `10.8.0.3`，回包路由查不到 → 超时。见 `errors/error-001-test-ip-mismatch.md`。
- 修复后（用实际分配 IP）：收到 ICMP Echo Reply (type=0)，`10.8.0.1 → 10.8.0.4`。✓✓✓

**结论：隧道数据通路完整打通。**

## 测试 4：安卓 APK

- `mars-vpn-v1.0.apk` 构建成功，`apksigner verify` 通过。
- `aapt2 dump badging`：包名 `com.marsgeek.vpn`，`MainActivity` 可启动。
- 真机联调待 Zhang 实测（需 VPS 服务端）。

## 待测

- [ ] 安卓真机连接 VPS 服务端，浏览器访问验证 IP 变化
- [ ] 长连接稳定性（1小时）
- [ ] 多客户端同时在线
