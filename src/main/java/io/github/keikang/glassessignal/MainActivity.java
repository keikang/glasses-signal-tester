package io.github.keikang.glassessignal;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 스마트 안경 없이 안경 탐지를 시험하기 위한 앱.
 * 이름 없이 블루투스 회사 번호만 담은 BLE 알림 신호를 내보낸다(제조사 데이터 = 회사 번호 + 0x01, 연결 불가).
 * 이름을 지운 Ray-Ban Meta(회사 번호 0x0D53 Luxottica)처럼 보이는 신호를 만든다. 진짜 안경의 동작을 대신하지는 않는다.
 */
public class MainActivity extends Activity {
    private static final String TAG = "GlassesSignal";
    private static final int[] IDS = {0x0D53, 0x01AB, 0x058E, 0x03C2, 0x0D54};
    private static final String[] LABELS = {
        "0x0D53 · Luxottica (Ray-Ban Meta·Oakley Meta 안경)",
        "0x01AB · Meta Platforms, Inc.",
        "0x058E · Meta Platforms Technologies, LLC",
        "0x03C2 · Snapchat Inc (Spectacles 안경)",
        "0x0D54 · 비교용: 안경과 관계없는 회사(ISEKI FRANCE)",
    };
    private static final int REQUEST_ADVERTISE = 1;

    private int selected = 0;
    private boolean pendingStart = false;
    private BluetoothLeAdvertiser advertiser;
    private AdvertiseCallback callback;
    private TextView status;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad * 2, pad, pad);

        TextView title = new TextView(this);
        title.setText("안경 신호 시험기");
        title.setTextSize(24);
        root.addView(title);

        TextView about = new TextView(this);
        about.setText("이름 없이 블루투스 회사 번호만 담은 신호를 내보냅니다. 이름을 지운 스마트 안경처럼 보이는 신호입니다. 진짜 안경이 아니므로, 진짜 안경이 언제 신호를 내는지는 이 앱으로 알 수 없습니다.\n\n보낼 회사 번호를 고르고 '신호 시작'을 누르세요. 화면은 켜진 채로 둡니다.");
        about.setTextSize(15);
        about.setPadding(0, pad / 2, 0, pad / 2);
        root.addView(about);

        RadioGroup group = new RadioGroup(this);
        for (int i = 0; i < IDS.length; i++) {
            RadioButton option = new RadioButton(this);
            option.setId(i + 1);
            option.setText(LABELS[i]);
            option.setTextSize(16);
            group.addView(option);
        }
        int extra = getIntent().getIntExtra("company", -1);
        for (int i = 0; i < IDS.length; i++) if (IDS[i] == extra) selected = i;
        group.check(selected + 1);
        group.setOnCheckedChangeListener((g, id) -> { selected = id - 1; if (callback != null) start(); });
        root.addView(group);

        Button startButton = new Button(this);
        startButton.setText("신호 시작");
        startButton.setOnClickListener(v -> start());
        root.addView(startButton);

        Button stopButton = new Button(this);
        stopButton.setText("신호 멈춤");
        stopButton.setOnClickListener(v -> stop());
        root.addView(stopButton);

        status = new TextView(this);
        status.setTextSize(18);
        status.setPadding(0, pad, 0, 0);
        status.setText("멈춤");
        root.addView(status);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);

        if (extra != -1) start();
    }

    private void start() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
            pendingStart = true;
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_ADVERTISE}, REQUEST_ADVERTISE);
            return;
        }
        BluetoothAdapter adapter = ((BluetoothManager) getSystemService(BLUETOOTH_SERVICE)).getAdapter();
        if (adapter == null || !adapter.isEnabled()) { show("블루투스를 켠 뒤 다시 누르세요."); return; }
        advertiser = adapter.getBluetoothLeAdvertiser();
        if (advertiser == null) { show("이 휴대폰은 BLE 알림 신호를 보낼 수 없습니다."); return; }
        stop();
        final int company = IDS[selected];
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false).build();
        AdvertiseData data = new AdvertiseData.Builder().setIncludeDeviceName(false).addManufacturerData(company, new byte[]{0x01}).build();
        callback = new AdvertiseCallback() {
            @Override public void onStartSuccess(AdvertiseSettings s) { show("신호 보내는 중 · " + hex(company) + " · 이름 없음"); Log.i(TAG, "ADVERTISING " + company); }
            @Override public void onStartFailure(int code) { callback = null; show("시작하지 못했습니다 (오류 " + code + ")"); Log.i(TAG, "FAILED " + code); }
        };
        advertiser.startAdvertising(settings, data, callback);
    }

    private void stop() {
        if (advertiser != null && callback != null) {
            try { advertiser.stopAdvertising(callback); } catch (SecurityException ignored) { }
            Log.i(TAG, "STOPPED");
        }
        callback = null;
        show("멈춤");
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (requestCode != REQUEST_ADVERTISE) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) { if (pendingStart) start(); }
        else show("근처 기기 권한이 있어야 신호를 보낼 수 있습니다.");
        pendingStart = false;
    }

    @Override protected void onDestroy() { stop(); super.onDestroy(); }

    private void show(String text) { if (status != null) status.setText(text); }

    private static String hex(int id) { return String.format("0x%04X", id); }
}
