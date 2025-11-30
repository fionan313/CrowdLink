package com.fyp.crowdlink.domain.repository

import com.fyp.crowdlink.domain.model.DiscoveredDevice
import com.fyp.crowdlink.domain.model.Friend
import kotlinx.coroutines.flow.StateFlow

interface DeviceRepository {
    val discoveredDevices: StateFlow<List<DiscoveredDevice>>
    fun startDiscovery()
    fun stopDiscovery()
    fun startAdvertising(myDeviceId: String)
    fun stopAdvertising()
    suspend fun getPairedFriends(): List<Friend>
}