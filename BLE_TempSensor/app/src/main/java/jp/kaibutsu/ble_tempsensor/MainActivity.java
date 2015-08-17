package jp.kaibutsu.ble_tempsensor;


import java.util.UUID;

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
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    //scan timeout
    private static final long SCAN_PERIOD = 10000;
    //devicename
    private static final String DEVICE_NAME = "Edison";
    //service_UUID
    private static final String DEVICE_SERVICE_UUID = "00002800-0000-1000-8000-00805f9b34fb";
    //characteristic_UUID
    private static final String DEVICE_CHARACTERISTIC_UUID = "00003333-0000-1000-8000-00805f9b34fb";

    private static final String TAG = "BLE_temp";

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
    private Button mBtnUpdate;
    private String mReadData = "";

    public View.OnClickListener BtnClickListener = new View.OnClickListener() {

        //ボタンIDで区別する

        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.btnConn:
                    Log.e(TAG, "Connect pressed!");
                    connect();
                    break;
                case R.id.btnDisConn:
                    Log.e(TAG, "Disconnect pressed");
                    disconnect();
                    break;
                case R.id.btnRead:
                    Log.e(TAG, "UPDATE pressed");
                    ReadGatt();
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

        mTextStatus = (TextView) findViewById(R.id.textState);
        mTextRead = (TextView) findViewById(R.id.textView2);
        mBtnConn = (Button) findViewById(R.id.btnConn);
        mBtnDiscon = (Button) findViewById(R.id.btnDisConn);
        mBtnUpdate = (Button) findViewById(R.id.btnRead);

        mBtnConn.setOnClickListener(BtnClickListener);
        mBtnDiscon.setOnClickListener(BtnClickListener);
        mBtnUpdate.setOnClickListener(BtnClickListener);

        mBtnConn.setEnabled(true);
        mBtnDiscon.setEnabled(false);
        mBtnUpdate.setEnabled(false);

        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                mTextStatus.setText(((BleStat) msg.obj).name());

                if (msg.obj.toString().equals(BleStat.READY.toString())) {
                    mBtnConn.setEnabled(false);
                    mBtnDiscon.setEnabled(true);
                    mBtnUpdate.setEnabled(true);
                }
                if (msg.obj.toString().equals(BleStat.BUSY.toString())) {
                    mBtnUpdate.setEnabled(false);
                }
                if (msg.obj.toString().equals(BleStat.CLOSED.toString())) {
                    mBtnConn.setEnabled(true);
                    mBtnDiscon.setEnabled(false);
                    mBtnUpdate.setEnabled(false);
                }
                if (msg.obj.toString().equals(BleStat.DISCONNECTED.toString())) {
                    mBtnConn.setEnabled(true);
                    mBtnDiscon.setEnabled(false);
                    mBtnUpdate.setEnabled(false);
                }
                if (msg.obj.toString().equals(BleStat.DATA_UPDATE.toString())) {
                    mBtnUpdate.setEnabled(true);

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


    private void ReadGatt() {
        if (mDataCharacteristic != null) {
            mGatt.readCharacteristic(mDataCharacteristic);
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
                mBluetoothGatt = null;
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.d(TAG, "onServicesDiscovered received: " + status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(UUID.fromString(DEVICE_SERVICE_UUID));
                if (service == null) {
                    setStatus(BleStat.SERVICE_NOT_FOUND);

                } else {
                    setStatus(BleStat.SERVICE_FOUND);

                    mDataCharacteristic =
                            service.getCharacteristic(UUID.fromString(DEVICE_CHARACTERISTIC_UUID));
                    if (mDataCharacteristic == null) {
                        setStatus(BleStat.CHARACTERISTIC_NOT_FOUND);

                    } else {

                        mGatt = gatt;
                        setStatus(BleStat.READY);
                        ReadGatt();
                    }
                }
            }
        }


        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {

            Log.d(TAG, "onCharacteristicRead: " + status);
            if (status == BluetoothGatt.GATT_SUCCESS) {

                byte[] read_data = characteristic.getValue();
                String tempdata = new String(read_data);
                mReadData = tempdata;

                Log.d(TAG, "data = " + tempdata);
                setStatus(BleStat.DATA_UPDATE);
            } else {

                Log.e(TAG, "no_data!");

            }

        }

    };

}
