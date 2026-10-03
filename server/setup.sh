#!/bin/bash
# VPN 服务端一键配置（Ubuntu/Debian，root 运行）
# 做：开 IP 转发、配 iptables NAT
set -e

echo "[1/3] 开启 IP 转发..."
sysctl -w net.ipv4.ip_forward=1
grep -q "net.ipv4.ip_forward=1" /etc/sysctl.conf || echo "net.ipv4.ip_forward=1" >> /etc/sysctl.conf

echo "[2/3] 配置 iptables NAT..."
# 假设公网网卡是 eth0，如不是请改
WAN_IF=$(ip route | grep default | awk '{print $5}' | head -1)
echo "公网网卡: $WAN_IF"
iptables -t nat -C POSTROUTING -s 10.8.0.0/24 -o "$WAN_IF" -j MASQUERADE 2>/dev/null \
  || iptables -t nat -A POSTROUTING -s 10.8.0.0/24 -o "$WAN_IF" -j MASQUERADE
iptables -C FORWARD -i tun0 -j ACCEPT 2>/dev/null \
  || iptables -A FORWARD -i tun0 -j ACCEPT
iptables -C FORWARD -o tun0 -j ACCEPT 2>/dev/null \
  || iptables -A FORWARD -o tun0 -j ACCEPT

echo "[3/3] 放行 VPN 端口..."
iptables -C INPUT -p tcp --dport 51820 -j ACCEPT 2>/dev/null \
  || iptables -A INPUT -p tcp --dport 51820 -j ACCEPT

echo "完成。启动服务端："
echo "  sudo python3 vpn_server.py --password <你的口令>"
