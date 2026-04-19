package com.fyp.crowdlink.presentation.map

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fyp.crowdlink.domain.model.DeviceLocation
import com.fyp.crowdlink.domain.model.Friend
import com.fyp.crowdlink.domain.repository.FriendRepository
import com.fyp.crowdlink.domain.repository.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

/**
 * FriendMapPin
 *
 * Pairs a friend's identity with their last known location received over the mesh,
 * used as the unit of data for rendering pins on the map.
 */
data class FriendMapPin(
    val friend: Friend,
    val location: DeviceLocation
)

/**
 * MapViewModel
 *
 * Manages reactive state for the map screen. Observes the local GPS position, aggregates
 * cached friend locations from Room into a list of [FriendMapPin] objects, and tracks
 * device heading via fused accelerometer and magnetometer sensors so the self-location
 * arrow rotates to reflect the user's bearing.
 */
@HiltViewModel
class MapViewModel @Inject constructor(
    private val locationRepository: LocationRepository,
    private val friendRepository: FriendRepository,
    private val sensorManager: SensorManager
) : ViewModel(), SensorEventListener {

    private val _myLocation = MutableStateFlow<DeviceLocation?>(null)
    val myLocation: StateFlow<DeviceLocation?> = _myLocation.asStateFlow()

    private val _friendPins = MutableStateFlow<List<FriendMapPin>>(emptyList())
    val friendPins: StateFlow<List<FriendMapPin>> = _friendPins.asStateFlow()

    private val _selectedFriendId = MutableStateFlow<String?>(null)
    val selectedFriendId: StateFlow<String?> = _selectedFriendId.asStateFlow()

    private val _isCachingTiles = MutableStateFlow(false)
    val isCachingTiles: StateFlow<Boolean> = _isCachingTiles.asStateFlow()

    private val _myHeading = MutableStateFlow(0f)
    val myHeading: StateFlow<Float> = _myHeading.asStateFlow()

    // raw sensor buffers for accelerometer/magnetometer fusion
    private var gravity: FloatArray? = null
    private var geomagnetic: FloatArray? = null
    private val alpha = 0.15f // low-pass filter weight

    init {
        // observe live GPS updates for the self-location arrow
        locationRepository.getMyLocation()
            .onEach { _myLocation.value = it }
            .launchIn(viewModelScope)

        // for each friend, observe their cached location and upsert into the pins list
        friendRepository.getAllFriends()
            .onEach { friends ->
                friends.forEach { friend ->
                    locationRepository.getFriendLocation(friend.deviceId)
                        .onEach { location ->
                            if (location != null) {
                                val existing = _friendPins.value.toMutableList()
                                val index = existing.indexOfFirst { it.friend.deviceId == friend.deviceId }
                                val pin = FriendMapPin(friend, location)
                                // replace existing pin or append if this friend has no pin yet
                                if (index >= 0) existing[index] = pin else existing.add(pin)
                                _friendPins.value = existing
                            }
                        }
                        .launchIn(viewModelScope)
                }
            }
            .launchIn(viewModelScope)

        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            gravity = lowPass(event.values.clone(), gravity)
        }
        if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            geomagnetic = lowPass(event.values.clone(), geomagnetic)
        }
        val g = gravity ?: return
        val m = geomagnetic ?: return

        // derive device heading from fused sensor vectors
        val r = FloatArray(9)
        val i = FloatArray(9)
        if (SensorManager.getRotationMatrix(r, i, g, m)) {
            val orientation = FloatArray(3)
            SensorManager.getOrientation(r, orientation)
            val azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
            _myHeading.value = (azimuth + 360) % 360 // normalise to 0-360
        }
    }

    /**
     * Applies an exponential moving average to reduce jitter on raw sensor vectors.
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

    fun selectFriend(friendId: String?) {
        _selectedFriendId.value = friendId
    }

    // called when navigating to the map with a pre-selected friend, e.g. from SOS alert
    fun selectFriendOnLoad(friendId: String?) {
        if (friendId != null) _selectedFriendId.value = friendId
    }

    // isCachingTiles is a UI flag only - the actual download runs in MapScreen via MapLibre
    fun startTileCaching() { _isCachingTiles.value = true }
    fun onTileCachingComplete() { _isCachingTiles.value = false }

    override fun onCleared() {
        super.onCleared()
        sensorManager.unregisterListener(this) // prevent sensor leaks after VM is destroyed
    }
}