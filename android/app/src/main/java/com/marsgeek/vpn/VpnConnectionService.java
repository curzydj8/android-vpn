package com.marsgeek.vpn;

import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;

/**
 * VPN 连接服务：VpnService + 自定义 TCP 隧道协议
 * 协议见 docs/04-protocol.md
 */
public class VpnConnectionService extends VpnService {
    private static final String TAG = "MarsVpn";
    private static final int TYPE_HELLO = 0x01;
    private static final int TYPE_WELCOME = 0x02;
    private static final int TYPE_DATA = 0x03;
    private static final int TYPE_PING = 0x04;

    public static final String EXTRA_SERVER = "server";
    public static final String EXTRA_PORT = "port";
    public static final String EXTRA_PASSWORD = "password";

    private volatile boolean running = false;
    private Thread worker;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String server = intent.getStringExtra(EXTRA_SERVER);
        int port = intent.getIntExtra(EXTRA_PORT, 51820);
        String password = intent.getStringExtra(EXTRA_PASSWORD);
        startVpn(server, port, password);
        return START_STICKY;
    }

    private void startVpn(String server, int port, String password) {
        if (running) return;
        running = true;
        worker = new Thread(() -> runVpn(server, port, password));
        worker.start();
    }

    private void runVpn(String server, int port, String password) {
        Socket sock = new Socket();
        ParcelFileDescriptor tun = null;
        try {
            // 1. TCP 连接（绕过 VPN 自身，避免回环）
            sock.connect(new InetSocketAddress(server, port), 10000);
            protect(sock);
            Log.i(TAG, "TCP 已连接 " + server + ":" + port);

            DataInputStream in = new DataInputStream(sock.getInputStream());
            DataOutputStream out = new DataOutputStream(sock.getOutputStream());

            // 2. 握手
            sendFrame(out, TYPE_HELLO, password.getBytes("UTF-8"));
            Frame welcome = recvFrame(in);
            if (welcome.type != TYPE_WELCOME) throw new IOException("握手失败");
            String vpnIp = new String(welcome.payload, "UTF-8");
            Log.i(TAG, "分配到 VPN IP: " + vpnIp);

            // 3. 建 TUN（错误001 的教训：必须用服务端分配的 IP）
            Builder b = new Builder();
            b.setSession("MarsVPN");
            b.addAddress(vpnIp, 24);
            b.addRoute("0.0.0.0", 0);
            b.addDnsServer("8.8.8.8");
            b.addDnsServer("114.114.114.114");
            tun = b.establish();
            if (tun == null) throw new IOException("TUN 建立失败");
            Log.i(TAG, "TUN 已建立");

            FileInputStream tunIn = new FileInputStream(tun.getFileDescriptor());
            FileOutputStream tunOut = new FileOutputStream(tun.getFileDescriptor());
            final DataOutputStream fout = out;

            // 4a. TUN -> TCP（手机流量上行）
            Thread up = new Thread(() -> {
                byte[] buf = new byte[2048];
                try {
                    while (running) {
                        int n = tunIn.read(buf);
                        if (n <= 0) break;
                        byte[] pkt = new byte[n];
                        System.arraycopy(buf, 0, pkt, 0, n);
                        synchronized (fout) { sendFrame(fout, TYPE_DATA, pkt); }
                    }
                } catch (IOException e) { Log.w(TAG, "上行结束: " + e); }
            });

            // 4b. TCP -> TUN（服务端回包下行）+ 保活
            long lastPing = 0;
            up.start();
            try {
                while (running) {
                    if (System.currentTimeMillis() - lastPing > 30000) {
                        synchronized (fout) { sendFrame(fout, TYPE_PING, new byte[0]); }
                        lastPing = System.currentTimeMillis();
                    }
                    sock.setSoTimeout(5000);
                    Frame f;
                    try { f = recvFrame(in); }
                    catch (java.net.SocketTimeoutException e) { continue; }
                    if (f.type == TYPE_DATA) {
                        tunOut.write(f.payload);
                    }
                }
            } finally {
                running = false;
                up.join(2000);
            }
        } catch (Exception e) {
            Log.e(TAG, "VPN 错误", e);
        } finally {
            running = false;
            try { if (tun != null) tun.close(); } catch (IOException ignored) {}
            try { sock.close(); } catch (IOException ignored) {}
            Log.i(TAG, "VPN 已断开");
            stopSelf();
        }
    }

    private static class Frame {
        int type; byte[] payload;
    }

    private void sendFrame(DataOutputStream out, int type, byte[] payload) throws IOException {
        out.writeShort(payload.length);
        out.writeByte(type);
        out.write(payload);
        out.flush();
    }

    private Frame recvFrame(DataInputStream in) throws IOException {
        int len = in.readUnsignedShort();
        int type = in.readUnsignedByte();
        byte[] payload = new byte[len];
        in.readFully(payload);
        Frame f = new Frame();
        f.type = type; f.payload = payload;
        return f;
    }

    @Override
    public void onDestroy() {
        running = false;
        if (worker != null) worker.interrupt();
        super.onDestroy();
    }
}
