package com.fyp.crowdlink.data.mesh

import com.fyp.crowdlink.domain.model.DeviceLocation
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LocationMessageSerialiser
 *
 * Serialises and deserialises [DeviceLocation] objects into a compact 29-byte binary format
 * for transmission over the BLE mesh. Using a fixed binary layout rather than JSON saves
 * roughly 60 bytes per packet, which matters given the 512-byte MTU ceiling.
 *
 * Wire format: type(1) + lat(8) + lon(8) + accuracy(4) + timestamp(8) = 29 bytes
 */
@Singleton
class LocationMessageSerialiser @Inject constructor() {

    /**
     * Packs a [DeviceLocation] into a 29-byte array.
     * The 0x03 type prefix identifies this as a location update post-decryption,
     * distinguishing it from a regular chat message (0x01).
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
     * Unpacks a 29-byte array back into a [DeviceLocation].
     * Returns null if the payload is too short or the type byte doesn't match,
     * preventing a malformed packet from propagating up the stack.
     */
    fun deserialize(bytes: ByteArray, deviceId: String): DeviceLocation? {
        if (bytes.size < 29 || bytes[0] != 0x03.toByte()) return null

        return try {
            val buffer = ByteBuffer.wrap(bytes)
            buffer.get() // skip type byte
            DeviceLocation(
                deviceId = deviceId,
                latitude = buffer.double,
                longitude = buffer.double,
                accuracy = buffer.float,
                timestamp = buffer.long
            )
        } catch (_: Exception) {
            null
        }
    }
}