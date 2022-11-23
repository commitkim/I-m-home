package com.geonwoo.imhome

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings.ACTION_BLUETOOTH_SETTINGS
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.geonwoo.imhome.databinding.FragmentBluetoothBinding

class BluetoothFragment : Fragment() {

    private lateinit var binding: FragmentBluetoothBinding

    // 대충 1M 거리인듯
    private val MIN_RSSI = -50

    private val bluetoothAdapter by lazy {
        (requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    private val leScanner by lazy {
        bluetoothAdapter.bluetoothLeScanner
    }

    private val gattCallback: BluetoothGattCallback = object : BluetoothGattCallback() {
        @Override
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (ActivityCompat.checkSelfPermission(
                            requireContext(),
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        return
                    }

                    requireActivity().runOnUiThread {
                        scanLeDevice(false)
                        setPulsator(true)
                        binding.connectInfo.text = "연결된 디바이스 : ${gatt.device.name}"
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d("Jungbong", "STATE_DISCONNECTED")
                }
            }
        }
    }

    private val permissions = arrayOf(
        Manifest.permission.BLUETOOTH,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN
    )

    private val requestMultiplePermissions =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            if (it.all { permission -> permission.value }) {
                scanLeDevice(true)
            } else {
                showToast("권한 거부")
            }
        }

    private fun PackageManager.missingSystemFeature(name: String): Boolean = !hasSystemFeature(name)

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            if (result?.device == null || result.rssi < MIN_RSSI) {
                return
            }

            connectDevice(result.device)
        }
    }

    private fun connectDevice(device: BluetoothDevice) {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        if (bluetoothAdapter.bondedDevices.contains(device)) {
            Log.d("Jungbong", "connectDevice")
            device.connectGatt(requireContext(), false, gattCallback)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentBluetoothBinding.inflate(inflater, container, false)
        initView()

        if (!checkPermission(permissions)) {
            requestMultiplePermissions.launch(permissions)
        }

        return binding.root
    }

    private fun initView() {
        binding.goToSettingButton.setOnClickListener {
            val intent = Intent(ACTION_BLUETOOTH_SETTINGS)
            startActivityForResult(intent, 0)
        }

        binding.scanButton.setOnClickListener {
            val textView = it as TextView
            val isScan = textView.text == "Stop scan"
            scanLeDevice(!isScan)
        }

        binding.connectInfo.text = "연결된 디바이스가 없습니다.\n블루투스 설정으로 이동하여 페어링하거나 스캔을 시작해주세요."
    }

    private fun scanLeDevice(enable: Boolean) {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        binding.scanButton.text = if (enable) {
            leScanner.startScan(scanCallback)
            setPulsator(!enable)
            "Stop scan"
        } else {
            leScanner.stopScan(scanCallback)
            binding.pulsatorDisconnected.stop()
            binding.pulsatorConnected.stop()
            "Start scan"
        }
    }

    private fun checkPermission(permissions: Array<String>): Boolean {
        return permissions.all {
            ContextCompat.checkSelfPermission(
                requireContext(),
                it
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onResume() {
        super.onResume()

        requireContext().packageManager.takeIf { it.missingSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) }
            ?.also {
                showToast("블루투스를 지원하지 않습니다.")
            }
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    private fun setPulsator(isConnected: Boolean) {
        if (isConnected) {
            binding.pulsatorDisconnected.stop()
            binding.pulsatorDisconnected.visibility = View.GONE
            binding.pulsatorConnected.start()
            binding.pulsatorConnected.visibility = View.VISIBLE
        } else {
            binding.pulsatorConnected.stop()
            binding.pulsatorConnected.visibility = View.GONE
            binding.pulsatorDisconnected.start()
            binding.pulsatorDisconnected.visibility = View.VISIBLE
        }
    }
}