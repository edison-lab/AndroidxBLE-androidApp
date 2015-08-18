package jp.kaibutsu.ble_tempsensor;


import java.util.UUID;

import android.support.v7.app.AppCompatActivity;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    //scan timeout
    private static final long SCAN_PERIOD = 10000;

    //uuid
    private static final String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";

    private static final String DEVICE_NAME = "Edison";
    private static final String DEVICE_BRIL_SERVICE_UUID = "00002800-0000-1000-8000-00805f9b34fb";
    private static final String DEVICE_RDWT_CHARACTERISTIC_UUID = "00003333-0000-1000-8000-00805f9b34fb";
    private static final String DEVICE_NOTIFY_CHARACTERISTIC_UUID = "00003333-0000-1000-8000-00805f9b34fb";


    private static final String TAG = "BLE_Edison";

    private BleStat mStatus = BleStat.INIT;
    private Handler mHandler;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothManager mBluetoothManager;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothGatt mGatt;
    private BluetoothGattCharacteristic mDataCharacteristic = null;

    private TextView mTextStatus;
    private TextView mTextRead;
    private Button mBtnConn, mBtnDiscon;
    private Button mbtnWrite;
    private String mReadData = "";
    private Button mBtnDisable, mBtnEnable;


    public View.OnClickListener BtnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.btnConn:
                    Log.e(TAG, "button Connect");
                    connect();
                    break;
                case R.id.btnDisConn:
                    Log.e(TAG, "button Disconnect");
                    disconnect();
                    break;
                case R.id.btnWrite:
                    Log.e(TAG, "button Write");
                    WriteData();
                    break;

                case R.id.btnDisable:
                    Log.e(TAG, "button notify_disconnect");
                    DisNotificationGatt();
                    break;

                case R.id.btnEnable:
                    Log.e(TAG, "button notify_connect");
                    NotificationGatt();
                    break;

            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mBluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if ((mBluetoothAdapter == null) || !mBluetoothAdapter.isEnabled()) {
            Toast.makeText(getApplicationContext(), "Bluetoothが無効になっています", Toast.LENGTH_SHORT).show();
            finish();
        }

        mTextStatus = (TextView) findViewById(R.id.textView1);
        mTextRead = (TextView) findViewById(R.id.textView2);
        mBtnConn = (Button) findViewById(R.id.btnConn);
        mBtnDiscon = (Button) findViewById(R.id.btnDisConn);
        mbtnWrite = (Button) findViewById(R.id.btnWrite);

        mBtnDisable = (Button) findViewById(R.id.btnDisable);
        mBtnEnable = (Button) findViewById(R.id.btnEnable);


        mBtnConn.setOnClickListener(BtnClickListener);
        mBtnDiscon.setOnClickListener(BtnClickListener);
        mbtnWrite.setOnClickListener(BtnClickListener);

        mBtnDisable.setOnClickListener(BtnClickListener);
        mBtnEnable.setOnClickListener(BtnClickListener);


        mBtnConn.setEnabled(true);
        mBtnDiscon.setEnabled(false);
        mbtnWrite.setEnabled(false);

        mBtnDisable.setEnabled(false);
        mBtnEnable.setEnabled(false);

        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                mTextStatus.setText(((BleStat) msg.obj).name());

                if (msg.obj.toString().equals(BleStat.READY.toString())) {
                    mBtnConn.setEnabled(false);
                    mBtnDiscon.setEnabled(true);
                    mbtnWrite.setEnabled(true);

                    mBtnDisable.setEnabled(true);
                    mBtnEnable.setEnabled(true);
                }
                if (msg.obj.toString().equals(BleStat.BUSY.toString())) {
                    mbtnWrite.setEnabled(false);
                }
                if (msg.obj.toString().equals(BleStat.CLOSED.toString())) {
                    mBtnConn.setEnabled(true);
                    mBtnDiscon.setEnabled(false);
                    mbtnWrite.setEnabled(false);

                    mBtnDisable.setEnabled(false);
                    mBtnEnable.setEnabled(false);
                }
                if (msg.obj.toString().equals(BleStat.DISCONNECTED.toString())) {
                    mBtnConn.setEnabled(true);
                    mBtnDiscon.setEnabled(false);
                    mbtnWrite.setEnabled(false);

                    mBtnDisable.setEnabled(false);
                    mBtnEnable.setEnabled(false);

                }
                if (msg.obj.toString().equals(BleStat.DATA_UPDATE.toString())) {
                    mbtnWrite.setEnabled(true);

                    mTextRead.setText(mReadData + " ℃");
                }
            }
        };
    }

    private void setStatus(BleStat status) {
        mStatus = status;
        mHandler.sendMessage(status.message());
    }

    private enum BleStat {
        INIT,
        BLE_SCANNING,
        SCAN_FAILED,
        DEVICE_FOUND,
        SERVICE_NOT_FOUND,
        SERVICE_FOUND,
        CHARACTERISTIC_NOT_FOUND,
        NOTIFICATION_REGISTERED,
        NOTIFICATION_REGISTER_FAILED,
        DATA_UPDATE,
        READY,
        BUSY,
        DISCONNECTED,
        CLOSED;

        public Message message() {
            Message message = new Message();
            message.obj = this;
            return message;
        }
    }

    private void NotificationGatt() {
        boolean registered = mGatt.setCharacteristicNotification(mDataCharacteristic, true);
        BluetoothGattDescriptor descriptor = mDataCharacteristic.getDescriptor(
                UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG));
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        mGatt.writeDescriptor(descriptor);
        if (registered) {
            setStatus(BleStat.NOTIFICATION_REGISTERED);
            Log.e(TAG, "Notify_Characteristics通知設定: 完了");

        } else {
            setStatus(BleStat.NOTIFICATION_REGISTER_FAILED);
            Log.e(TAG, "Notify_Characteristics通知設定: エラー");
        }

    }

    private void DisNotificationGatt() {
        BluetoothGattDescriptor descriptor = mDataCharacteristic.getDescriptor(
                UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG));
        descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        mGatt.writeDescriptor(descriptor);

        boolean registered = mGatt.setCharacteristicNotification(mDataCharacteristic, false);
        if (registered) {
            setStatus(BleStat.NOTIFICATION_REGISTERED);
            Log.e(TAG, "Notify_Characteristics通知設定: 解除");

        } else {
            setStatus(BleStat.NOTIFICATION_REGISTER_FAILED);
            Log.e(TAG, "Notify_Characteristics通知設定: エラー");
        }

    }

    private void WriteData() {
        if (mDataCharacteristic != null) {

            mDataCharacteristic.setValue("1");

            boolean status = mGatt.writeCharacteristic(mDataCharacteristic);
            if (status == false) {
            Log.e(TAG, "writeCharacteristic failed");
        }
    }
    }

    private void connect() {
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {

                mBluetoothAdapter.stopLeScan(mLeScanCallback);
                if (BleStat.BLE_SCANNING.equals(mStatus)) {
                    setStatus(BleStat.SCAN_FAILED);
                }
            }
        }, SCAN_PERIOD);

        mBluetoothAdapter.stopLeScan(mLeScanCallback);
        mBluetoothAdapter.startLeScan(mLeScanCallback);
        setStatus(BleStat.BLE_SCANNING);
    }

    private void disconnect() {

        if (mBluetoothGatt != null) {
            mBluetoothGatt.close();
            mBluetoothGatt = null;
            mDataCharacteristic = null;
            setStatus(BleStat.CLOSED);
        }
    }

    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {

        //BLEデバイスを発見する
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            Log.d(TAG, "device found: " + device.getName() + " :" + device.getUuids());
            if (DEVICE_NAME.equals(device.getName())) {
                setStatus(BleStat.DEVICE_FOUND);

                mBluetoothAdapter.stopLeScan(this);

                mBluetoothGatt = device.connectGatt(getApplicationContext(), false, mBluetoothGattCallback);
            }
        }
    };

    private final BluetoothGattCallback mBluetoothGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.d(TAG, "onConnectionStateChange: " + status + " -> " + newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                setStatus(BleStat.DISCONNECTED);
                setTitle("BLE-edison - DISCONNECTED");
                mBluetoothGatt = null;
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.d(TAG, "onServicesDiscovered received: " + status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(UUID.fromString(DEVICE_BRIL_SERVICE_UUID));
                if (service == null) {
                    setStatus(BleStat.SERVICE_NOT_FOUND);
                    setTitle("BLE-edison - SERVICE_NOT_FOUND");

                } else {
                    setStatus(BleStat.SERVICE_FOUND);
                    setTitle("BLE-edison - SERVICE_FOUND");
                    mDataCharacteristic =
                            service.getCharacteristic(UUID.fromString(DEVICE_RDWT_CHARACTERISTIC_UUID));
                    if (mDataCharacteristic == null) {
                        setStatus(BleStat.CHARACTERISTIC_NOT_FOUND);
                        setTitle("BLE-edison - CHARACTERISTIC_NOT_FOUND");
                    } else {

                        mGatt = gatt;
                        setStatus(BleStat.READY);

                        Log.e(TAG, "check permission");
                        Log.e(TAG, "characteristic prop " + mDataCharacteristic.getProperties());
                        Log.e(TAG, "characteristic perm " + mDataCharacteristic.getPermissions());
                    }
                }
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic, int status) {
            Log.d(TAG, "onCharacteristicWrite: " + status);
            switch (status) {
                case BluetoothGatt.GATT_SUCCESS:
                    Log.e(TAG, "onCharacteristicWrite: GATT_SUCCESS");
                    break;
                case BluetoothGatt.GATT_WRITE_NOT_PERMITTED:
                    Log.e(TAG, "onCharacteristicWrite: GATT_WRITE_NOT_PERMITTED");
                    break;

                case BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED:
                    Log.e(TAG, "onCharacteristicWrite: GATT_REQUEST_NOT_SUPPORTED");
                    break;

                case BluetoothGatt.GATT_FAILURE:
                    Log.e(TAG, "onCharacteristicWrite: GATT_FAILURE");
                    break;

                case BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION:
                    break;

                case BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION:
                    break;

                case BluetoothGatt.GATT_INVALID_OFFSET:
                    break;
            }

            setStatus(BleStat.READY);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            Log.d(TAG, "onNotify");
            if (DEVICE_NOTIFY_CHARACTERISTIC_UUID.equals(characteristic.getUuid().toString())) {
                byte[] read_data = characteristic.getValue();
                String notifyData = new String(read_data);
                Log.e(TAG, notifyData);
                mReadData = notifyData;
                setStatus(BleStat.DATA_UPDATE);
            }
        }
    };

}
