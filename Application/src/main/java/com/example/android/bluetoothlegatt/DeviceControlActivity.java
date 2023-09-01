/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.bluetoothlegatt;

import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import android.widget.TextView;
import android.widget.Toast;

import java.util.Iterator;
import java.util.List;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlActivity extends Activity {
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    private TextView mConnectionState;
    private TextView mDataField;
    private TextView mReceiveCntTv;
    private TextView mSendCntTv;
    private Button mClearRecvBtn;
    private Button mClearSendBtn;
    private Button mSendBtn;
    private EditText mSendDataEt;
    private String mDeviceName;
    private String mDeviceAddress;
    private List<BluetoothGattService> mAllGattService;
    private BluetoothGattService mUserDefService;
    private BluetoothLeService mBluetoothLeService;
    private boolean mConnected = false;

    private int send_byte_cnt = 0;
    private int receive_byte_cnt = 0;

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();

            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                clearUI();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                //TODO:发现服务之后不列举出所有服务，直接匹配用户自定义的服务
                mAllGattService = mBluetoothLeService.getSupportedGattServices();
                if(mAllGattService == null) {
                    return;
                }
                Iterator<BluetoothGattService> iterator = mAllGattService.iterator();

                while(iterator.hasNext())
                {
                    mUserDefService = iterator.next();
                    String serviceUuid = mUserDefService.getUuid().toString();
                    if(serviceUuid.equals(SampleGattAttributes.TRANSMIT_SERVICE_UUID))
                    {
                        //遍历特征值
                        List<BluetoothGattCharacteristic> characteristics = mUserDefService.getCharacteristics();
                        for (BluetoothGattCharacteristic characteristic : characteristics) {
                            String characteristicUuid = characteristic.getUuid().toString();
                            Log.d(TAG, "Characteristic discovered: " + characteristicUuid);

                            //使能CCCD
                            if(characteristic.PROPERTY_NOTIFY != 0)
                            {
                                if(characteristicUuid.equals(SampleGattAttributes.TRANSMIT_READ_UUID))
                                {
                                    mBluetoothLeService.setCharacteristicNotification(
                                            characteristic, true);
                                }
                            }
                        }
                    }
                }
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                Log.i(TAG, "display data.");
                displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
            }
        }
    };

    private void clearUI() {
        mDataField.setText("");
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gatt_services_characteristics);

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        init_ui();

        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    private void init_ui() {
        // Sets up UI references.
        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);

        mConnectionState = (TextView) findViewById(R.id.connection_state);
        mDataField = (TextView) findViewById(R.id.data_value);
        mReceiveCntTv = (TextView) findViewById(R.id.receive_cnt_tv);
        mSendCntTv = (TextView) findViewById(R.id.send_cnt_tv);
        mClearRecvBtn = (Button) findViewById(R.id.clrar_receive_btn);
        mClearSendBtn = (Button) findViewById(R.id.clear_send_btn);
        mSendBtn = (Button) findViewById(R.id.send_btn);
        mSendDataEt = (EditText) findViewById(R.id.et_send_data);

        mClearRecvBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mReceiveCntTv.setText("0");
                mDataField.setText("");
                receive_byte_cnt = 0;
            }
        });

        mClearSendBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mSendCntTv.setText("0");
                mSendDataEt.setText("");
                send_byte_cnt = 0;

            }
        });

        //发送数据
        mSendBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mBluetoothLeService != null)
                {
                    //获取的内容转换为字符串，再发送出去
//                    mBluetoothLeService.sendData(mSendDataEt.getText().toString().getBytes());

                    //获取的内容为十六进制数据
                    String hexString = mSendDataEt.getText().toString();
                    // 去除字符串中的空格
                    hexString = hexString.replaceAll("\\s+", "");

                    if(hexString.length() % 2 != 0) {
                        Toast.makeText(getApplicationContext(), "仅支持十六进制数据发送", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // 计算字节数组长度
                    int length = hexString.length() / 2;
                    byte[] hexBytes = new byte[length];
                    // 将十六进制字符串转换为字节数组
                    for (int i = 0; i < length; i++) {
                        int startIndex = i * 2;
                        int endIndex = startIndex + 2;
                        String byteString = hexString.substring(startIndex, endIndex);
                        hexBytes[i] = (byte) ((Character.digit(byteString.charAt(0), 16) << 4)
                                + Character.digit(byteString.charAt(1), 16));
                    }
                    try {
                        mBluetoothLeService.sendData(hexBytes);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    send_byte_cnt += length;

                    mSendCntTv.setText(Integer.toString(send_byte_cnt));

                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_connect:
                mBluetoothLeService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setText(resourceId);
            }
        });
    }

    private void displayData(String data) {
        if (data != null) {
            //BUG:当mtu比较小时，两次调用此函数的时间较短，导致前一次的没显示完全就会被覆盖掉
            mDataField.setText(data);
            String hexString = data.replaceAll("\\s+", "");
            receive_byte_cnt += hexString.length() / 2;
            mReceiveCntTv.setText(Integer.toString(receive_byte_cnt));
        }
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }
}
