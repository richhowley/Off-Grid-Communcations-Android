/*

    This project is a modified version of an Android exaple project.

    It began its life as a Bluetooth chat program, then begame a BLE
    chat program that communicated with an Arduino using a HM-10 BLE
    module attached.

    This iteration is part of an off-grid communication system that
    uses an Arudino 101 with built-in BLE.  The serial characteristics
    used by the HM-10 are still used in this version.

 */


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

package com.example.android.offgrid;

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
import android.widget.ExpandableListView;
import android.widget.ListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


/**
 * For a given BLE device, this Activity provides the user interface to connect,
 * display data, and display GATT services and characteristics supported by the
 * device. The Activity communicates with {@code BluetoothLeService}, which in
 * turn interacts with the Bluetooth LE API.
 */
public class DeviceControlActivity extends Activity {
    private final static String TAG = DeviceControlActivity.class
            .getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    private TextView mConnectionState;
    private TextView mDataField;
    private String mDeviceName;
    private String mDeviceAddress;
    private ExpandableListView mGattServicesList;
    private BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;


    private CoversationAdaptor<String> mConversationAdapter;


    private String inBuffer=null;
    private ListView mConversationView;

    // blechat - characteristics for HM-10 serial
    private BluetoothGattCharacteristic characteristicTX;
    private BluetoothGattCharacteristic characteristicRX;

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName,
                                       IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service)
                    .getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up
            // initialization.
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
    // ACTION_DATA_AVAILABLE: received data from the device. This can be a
    // result of read
    // or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();

            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED
                    .equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                clearUI();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED
                    .equals(action)) {
                // Show all the supported services and characteristics on the
                // user interface.

                // blechat
                // set serial chaacteristics
                setupSerial();

            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {

                 String str = intent.getStringExtra(BluetoothLeService.EXTRA_DATA);

                // add string to input buffer, it will always end with a newline
                if( inBuffer == null )
                {
                    inBuffer = str.substring(0,str.indexOf('\n'));

                } else {
                    inBuffer = inBuffer.concat(str.substring(0,str.indexOf('\n')));

                } // else


                // if string has EOT character
                if( inBuffer.indexOf('~') >= 0)
                {
                    // write it to screen
                    displayData(inBuffer.substring(0,inBuffer.indexOf('~')));

                    // nuke contents of buffer
                    inBuffer = null;

                } // if

            } // else
        }
    };

    // If a given GATT characteristic is selected, check for supported features.
    // This sample
    // demonstrates 'Read' and 'Notify' features. See
    // http://d.android.com/reference/android/bluetooth/BluetoothGatt.html for
    // the complete
    // list of supported characteristic features.
    private final ExpandableListView.OnChildClickListener servicesListClickListner = new ExpandableListView.OnChildClickListener() {
        @Override
        public boolean onChildClick(ExpandableListView parent, View v,
                                    int groupPosition, int childPosition, long id) {
            if (mGattCharacteristics != null) {
                final BluetoothGattCharacteristic characteristic = mGattCharacteristics
                        .get(groupPosition).get(childPosition);
                final int charaProp = characteristic.getProperties();
                if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                    // If there is an active notification on a characteristic,
                    // clear
                    // it first so it doesn't update the data field on the user
                    // interface.
                    if (mNotifyCharacteristic != null) {
                        mBluetoothLeService.setCharacteristicNotification(
                                mNotifyCharacteristic, false);
                        mNotifyCharacteristic = null;
                    }
                    mBluetoothLeService.readCharacteristic(characteristic);
                }
                if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                    mNotifyCharacteristic = characteristic;
                    mBluetoothLeService.setCharacteristicNotification(
                            characteristic, true);
                }

                return true;
            }
            return false;
        }
    };

    private void clearUI() {
        mGattServicesList.setAdapter((SimpleExpandableListAdapter) null);
        mDataField.setText(R.string.no_data);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gatt_services_characteristics);

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        // Sets up UI references.
        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
        mGattServicesList = (ExpandableListView) findViewById(R.id.gatt_services_list);
        mGattServicesList.setOnChildClickListener(servicesListClickListner);
        mConnectionState = (TextView) findViewById(R.id.connection_state);
        mDataField = (TextView) findViewById(R.id.data_value);

        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

    }

    // setupSerial
    //
    // set serial characteristics
    //
    private void setupSerial() {

        // blechat - set serial characteristics
        String uuid;
        String unknownServiceString = getResources().getString(
                R.string.unknown_service);

        for (BluetoothGattService gattService : mBluetoothLeService
                .getSupportedGattServices()) {
            // HashMap<String, String> currentServiceData = new HashMap<String,
            // String>();
            uuid = gattService.getUuid().toString();

            // If the service exists for HM 10 Serial,set RX and TX
            if (SampleGattAttributes.lookup(uuid, unknownServiceString) == "HM 10 Serial") {

                // get characteristic when UUID matches RX/TX UUID
                characteristicTX = gattService
                        .getCharacteristic(BluetoothLeService.UUID_HM_RX_TX);
                characteristicRX = gattService
                        .getCharacteristic(BluetoothLeService.UUID_HM_RX_TX);

                mBluetoothLeService.setCharacteristicNotification(
                        characteristicRX, true);

                break;

            } // if

        } // for


    }

    // blechat
    @Override
    public void onStart() {
        super.onStart();

        // change view for chat
        setContentView(R.layout.communication_layout);

        // Initialize the array adapter for the conversation
        mConversationAdapter = new CoversationAdaptor<String>(this, R.layout.message);

        // set adapter for message view
        mConversationView = (ListView) findViewById(R.id.in);
        mConversationView.setAdapter(mConversationAdapter);

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
        switch (item.getItemId()) {
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
            mDataField.setText(data);

            int nlIdx = data.indexOf('\n');  // index of newline

            // blechat
            // add received data to screen

            // if there is a newline
            if( nlIdx > 0 ) {
                mConversationAdapter.add(data.substring(0,nlIdx));

            } else {
                // use whole string
                mConversationAdapter.add(data);

            } // else

        }
    }

    private void onAck(String data) {
        if (data != null) {
            mDataField.setText(data);

            int nlIdx = data.indexOf('\n');  // index of newline

            // blechat
            // add received data to screen

            // if there is a newline
            if( nlIdx > 0 ) {
                mConversationAdapter.add(data.substring(0,nlIdx));

            } else {
                // use whole string
                mConversationAdapter.add(data);

            } // else


        }
    }

    // Demonstrates how to iterate through the supported GATT
    // Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the
    // ExpandableListView
    // on the UI.
    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null)
            return;
        String uuid = null;
        String unknownServiceString = getResources().getString(
                R.string.unknown_service);
        String unknownCharaString = getResources().getString(
                R.string.unknown_characteristic);
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData = new ArrayList<ArrayList<HashMap<String, String>>>();
        mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData = new HashMap<String, String>();
            uuid = gattService.getUuid().toString();
            currentServiceData.put(LIST_NAME,
                    SampleGattAttributes.lookup(uuid, unknownServiceString));
            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

            ArrayList<HashMap<String, String>> gattCharacteristicGroupData = new ArrayList<HashMap<String, String>>();
            List<BluetoothGattCharacteristic> gattCharacteristics = gattService
                    .getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas = new ArrayList<BluetoothGattCharacteristic>();

            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                charas.add(gattCharacteristic);
                HashMap<String, String> currentCharaData = new HashMap<String, String>();
                uuid = gattCharacteristic.getUuid().toString();
                currentCharaData.put(LIST_NAME,
                        SampleGattAttributes.lookup(uuid, unknownCharaString));
                currentCharaData.put(LIST_UUID, uuid);
                gattCharacteristicGroupData.add(currentCharaData);
            }
            mGattCharacteristics.add(charas);
            gattCharacteristicData.add(gattCharacteristicGroupData);

        }

        SimpleExpandableListAdapter gattServiceAdapter = new SimpleExpandableListAdapter(
                this, gattServiceData,
                android.R.layout.simple_expandable_list_item_2, new String[] {
                LIST_NAME, LIST_UUID }, new int[] { android.R.id.text1,
                android.R.id.text2 }, gattCharacteristicData,
                android.R.layout.simple_expandable_list_item_2, new String[] {
                LIST_NAME, LIST_UUID }, new int[] { android.R.id.text1,
                android.R.id.text2 });
        mGattServicesList.setAdapter(gattServiceAdapter);

    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter
                .addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    // blechat

    // btnClick
    //
    // Click handler for Send button
    //
    public void btnClick(View view) {

        // retrieve message
        TextView txtView = (TextView) findViewById(R.id.edit_text_out);
        String fullMessage = txtView.getText().toString();

        // send message out serial port
        sendSerial(fullMessage);

        // clear text
        txtView.setText("");

    }


    // blechat
    //
    // sendSerial
    //
    // Send string io out field
    private void sendSerial(String msg) {

		/*
		Log.d(TAG, "Sending: " + msg);
		final byte[] tx = msg.getBytes();
		if (mConnected) {
			characteristicTX.setValue(tx);
			mBluetoothLeService.writeCharacteristic(characteristicTX);
		} // if
		*/

        Log.d(TAG, "Sending: " + msg);
        final byte[] tx = msg.getBytes();
        if (mConnected) {
            //	characteristicTX.setValue(tx);
            mBluetoothLeService.writeString(characteristicTX,msg);


        } // if

    }

}
