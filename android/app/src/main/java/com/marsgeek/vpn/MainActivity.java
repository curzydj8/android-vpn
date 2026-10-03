package com.marsgeek.vpn;

import android.app.Activity;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;
import android.graphics.Color;

public class MainActivity extends Activity {
    private static final int REQ_VPN = 1001;
    private EditText etServer, etPort, etPassword;
    private Button btnToggle;
    private TextView tvStatus;
    private boolean connected = false;

    // 待连接参数（等用户授权 VPN 后用）
    private String pendingServer; private int pendingPort; private String pendingPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 48, 48, 48);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("🛡️ Mars VPN");
        title.setTextSize(28);
        title.setTextColor(Color.parseColor("#4fc3f7"));
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        etServer = addField(layout, "服务器地址", "192.168.1.100");
        etPort = addField(layout, "端口", "51820");
        etPassword = addField(layout, "口令", "");
        etPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);

        tvStatus = new TextView(this);
        tvStatus.setText("未连接");
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setPadding(0, 24, 0, 24);
        layout.addView(tvStatus);

        btnToggle = new Button(this);
        btnToggle.setText("连接");
        btnToggle.setOnClickListener(v -> toggle());
        layout.addView(btnToggle);

        setContentView(layout);
    }

    private EditText addField(LinearLayout layout, String hint, String def) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setText(def);
        layout.addView(et);
        return et;
    }

    private void toggle() {
        if (connected) {
            stopService(new Intent(this, VpnConnectionService.class));
            connected = false;
            btnToggle.setText("连接");
            tvStatus.setText("未连接");
            return;
        }
        pendingServer = etServer.getText().toString().trim();
        try { pendingPort = Integer.parseInt(etPort.getText().toString().trim()); }
        catch (NumberFormatException e) { pendingPort = 51820; }
        pendingPassword = etPassword.getText().toString();

        if (pendingServer.isEmpty() || pendingPassword.isEmpty()) {
            Toast.makeText(this, "请填写服务器地址和口令", Toast.LENGTH_SHORT).show();
            return;
        }
        // 系统 VPN 授权（首次会弹窗）
        Intent intent = VpnService.prepare(this);
        if (intent != null) {
            startActivityForResult(intent, REQ_VPN);
        } else {
            onActivityResult(REQ_VPN, RESULT_OK, null);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_VPN && resultCode == RESULT_OK) {
            Intent svc = new Intent(this, VpnConnectionService.class);
            svc.putExtra(VpnConnectionService.EXTRA_SERVER, pendingServer);
            svc.putExtra(VpnConnectionService.EXTRA_PORT, pendingPort);
            svc.putExtra(VpnConnectionService.EXTRA_PASSWORD, pendingPassword);
            startService(svc);
            connected = true;
            btnToggle.setText("断开");
            tvStatus.setText("已连接: " + pendingServer);
        } else if (requestCode == REQ_VPN) {
            Toast.makeText(this, "需要 VPN 授权才能连接", Toast.LENGTH_SHORT).show();
        }
    }
}
