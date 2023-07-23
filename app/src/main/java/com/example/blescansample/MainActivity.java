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
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;

    private BluetoothGatt bluetoothGatt;  // <-- Add this line
    private ScanSettings scanSettings;
    private List<ScanFilter> scanFilters;
    private Context context;
    private boolean mScanning;
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int PERMISSION_REQUEST_CODE = 1;
    private Handler handler = new Handler();
    private static final long SCAN_PERIOD = 10000;

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
            menu.findItem(R.id.menu_stop).setVisible(false);
            menu.findItem(R.id.menu_scan).setVisible(true);
            menu.findItem(R.id.menu_refresh).setActionView(null);
        } else {
            menu.findItem(R.id.menu_stop).setVisible(true);
            menu.findItem(R.id.menu_scan).setVisible(false);
            menu.findItem(R.id.menu_refresh).setActionView(R.layout.actionbar_indeterminate_progress);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_scan:
                scanLeDevice(true);
                break;
            case R.id.menu_stop:
                scanLeDevice(false);
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
            // get value as byte array
            byte[] dataBytes = characteristic.getValue();

            // create ByteBuffer for conversion
            ByteBuffer bb = ByteBuffer.wrap(dataBytes);

            // set the ByteBuffer to use little endian
            bb.order(ByteOrder.LITTLE_ENDIAN);

            // get the float value from the ByteBuffer
            float data_sample = bb.getFloat();

            runOnUiThread(() -> {
                TextView textView = findViewById(R.id.text_view);
                textView.setText(String.valueOf(data_sample));
            });
        }

    };

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
