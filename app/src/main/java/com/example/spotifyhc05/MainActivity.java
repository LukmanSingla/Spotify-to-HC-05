package com.example.spotifyhc05;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity {
    public static final String ACTION_TRACK_UPDATE = "com.example.spotifyhc05.TRACK_UPDATE";
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private BluetoothAdapter adapter;
    private BluetoothSocket socket;
    private OutputStream output;
    private Spinner deviceSpinner;
    private TextView status;
    private TextView currentTrack;
    private final ArrayList<BluetoothDevice> devices = new ArrayList<>();
    private String lastSent = "";

    private final BroadcastReceiver trackUpdates = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String action = intent.getAction();
            if (!ACTION_TRACK_UPDATE.equals(action) && !"com.spotify.music.metadatachanged".equals(action)) return;
            String track = intent.getStringExtra("track");
            String artist = intent.getStringExtra("artist");
            showAndSend(track == null ? "" : track, artist == null ? "" : artist);
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) { status.setText("This phone has no Bluetooth adapter."); return; }
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 100);
        } else loadPairedDevices();
        IntentFilter filter = new IntentFilter(ACTION_TRACK_UPDATE);
        filter.addAction("com.spotify.music.metadatachanged");
        if (Build.VERSION.SDK_INT >= 33) {
    registerReceiver(trackUpdates, filter, Context.RECEIVER_EXPORTED);
} else {
    registerReceiver(trackUpdates, filter);
}
        if (getIntent() != null && ACTION_TRACK_UPDATE.equals(getIntent().getAction())) handleTrackIntent(getIntent());
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(24, 28, 24, 24);
        TextView title = new TextView(this); title.setText("Spotify HC-05 Bridge"); title.setTextSize(24); root.addView(title);
        status = new TextView(this); status.setText("Choose your paired HC-05 device."); status.setTextSize(16); root.addView(status);
        deviceSpinner = new Spinner(this); root.addView(deviceSpinner);
        Button refresh = new Button(this); refresh.setText("Refresh paired devices"); refresh.setOnClickListener(v -> loadPairedDevices()); root.addView(refresh);
        Button connect = new Button(this); connect.setText("Connect to HC-05"); connect.setOnClickListener(v -> connectSelected()); root.addView(connect);
        Button disconnect = new Button(this); disconnect.setText("Disconnect"); disconnect.setOnClickListener(v -> disconnect()); root.addView(disconnect);
        currentTrack = new TextView(this); currentTrack.setText("Waiting for Spotify song metadata…"); currentTrack.setTextSize(18); root.addView(currentTrack);
        TextView note = new TextView(this); note.setText("Enable Spotify → Settings → Devices → Device Broadcast Status. Keep this app open while testing."); root.addView(note);
        setContentView(root, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) loadPairedDevices();
        else status.setText("Bluetooth permission is required.");
    }

    private boolean hasConnectPermission() {
        return Build.VERSION.SDK_INT < 31 || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private void loadPairedDevices() {
        if (adapter == null) return;
        if (Build.VERSION.SDK_INT >= 31 && !hasConnectPermission()) { requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 100); return; }
        if (!adapter.isEnabled()) { status.setText("Turn Bluetooth on, then refresh."); return; }
        devices.clear(); ArrayList<String> labels = new ArrayList<>();
        Set<BluetoothDevice> paired = adapter.getBondedDevices();
        for (BluetoothDevice d : paired) {
            devices.add(d); String name = d.getName(); labels.add((name == null ? "Unknown device" : name) + " — " + d.getAddress());
        }
        if (labels.isEmpty()) labels.add("No paired devices — pair HC-05 in Android Settings first");
        deviceSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        status.setText("Found " + devices.size() + " paired device(s).");
    }

    private void connectSelected() {
        if (!hasConnectPermission()) { requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 100); return; }
        int index = deviceSpinner.getSelectedItemPosition();
        if (index < 0 || index >= devices.size()) { status.setText("Pair the HC-05 in Android Bluetooth settings first."); return; }
        final BluetoothDevice device = devices.get(index);
        status.setText("Connecting to " + device.getName() + "…");
        new Thread(() -> {
            try {
                if (socket != null) try { socket.close(); } catch (Exception ignored) {}
                BluetoothSocket candidate = device.createRfcommSocketToServiceRecord(SPP_UUID);
                candidate.connect();
                OutputStream stream = candidate.getOutputStream();
                runOnUiThread(() -> {
    socket = candidate;
    output = stream;

    status.setText("Connected to HC-05.");
    Toast.makeText(this, "Bluetooth serial connected", Toast.LENGTH_SHORT).show();

    sendLatestTrack();
});
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("Connection failed: " + e.getMessage() + ". Close Serial Bluetooth Terminal first and retry."));
            }
        }).start();
    }

    private void disconnect() {
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        socket = null; output = null; status.setText("Disconnected.");
    }

    private void handleTrackIntent(Intent intent) { showAndSend(intent.getStringExtra("track"), intent.getStringExtra("artist")); }

    private void showAndSend(String track, String artist) {
    if (track == null) track = "";
    if (artist == null) artist = "";

    if (track.trim().isEmpty()) return;

    String line = artist.trim().isEmpty()
            ? track.trim()
            : track.trim() + " - " + artist.trim();

    runOnUiThread(() -> currentTrack.setText("Now playing:\n" + line));

    // Remember the latest song, even if Bluetooth is disconnected.
    lastSent = line;

    sendLatestTrack();
}

private void sendLatestTrack() {
    final String payload = lastSent;

    if (payload == null || payload.isEmpty()) return;

    new Thread(() -> {
        try {
            BluetoothSocket currentSocket = socket;
            OutputStream currentOutput = output;

            if (currentSocket != null
                    && currentOutput != null
                    && currentSocket.isConnected()) {

                currentOutput.write(
                        (payload + "\n").getBytes(StandardCharsets.UTF_8)
                );
                currentOutput.flush();

                runOnUiThread(() ->
                        status.setText("Connected — sent latest song to Arduino.")
                );
            } else {
                runOnUiThread(() ->
                        status.setText("Song detected; connect to HC-05 to send it.")
                );
            }
        } catch (Exception e) {
            runOnUiThread(() ->
                    status.setText("Send failed. Reconnect to HC-05.")
            );
        }
    }).start();
}

    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); handleTrackIntent(intent); }
    @Override protected void onDestroy() {
        try { unregisterReceiver(trackUpdates); } catch (Exception ignored) {}
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        super.onDestroy();
    }
}
