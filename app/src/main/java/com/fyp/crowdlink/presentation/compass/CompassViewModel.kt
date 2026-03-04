package com.fyp.crowdlink.presentation.compass

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fyp.crowdlink.domain.model.DeviceLocation
import com.fyp.crowdlink.domain.repository.FriendRepository
import com.fyp.crowdlink.domain.repository.LocationRepository
import com.fyp.crowdlink.domain.usecase.EstimateDistanceUseCase
import com.fyp.crowdlink.domain.usecase.ShareLocationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CompassViewModel @Inject constructor(
    private val locationRepository: LocationRepository,
    private val friendRepository: FriendRepository,
    private val sensorManager: SensorManager,
    private val estimateDistanceUseCase: EstimateDistanceUseCase,
    private val shareLocationUseCase: ShareLocationUseCase
) : ViewModel(), SensorEventListener {

    private val _myLocation = locationRepository.getMyLocation().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), null
    )
    val myLocation: StateFlow<DeviceLocation?> = _myLocation

    private var currentFriendId: String = ""
    private val _friendLocation = MutableStateFlow<DeviceLocation?>(null)
    val friendLocation: StateFlow<DeviceLocation?> = _friendLocation.asStateFlow()

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

    private var gravity: FloatArray? = null
    private var geomagnetic: FloatArray? = null

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
                val friendAndroidLoc = Location("").apply {
                    latitude = friendLoc.latitude
                    longitude = friendLoc.longitude
                }
                _bearingToFriend.value = myAndroidLoc.bearingTo(friendAndroidLoc)

                val isStale = System.currentTimeMillis() - friendLoc.timestamp > 60000 // 60s stale threshold
                _isGpsAvailable.value = myLoc.accuracy < 50f && !isStale
            } else {
                _distanceMetres.value = null
                _bearingToFriend.value = null
                _isGpsAvailable.value = false
            }
        }.launchIn(viewModelScope)
    }

    fun setFriendId(friendId: String) {
        currentFriendId = friendId
        viewModelScope.launch {
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
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) gravity = event.values
        if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) geomagnetic = event.values

        if (gravity != null && geomagnetic != null) {
            val r = FloatArray(9)
            val i = FloatArray(9)
            if (SensorManager.getRotationMatrix(r, i, gravity, geomagnetic)) {
                val orientation = FloatArray(3)
                SensorManager.getOrientation(r, orientation)
                val azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
                _compassHeading.value = (azimuth + 360) % 360
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onCleared() {
        super.onCleared()
        sensorManager.unregisterListener(this)
    }
}
