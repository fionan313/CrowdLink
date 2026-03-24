package com.fyp.crowdlink.data.mesh

import com.fyp.crowdlink.domain.model.DeviceLocation
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationMessageSerialiser @Inject constructor() {

    /**
     * Serializes a DeviceLocation into a ByteArray.
     * Format: type(1) + lat(8) + lon(8) + accuracy(4) + timestamp(8) = 29 bytes
     */
    fun serialize(location: DeviceLocation): ByteArray {
        val buffer = ByteBuffer.allocate(29)
        buffer.put(0x03.toByte())
        buffer.putDouble(location.latitude)
        buffer.putDouble(location.longitude)
        buffer.putFloat(location.accuracy)
        buffer.putLong(location.timestamp)
        return buffer.array()
    }

    /**
     * Deserializes a ByteArray into a DeviceLocation.
     */
    fun deserialize(bytes: ByteArray, deviceId: String): DeviceLocation? {
        if (bytes.size < 29 || bytes[0] != 0x03.toByte()) return null
        
        return try {
            val buffer = ByteBuffer.wrap(bytes)
            buffer.get() // Skip type byte
            val lat = buffer.double
            val lon = buffer.double
            val accuracy = buffer.float
            val timestamp = buffer.long
            
            DeviceLocation(
                deviceId = deviceId,
                latitude = lat,
                longitude = lon,
                accuracy = accuracy,
                timestamp = timestamp
            )
        } catch (_: Exception) {
            null
        }
    }
}
