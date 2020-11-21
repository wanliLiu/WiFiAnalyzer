/*
 * WiFiAnalyzer
 * Copyright (C) 2015 - 2020 VREM Software Development <VREMSoftwareDevelopment@gmail.com>
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
package com.vrem.wifianalyzer.wifi.band

import java.util.*

private class Rules(val countries: SortedSet<String>, val channels: SortedSet<Int>) {
    fun find(countryCode: String): SortedSet<Int> =
            if (countries.any { it == countryCode }) {
                channels
            } else sortedSetOf()
}

private val countriesETSI: SortedSet<String> = sortedSetOf(
        "AT",      // ETSI Austria
        "BE",      // ETSI Belgium
        "CH",      // ETSI Switzerland
        "CY",      // ETSI Cyprus
        "CZ",      // ETSI Czechia
        "DE",      // ETSI Germany
        "DK",      // ETSI Denmark
        "EE",      // ETSI Estonia
        "ES",      // ETSI Spain
        "FI",      // ETSI Finland
        "FR",      // ETSI France
        "GR",      // ETSI Greece
        "HU",      // ETSI Hungary
        "IE",      // ETSI Ireland
        "IS",      // ETSI Iceland
        "IT",      // ETSI Italy
        "LI",      // ETSI Liechtenstein
        "LT",      // ETSI Lithuania
        "LU",      // ETSI Luxembourg
        "LV",      // ETSI Latvia
        "MT",      // ETSI Malta
        "NL",      // ETSI Netherlands
        "NO",      // ETSI Norway
        "PL",      // ETSI Poland
        "PT",      // ETSI Portugal
        "RO",      // ETSI Romania
        "SE",      // ETSI Sweden
        "SI",      // ETSI Slovenia
        "SK"       // ETSI Slovakia
)

internal class WiFiChannelCountryGHZ5 {
    private val channelsSet1: SortedSet<Int> = sortedSetOf(36, 40, 44, 48, 52, 56, 60, 64)
    private val channelsSet2: SortedSet<Int> = sortedSetOf(100, 104, 108, 112, 116, 120, 124, 128, 132, 136, 140, 144)
    private val channelsSet3: SortedSet<Int> = sortedSetOf(149, 153, 157, 161, 165)
    private val channels: SortedSet<Int> = channelsSet1.union(channelsSet2).union(channelsSet3).toSortedSet()

    private val exclude: List<Rules> = listOf(
            Rules(sortedSetOf("AU", "CA"), sortedSetOf(120, 124, 128)),
            Rules(sortedSetOf("RU"), sortedSetOf(100, 104, 108, 112, 116, 120, 124, 128)),
            Rules(countriesETSI, sortedSetOf(144)),
            Rules(sortedSetOf("IL"), channelsSet2.union(channelsSet3).toSortedSet()),
            Rules(sortedSetOf("CN", "KR"), channelsSet2),
            Rules(sortedSetOf("JP", "TR", "ZA"), channelsSet3))

    private val include: Rules = Rules(countriesETSI, sortedSetOf(169, 173))

    fun findChannels(countryCode: String): SortedSet<Int> =
            channels.subtract(exclude.flatMap { it.find(countryCode.capitalize(Locale.getDefault())) })
                    .union(include.find(countryCode.capitalize(Locale.getDefault())))
                    .toSortedSet()

}
