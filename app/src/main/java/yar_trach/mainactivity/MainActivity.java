package yar_trach.mainactivity;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;

import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.ListView;

import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;


public class MainActivity extends Activity {

    private static final int REQUEST_ENABLE_BT = 1;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket socket;
    private final UUID PORT_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
    public OutputStream outputStream;
    public InputStream inputStream;
    private BluetoothDevice activeDevice;

    Button btnScanDevices, btnDisconnectDevice, btnOnOff;
    Switch bluetoothSwitch;
    ListView foundDevices;
    TextView bluetoothState;
    ArrayAdapter<String> bluetoothArrayAdapter;
    Thread thread;
    byte buffer[];
    boolean stopThread;
    int led = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnScanDevices = (Button) findViewById(R.id.btnScanDevices);
        btnDisconnectDevice = (Button) findViewById(R.id.btnDisconnectDevice);
        btnOnOff = (Button) findViewById(R.id.btnOnOff);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        bluetoothArrayAdapter = new ArrayAdapter<String>(MainActivity.this, android.R.layout.simple_list_item_1);
        foundDevices = (ListView) findViewById(R.id.foundDevices);
        bluetoothState = (TextView) findViewById(R.id.bluetoothState);
        bluetoothSwitch = (Switch) findViewById(R.id.bluetoothSwitch);
        foundDevices.setAdapter(bluetoothArrayAdapter);

        checkBluetoothState();

        btnScanDevices.setOnClickListener(btnScanDevicesOnClickListener);
        btnDisconnectDevice.setOnClickListener(btnDisconnectDeviceOnClickListener);
        btnOnOff.setOnClickListener(btnOnOffOnClickListener);
        registerReceiver(actionFoundReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));

        foundDevices.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                bluetoothAdapter.cancelDiscovery();

                final String info = ((TextView) view).getText().toString();
                final String address = info.substring(info.length() - 17);

                if (checkBluetoothState()) {
                    if (findDevice(address)) {
                        if (connectToDevice()) {
                            Toast.makeText(getApplicationContext(), "Connected", Toast.LENGTH_SHORT).show();

                            beginDataListening();
                        } else {
                            Toast.makeText(getApplicationContext(), "Not connected", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            }
        });

        bluetoothSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) on(null);
                else off(null);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(actionFoundReceiver);
    }

    private void on(View v){
        if (!bluetoothAdapter.isEnabled()) {
            Intent turnOn = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(turnOn, 0);
            Toast.makeText(getApplicationContext(), "Turned on",Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(getApplicationContext(), "Already on", Toast.LENGTH_LONG).show();
        }
    }

    private void off(View v){
        bluetoothAdapter.disable();
        Toast.makeText(getApplicationContext(), "Turned off" ,Toast.LENGTH_LONG).show();
    }

    private boolean checkBluetoothState() {
        boolean enabledBluetooth = false;
        if (bluetoothAdapter == null) {
            if (enabledBluetooth) enabledBluetooth = false;
            bluetoothState.setText("Bluetooth not supported");
        } else {
            if (bluetoothAdapter.isEnabled()) {
                enabledBluetooth = true;
                bluetoothSwitch.setChecked(true);
            } else {
                bluetoothSwitch.setChecked(false);
                bluetoothState.setText("Bluetooth is disabled");
                Intent enableBluetoothIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBluetoothIntent, REQUEST_ENABLE_BT);
            }
        }
        return enabledBluetooth;
    }

    private boolean findDevice(String deviceAddress) {
        boolean found = false;

        Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();
        if (bondedDevices.isEmpty()) {
            Toast.makeText(getApplicationContext(), "Pair the device first", Toast.LENGTH_SHORT).show();
        } else {
            for (BluetoothDevice device : bondedDevices) {
                if (device.getAddress().equals(deviceAddress)) {
                    activeDevice = device;
                    found = true;
                    break;
                }
            }
        }

        return found;
    }

    private boolean connectToDevice() {
        boolean connected = false;

        try {
            socket = activeDevice.createRfcommSocketToServiceRecord(PORT_UUID);
            socket.connect();
            connected = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (connected) {
            try {
                outputStream = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                inputStream = socket.getInputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return connected;
    }

    private void beginDataListening() {
        final Handler handler = new Handler();
        stopThread = false;
        buffer = new byte[1024];
        Thread thread = new Thread(new Runnable() {
            public void run() {
                while (!Thread.currentThread().isInterrupted() && !stopThread) {
                    try {
                        int byteCount = inputStream.available();
                        if (byteCount > 0) {
                            byte[] rawBytes = new byte[byteCount];
                            inputStream.read(rawBytes);

                            final String string = new String(rawBytes, "UTF-8");
                            handler.post(new Runnable() {
                                public void run() {
                                }
                            });
                        }
                    } catch (IOException e) {
                        stopThread = true;
                    }
                }
            }
        });
        thread.start();
    }



    /**
     *
     * Scan devices
     */
    private Button.OnClickListener btnScanDevicesOnClickListener = new Button.OnClickListener() {
        @Override
        public void onClick(View v) {
            bluetoothArrayAdapter.clear();
            bluetoothAdapter.startDiscovery();
        }
    };

    /**
     *
     * Disconnection
     */
    private Button.OnClickListener btnDisconnectDeviceOnClickListener = new Button.OnClickListener() {
        @Override
        public void onClick(View v) {
            try {
                inputStream.close();
                outputStream.close();
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    };

    private Button.OnClickListener btnOnOffOnClickListener = new Button.OnClickListener() {
        @Override
        public void onClick(View v) {
            try {
                outputStream.write(String.valueOf(led).getBytes());
                Toast.makeText(getApplicationContext(), String.valueOf(led), Toast.LENGTH_SHORT).show();
                if (led == 1) {
                    led = 0;
                } else {
                    led = 1;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            checkBluetoothState();
        }
    }

    private final BroadcastReceiver actionFoundReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                bluetoothArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                bluetoothArrayAdapter.notifyDataSetChanged();
            }
        }
    };
}
