/*
 * WiFiAnalyzer
 * Copyright (C) 2015 - 2023 VREM Software Development <VREMSoftwareDevelopment@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */
package com.vrem.wifianalyzer.wifi.scanner

import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import com.vrem.annotation.OpenClass
import com.vrem.util.EMPTY
import com.vrem.util.buildMinVersionR
import com.vrem.util.ssid
import com.vrem.wifianalyzer.wifi.model.WiFiConnection
import com.vrem.wifianalyzer.wifi.model.WiFiData
import com.vrem.wifianalyzer.wifi.model.WiFiDetail
import com.vrem.wifianalyzer.wifi.model.WiFiIdentifier
import com.vrem.wifianalyzer.wifi.model.WiFiSignal
import com.vrem.wifianalyzer.wifi.model.WiFiStandard
import com.vrem.wifianalyzer.wifi.model.WiFiStandardId
import com.vrem.wifianalyzer.wifi.model.WiFiWidth
import com.vrem.wifianalyzer.wifi.model.convertIpV4Address
import com.vrem.wifianalyzer.wifi.model.convertSSID

@Suppress("DEPRECATION")
fun WifiInfo.ipV4Address(): Int = ipAddress

@OpenClass
internal class Transformer(private val cache: Cache) {

    internal fun transformWifiInfo(): WiFiConnection {
        val wifiInfo: WifiInfo? = cache.wifiInfo
        val dhcpInfo = cache.dhcpInfo
        return if (wifiInfo == null || wifiInfo.networkId == -1) {
            WiFiConnection.EMPTY
        } else {
            val ssid = convertSSID(wifiInfo.ssid ?: String.EMPTY)
            val wiFiIdentifier =
                WiFiIdentifier(ssid, wifiInfo.bssid ?: String.EMPTY)
            val ipAddress = if (dhcpInfo != null) convertIpV4Address(dhcpInfo.ipAddress) else ""
            val gateIpAddress = if (dhcpInfo != null) convertIpV4Address(dhcpInfo.gateway) else ""
            WiFiConnection(
                wiFiIdentifier,
                "$ipAddress $gateIpAddress",
                wifiInfo.linkSpeed
            )
        }
    }

    internal fun transformCacheResults(): List<WiFiDetail> =
        cache.scanResults().map { transform(it) }

    internal fun transformToWiFiData(): WiFiData =
        WiFiData(transformCacheResults(), transformWifiInfo())

    internal fun wiFiStandard(scanResult: ScanResult): WiFiStandardId =
        if (minVersionR()) {
            scanResult.wifiStandard
        } else {
            WiFiStandard.UNKNOWN.wiFiStandardId
        }

    internal fun minVersionR(): Boolean = buildMinVersionR()

    private fun transform(cacheResult: CacheResult): WiFiDetail {
        val scanResult = cacheResult.scanResult
        val wiFiWidth = WiFiWidth.findOne(scanResult.channelWidth)
        val centerFrequency =
            wiFiWidth.calculateCenter(scanResult.frequency, scanResult.centerFreq0)
        val mc80211 = scanResult.is80211mcResponder
        val wiFiStandard = WiFiStandard.findOne(wiFiStandard(scanResult))
        val wiFiSignal = WiFiSignal(
            scanResult.frequency, centerFrequency, wiFiWidth,
            cacheResult.average, mc80211, wiFiStandard, scanResult.timestamp
        )
        val wiFiIdentifier = WiFiIdentifier(
            scanResult.ssid(),
            if (scanResult.BSSID == null) String.EMPTY else scanResult.BSSID
        )
        return WiFiDetail(
            wiFiIdentifier,
            if (scanResult.capabilities == null) String.EMPTY else scanResult.capabilities,
            wiFiSignal
        )
    }

}