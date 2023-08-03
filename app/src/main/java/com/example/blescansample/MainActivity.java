package com.example.blescansample;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.bluetooth.le.ScanFilter;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;
import android.content.pm.PackageManager;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity {
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;

    private BluetoothGatt bluetoothGatt;  // <-- Add this line
    private ScanSettings scanSettings;
    private List<ScanFilter> scanFilters;
    private Context context;
    private boolean mScanning;
    private boolean isRecording;
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int PERMISSION_REQUEST_CODE = 1;
    private Handler handler = new Handler();
    private static final long SCAN_PERIOD = 10000;

    private ConcurrentHashMap<String, Float> sensorDataMap = new ConcurrentHashMap<>();
    private Timer timer;

    private static final UUID SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b");
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8");



    // Request code for location permission
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.context = this;
        setContentView(R.layout.activity_main);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        scanSettings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build();
        scanFilters = new ArrayList<>();

        isRecording = false;
        timer = new Timer();

        // Add a filter
        UUID[] serviceUuids = {SERVICE_UUID};
        ScanFilter scanFilter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(serviceUuids[0]))
                .build();

        scanFilters.add(scanFilter);

        // Request necessary permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);

        if (!mScanning) {
            menu.findItem(R.id.menu_stop_ble).setVisible(false);
            menu.findItem(R.id.menu_scan_ble).setVisible(true);
            menu.findItem(R.id.menu_refresh).setActionView(null);
        } else {
            menu.findItem(R.id.menu_stop_ble).setVisible(true);
            menu.findItem(R.id.menu_scan_ble).setVisible(false);
            menu.findItem(R.id.menu_refresh).setActionView(R.layout.actionbar_indeterminate_progress);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_scan_ble:
                scanLeDevice(true);
                break;
            case R.id.menu_stop_ble:
                scanLeDevice(false);
                break;
            case R.id.action_csv:
                if (!isRecording) {
                    // レコーディングを開始
                    startRecording();
                } else {
                    // レコーディングを終了
                    stopRecording();
                }
                break;
        }
        return true;
    }

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            handler.postDelayed(() -> {
                mScanning = false;
                bluetoothLeScanner.stopScan(mLeScanCallback);
                invalidateOptionsMenu();
            }, SCAN_PERIOD);

            mScanning = true;
            Log.d("BleScan", "Starting BLE scan...");  // ログ出力
            bluetoothLeScanner.startScan(scanFilters, scanSettings, mLeScanCallback);
            invalidateOptionsMenu();
        } else {
            mScanning = false;
            handler.removeCallbacksAndMessages(null);
            bluetoothLeScanner.stopScan(mLeScanCallback);
            invalidateOptionsMenu();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PERMISSION_REQUEST_CODE: {
                // grantResults配列は空ではなく、
                // 各パーミッションは要求の順番に対応しています
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // ACCESS_FINE_LOCATION, BLUETOOTH_SCAN, BLUETOOTH_CONNECT が付与されました
                    Log.d("BleScan", "Starting BLE scan after permission granted...");
                    scanLeDevice(true);
                } else {
                    // パーミッションが拒否されました。適切に対応します（ユーザーに説明を表示したり、機能を無効化したりするなど）
                    Log.d("BleScan", "Permission denied...");
                }
                return;
            }

            // 他の'case'行を使って他のパーミッション要求をチェックすることができます。
            // アプリの操作に応じて
        }
    }






    private ScanCallback mLeScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            // Log to see if we are getting any results
            if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                Log.i("BLE", "Device found: " + result.getDevice().getName());
            }

            // 特定のデバイスを見つけた場合のみ進めるようにする。
            // Check if we have permissions to access Bluetooth device information
            if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                // Now we are sure that we have permissions, we can access the device name
                String deviceName = result.getDevice().getName();
                if (deviceName == null || !deviceName.equals("ESP32-BLE")) {
                    return;
                }
                // ... rest of the code
            } else {
                // Handle the case where we don't have permissions
                // This could be showing a message to the user, or requesting the needed permissions
                // ...
            }


            if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(MainActivity.this, "Target device found: " + result.getDevice().getName(), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(MainActivity.this, "Missing permissions to retrieve device name", Toast.LENGTH_SHORT).show();
            }

            // 既存の接続を閉じます。
            if (bluetoothGatt != null) {
                bluetoothGatt.close();
                bluetoothGatt = null;
            }

            // スキャンを停止します。
            mScanning = false;
            bluetoothLeScanner.stopScan(this);

            // デバイスとの接続を開始します。
            bluetoothGatt = result.getDevice().connectGatt(MainActivity.this, false, mGattCallback);
        }

        @Override
        public void onScanFailed(int errorCode) {
            // Log to see if there are any errors
            Log.e("BLE", "Scan failed with error: " + errorCode);
        }
    };


    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Connected to GATT server.");
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH)
                        == PackageManager.PERMISSION_GRANTED) {
                    //gatt.requestMtu(40);
                    gatt.discoverServices(); // パーミッションが許可されている場合のみサービスの検出を試みます
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from GATT server.");
                gatt.close();
            }
        }

        @Override
        // サービスの検出結果に対する処理を記述します。
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(SERVICE_UUID);
                if (service == null) {
                    Log.w(TAG, "Service not found!");
                    return;
                }
                BluetoothGattCharacteristic characteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
                if (characteristic == null) {
                    Log.w(TAG, "Characteristic not found!");
                    return;
                }


                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH)
                        == PackageManager.PERMISSION_GRANTED) {
                    gatt.setCharacteristicNotification(characteristic, true);  // パーミッションが許可されている場合のみ通知を有効にします。
                    BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    gatt.writeDescriptor(descriptor);
                }
            }
        }


        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);

            byte[] data = characteristic.getValue();
            Log.d("BLE", "Received data length: " + data.length);

            float[] dataAsFloats = MainActivity.this.convertBytesToFloats(data);

            if(dataAsFloats != null && dataAsFloats.length > 0) {
                int seq = (int)dataAsFloats[0];  // First float is sequence number
                float[] actualData = Arrays.copyOfRange(dataAsFloats, 1, dataAsFloats.length);  // Ignore first float
                Log.d("BLE", "Actual data length: " + actualData.length);

                MainActivity.this.updateTextViews(seq, actualData);  // Call the method of the outer class
            } else {
                Log.d("BLE", "Failed to convert data");
            }
        }
    };

    // バイトを解析するためのメソッド
    private float[] convertBytesToFloats(byte[] data) {
        if (data == null || data.length % 4 != 0) return null;

        float[] floats = new float[data.length / 4];
        for (int i = 0; i < floats.length; i++) {
            int value = 0;
            for (int j = 0; j < 4; j++) {
                value |= (data[i * 4 + j] & 0xFF) << (j * 8);
            }
            floats[i] = Float.intBitsToFloat(value);
        }
        return floats;
    }

    // データを各TextViewに反映するメソッド
    private void updateTextViews(int sequenceNumber, float[] data) {
        // UIスレッド外でsensorDataMapを更新
        for (int i = 0; i < data.length; i++) {
            sensorDataMap.put(String.valueOf(sequenceNumber * 4 + i + 1), data[i]);
        }
        runOnUiThread(() -> {
            for (int i = 0; i < data.length; i++) {
                TextView textView = findViewById(getResources().getIdentifier("text_view" + (sequenceNumber * 4 + i + 1), "id", getPackageName()));
                if (textView != null && i < data.length) {
                    textView.setText(String.valueOf(data[i]));
                }
            }
        });
    }

    private void startRecording() {
        Toast.makeText(MainActivity.this, "Recording start", Toast.LENGTH_SHORT).show();
        // レコーディング状態をtrueに
        isRecording = true;

        // ファイル名を現在の日時から生成
        String filename = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(new Date()) + ".csv";

        // 保存するファイルのパスを指定
        File outputFile = new File(getExternalFilesDir(null), filename);

        // タイマータスクを作成
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                try (FileWriter writer = new FileWriter(outputFile, true)) {
                    // データ取得時間をHHmmssSSS形式にフォーマット
                    String formattedTime = new SimpleDateFormat("HHmmssSSS", Locale.getDefault()).format(new Date());
                    // データを文字列に変換
                    String dataString = sensorDataMap.entrySet().stream()
                            .map(entry -> entry.getKey() + ":" + entry.getValue())
                            .collect(Collectors.joining(","));
                    // ファイルに書き込み
                    writer.write(formattedTime + "," + dataString + "\n");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };


        // タイマーを開始（0.5秒ごとにタスクを実行）
        timer.scheduleAtFixedRate(timerTask, 0, 500);
    }

    private void stopRecording() {
        Toast.makeText(MainActivity.this, "Recording stop", Toast.LENGTH_SHORT).show();
        // レコーディング状態をfalseに
        isRecording = false;

        // タイマーを停止
        timer.cancel();
        timer.purge();

        timer = new Timer();  // 新たなTimerオブジェクトを作成
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothGatt != null) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH)
                    == PackageManager.PERMISSION_GRANTED) {
                bluetoothGatt.close();
            }
            bluetoothGatt = null;
        }
    }

}
