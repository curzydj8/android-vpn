# 02 - 服务端部署

日期：2026-10-03

## 环境要求

- Linux VPS（Ubuntu/Debian），有公网 IP
- root 权限（创建 TUN 设备、配 iptables）
- Python 3（无第三方依赖，纯标准库）

## 部署步骤

```bash
# 1. 上传 server/ 到 VPS
scp -r server/ root@你的VPS:/opt/mars-vpn/

# 2. 一键配置（开 IP 转发、iptables NAT、放行端口）
sudo bash /opt/mars-vpn/setup.sh

# 3. 启动服务端（口令自己定）
sudo python3 /opt/mars-vpn/vpn_server.py --password 你的口令 --port 51820
```

## 后台运行（systemd）

```ini
# /etc/systemd/system/mars-vpn.service
[Unit]
Description=Mars VPN Server
After=network.target

[Service]
ExecStart=/usr/bin/python3 /opt/mars-vpn/vpn_server.py --password 你的口令
Restart=always

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl enable --now mars-vpn
```

## 验证

服务端日志应显示：

```
[+] TUN tun0 已启动: 10.8.0.1/24
[+] VPN 服务端监听 0.0.0.0:51820
```

客户端连接后显示：`[+] ('x.x.x.x', port) 认证通过，分配 10.8.0.2`

## 已验证（2026-10-03）

在沙盒实测：握手、口令校验、IP 分配、ICMP 隧道往返全部通过。详见 `05-testing.md`。
