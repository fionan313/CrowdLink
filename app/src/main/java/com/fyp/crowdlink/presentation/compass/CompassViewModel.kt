package com.fyp.crowdlink.presentation.compass

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fyp.crowdlink.data.ble.BleScanner
import com.fyp.crowdlink.domain.model.DeviceLocation
import com.fyp.crowdlink.domain.repository.FriendRepository
import com.fyp.crowdlink.domain.repository.LocationRepository
import com.fyp.crowdlink.domain.usecase.EstimateDistanceUseCase
import com.fyp.crowdlink.domain.usecase.ShareLocationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CompassViewModel @Inject constructor(
    private val locationRepository: LocationRepository,
    private val friendRepository: FriendRepository,
    private val sensorManager: SensorManager,
    private val bleScanner: BleScanner,
    private val estimateDistanceUseCase: EstimateDistanceUseCase,
    private val shareLocationUseCase: ShareLocationUseCase
) : ViewModel(), SensorEventListener {

    private val _myLocation = locationRepository.getMyLocation().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), null
    )

    private var currentFriendId: String = ""
    private val _friendLocation = MutableStateFlow<DeviceLocation?>(null)

    private val _compassHeading = MutableStateFlow(0f)
    val compassHeading: StateFlow<Float> = _compassHeading.asStateFlow()

    private val _bearingToFriend = MutableStateFlow<Float?>(null)
    val bearingToFriend: StateFlow<Float?> = _bearingToFriend.asStateFlow()

    private val _distanceMetres = MutableStateFlow<Float?>(null)
    val distanceMetres: StateFlow<Float?> = _distanceMetres.asStateFlow()

    private val _isGpsAvailable = MutableStateFlow(false)
    val isGpsAvailable: StateFlow<Boolean> = _isGpsAvailable.asStateFlow()

    private val _rssiDistance = MutableStateFlow<Double?>(null)
    val rssiDistance: StateFlow<Double?> = _rssiDistance.asStateFlow()

    // Smoothing sensors
    private var gravity: FloatArray? = null
    private var geomagnetic: FloatArray? = null
    private val alpha = 0.15f // Low-pass filter constant

    private var lastBearingLocation: Location? = null

    init {
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI)

        combine(_myLocation, _friendLocation) { myLoc, friendLoc ->
            if (myLoc != null && friendLoc != null) {
                val results = FloatArray(1)
                Location.distanceBetween(
                    myLoc.latitude, myLoc.longitude,
                    friendLoc.latitude, friendLoc.longitude,
                    results
                )
                _distanceMetres.value = results[0]

                val myAndroidLoc = Location("").apply {
                    latitude = myLoc.latitude
                    longitude = myLoc.longitude
                }

                // Only update bearing if we have significant movement (>2m) or it's the first fix
                // to avoid jitter when stationary
                if (lastBearingLocation == null || myAndroidLoc.distanceTo(lastBearingLocation!!) > 2f) {
                    val friendAndroidLoc = Location("").apply {
                        latitude = friendLoc.latitude
                        longitude = friendLoc.longitude
                    }
                    _bearingToFriend.value = myAndroidLoc.bearingTo(friendAndroidLoc)
                    lastBearingLocation = myAndroidLoc
                }

                val isStale = System.currentTimeMillis() - friendLoc.timestamp > 60000
                _isGpsAvailable.value = myLoc.accuracy < 50f && !isStale
            } else {
                _distanceMetres.value = null
                _bearingToFriend.value = null
                _isGpsAvailable.value = false
            }
        }.launchIn(viewModelScope)

        // Watch RSSI for the current friend from BLE scanner
        bleScanner.discoveredDevices
            .onEach { devices ->
                val device = devices.firstOrNull { it.deviceId == currentFriendId }
                _rssiDistance.value = device?.estimatedDistance
            }
            .launchIn(viewModelScope)
    }

    private var locationCollectionJob: Job? = null

    fun setFriendId(friendId: String) {
        currentFriendId = friendId
        locationCollectionJob?.cancel()
        locationCollectionJob = viewModelScope.launch {
            locationRepository.getFriendLocation(friendId).collect {
                _friendLocation.value = it
            }
        }
    }

    fun shareLocation() {
        viewModelScope.launch {
            if (currentFriendId.isNotEmpty()) {
                shareLocationUseCase(currentFriendId)
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            gravity = lowPass(event.values.clone(), gravity)
        }
        if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            geomagnetic = lowPass(event.values.clone(), geomagnetic)
        }

        if (gravity != null && geomagnetic != null) {
            val r = FloatArray(9)
            val i = FloatArray(9)
            if (SensorManager.getRotationMatrix(r, i, gravity, geomagnetic)) {
                val orientation = FloatArray(3)
                SensorManager.getOrientation(r, orientation)
                val azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
                
                // Final value smoothing
                val newHeading = (azimuth + 360) % 360
                _compassHeading.value = newHeading
            }
        }
    }

    private fun lowPass(input: FloatArray, output: FloatArray?): FloatArray {
        if (output == null) return input
        for (i in input.indices) {
            output[i] = output[i] + alpha * (input[i] - output[i])
        }
        return output
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onCleared() {
        super.onCleared()
        sensorManager.unregisterListener(this)
    }
}
