#!/usr/bin/env python3
"""
简易 VPN 服务端
- TCP 监听，自定义协议（见 docs/04-protocol.md）
- TUN 设备收发 IP 包
- iptables NAT 转发到公网

运行：sudo python3 vpn_server.py [--port 51820] [--password PASSWORD]
依赖：无（纯标准库）
"""
import argparse
import fcntl
import os
import socket
import struct
import threading

# ---- 协议常量 ----
TYPE_HELLO   = 0x01
TYPE_WELCOME = 0x02
TYPE_DATA    = 0x03
TYPE_PING    = 0x04
TYPE_PONG    = 0x05

TUN_IP = "10.8.0.1"
TUN_NET = "10.8.0.0/24"
CLIENT_IP_BASE = "10.8.0."

# Linux TUN ioctl
TUNSETIFF = 0x400454ca
IFF_TUN = 0x0001
IFF_NO_PI = 0x1000


def create_tun(name="tun0"):
    """创建 TUN 设备，返回 fd。需要 root。"""
    tun = os.open("/dev/net/tun", os.O_RDWR)
    ifr = struct.pack("16sH", name.encode(), IFF_TUN | IFF_NO_PI)
    fcntl.ioctl(tun, TUNSETIFF, ifr)
    return tun


def send_frame(sock, ftype, payload: bytes):
    header = struct.pack(">HB", len(payload), ftype)
    sock.sendall(header + payload)


def recv_exact(sock, n):
    buf = b""
    while len(buf) < n:
        chunk = sock.recv(n - len(buf))
        if not chunk:
            raise ConnectionError("连接断开")
        buf += chunk
    return buf


def recv_frame(sock):
    header = recv_exact(sock, 3)
    length, ftype = struct.unpack(">HB", header)
    payload = recv_exact(sock, length) if length else b""
    return ftype, payload


class VPNServer:
    def __init__(self, port, password):
        self.port = port
        self.password = password
        self.tun = None
        self.clients = {}          # vpn_ip -> socket
        self.next_id = 2           # 10.8.0.2 开始
        self.lock = threading.Lock()

    def setup_tun(self):
        self.tun = create_tun("tun0")
        # 配置 IP（调 ip 命令，比 ioctl 写起来简单）
        os.system(f"ip addr add {TUN_IP}/24 dev tun0")
        os.system("ip link set tun0 up")
        print(f"[+] TUN tun0 已启动: {TUN_IP}/24")

    def handle_client(self, conn, addr):
        print(f"[+] 新连接: {addr}")
        vpn_ip = None
        try:
            # 1. 等 HELLO
            ftype, payload = recv_frame(conn)
            if ftype != TYPE_HELLO or payload.decode(errors="ignore") != self.password:
                print(f"[-] 认证失败: {addr}")
                conn.close()
                return
            # 2. 分配 IP，回 WELCOME
            with self.lock:
                vpn_ip = f"{CLIENT_IP_BASE}{self.next_id}"
                self.next_id += 1
                self.clients[vpn_ip] = conn
            send_frame(conn, TYPE_WELCOME, vpn_ip.encode())
            print(f"[+] {addr} 认证通过，分配 {vpn_ip}")

            # 3. 转发：TCP -> TUN
            while True:
                ftype, payload = recv_frame(conn)
                if ftype == TYPE_DATA:
                    os.write(self.tun, payload)
                elif ftype == TYPE_PING:
                    send_frame(conn, TYPE_PONG, b"")
        except Exception as e:
            print(f"[-] {vpn_ip or addr} 断开: {e}")
        finally:
            with self.lock:
                if vpn_ip and vpn_ip in self.clients:
                    del self.clients[vpn_ip]
            conn.close()

    def tun_reader(self):
        """TUN -> TCP：按目标 IP 找到客户端，发下去。"""
        while True:
            try:
                packet = os.read(self.tun, 2048)
                if len(packet) < 20:
                    continue
                # IP 头第 16-19 字节是目标地址
                dst = socket.inet_ntoa(packet[16:20])
                with self.lock:
                    conn = self.clients.get(dst)
                if conn:
                    try:
                        send_frame(conn, TYPE_DATA, packet)
                    except Exception:
                        pass
            except Exception as e:
                print(f"[-] TUN 读取错误: {e}")
                break

    def run(self):
        self.setup_tun()
        threading.Thread(target=self.tun_reader, daemon=True).start()
        srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        srv.bind(("0.0.0.0", self.port))
        srv.listen(32)
        print(f"[+] VPN 服务端监听 0.0.0.0:{self.port}")
        while True:
            conn, addr = srv.accept()
            threading.Thread(target=self.handle_client, args=(conn, addr), daemon=True).start()


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, default=51820)
    ap.add_argument("--password", default="changeme")
    args = ap.parse_args()
    if os.geteuid() != 0:
        print("需要 root 运行（创建 TUN 设备）")
        raise SystemExit(1)
    VPNServer(args.port, args.password).run()
