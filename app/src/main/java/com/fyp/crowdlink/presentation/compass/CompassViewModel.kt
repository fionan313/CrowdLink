package com.fyp.crowdlink.presentation.compass

import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fyp.crowdlink.data.ble.BleScanner
import com.fyp.crowdlink.domain.model.DeviceLocation
import com.fyp.crowdlink.domain.repository.LocationRepository
import com.fyp.crowdlink.domain.usecase.ShareLocationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * CompassViewModel
 *
 * Computes bearing and distance to a paired friend by combining the local device's GPS fix
 * with the friend's last known location received over the mesh. Device heading is derived
 * from fused accelerometer and magnetometer readings with a low-pass filter applied to
 * reduce sensor jitter. Falls back to BLE RSSI distance when GPS is unavailable or stale.
 */
@HiltViewModel
class CompassViewModel @Inject constructor(
    private val locationRepository: LocationRepository,
    private val sensorManager: SensorManager,
    private val bleScanner: BleScanner,
    private val shareLocationUseCase: ShareLocationUseCase,
    private val sharedPreferences: SharedPreferences
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

    private val _indoorOverride = MutableStateFlow(
        sharedPreferences.getBoolean("indoor_override", false)
    )

    // raw sensor buffers for accelerometer and magnetometer fusion
    private var gravity: FloatArray? = null
    private var geomagnetic: FloatArray? = null
    private val alpha = 0.15f // low-pass filter weight - smaller = smoother but slower to react

    // tracks the last position used for bearing to apply the 2-metre movement threshold
    private var lastBearingLocation: Location? = null

    init {
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI)

        // reactively recompute distance, bearing and GPS availability whenever any input changes
        combine(_myLocation, _friendLocation, _indoorOverride) { myLoc, friendLoc, forceIndoor ->
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

                // only update bearing when the device has moved more than 2 metres
                // prevents the arrow jittering while the user is stationary
                if (lastBearingLocation == null || myAndroidLoc.distanceTo(lastBearingLocation!!) > 2f) {
                    val friendAndroidLoc = Location("").apply {
                        latitude = friendLoc.latitude
                        longitude = friendLoc.longitude
                    }
                    _bearingToFriend.value = myAndroidLoc.bearingTo(friendAndroidLoc)
                    lastBearingLocation = myAndroidLoc
                }

                // GPS is considered unreliable if the friend's location is over 60 seconds old
                val isStale = System.currentTimeMillis() - friendLoc.timestamp > 60000
                _isGpsAvailable.value = !forceIndoor && myLoc.accuracy < 50f && !isStale
            } else {
                _distanceMetres.value = null
                _bearingToFriend.value = null
                _isGpsAvailable.value = false
            }
        }.launchIn(viewModelScope)

        // track BLE RSSI distance for the indoor fallback indicator
        bleScanner.discoveredDevices
            .onEach { devices ->
                val device = devices.firstOrNull { it.deviceId == currentFriendId }
                _rssiDistance.value = device?.estimatedDistance
            }
            .launchIn(viewModelScope)
    }

    private var locationCollectionJob: Job? = null

    /**
     * Sets the friend to track and starts collecting their cached location from Room.
     * Cancels any previous collection job to avoid observing a stale friend's location.
     */
    fun setFriendId(friendId: String) {
        currentFriendId = friendId
        locationCollectionJob?.cancel()
        locationCollectionJob = viewModelScope.launch {
            locationRepository.getFriendLocation(friendId).collect {
                _friendLocation.value = it
            }
        }
    }

    fun refreshIndoorOverride() {
        _indoorOverride.value = sharedPreferences.getBoolean("indoor_override", false)
    }

    fun shareLocation() {
        viewModelScope.launch {
            if (currentFriendId.isNotEmpty()) {
                shareLocationUseCase(currentFriendId)
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        // apply low-pass filter to each sensor's raw values before fusing
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
                _compassHeading.value = (azimuth + 360) % 360 // normalise to 0-360
            }
        }
    }

    /**
     * Applies a basic exponential moving average to reduce noise on raw sensor vectors.
     * Alpha controls the trade-off between smoothness and responsiveness.
     */
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
        sensorManager.unregisterListener(this) // unregister to prevent sensor leaks after the VM is destroyed
    }
}