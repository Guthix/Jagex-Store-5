/*
GNU LGPL V3
Copyright (C) 2019 Bart van Helvert
B.A.J.v.Helvert@gmail.com

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU Lesser General Public
License as published by the Free Software Foundation; either
version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public License
along with this program; if not, write to the Free Software Foundation,
Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package io.guthix.cache.fs.util

import org.bouncycastle.util.encoders.Hex
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class WhirlpoolTest {
    @ParameterizedTest
    @MethodSource("hashTestArgs")
    fun hashTest(string: String, expededHash: String) {
        val hash1 = whirlPoolHash(string.toByteArray())
        val expecedHash =  Hex.decode(expededHash)
        assert(hash1.contentEquals(expecedHash))
    }

    companion object {
        @JvmStatic
        fun hashTestArgs() = listOf(
            Arguments.of(
                "The quick brown fox jumps over the lazy dog",
                "B97DE512E91E3828B40D2B0FDCE9CEB3C4A71F9BEA8D88E75C4FA854DF36725FD2B52EB6544EDCACD6F8BEDDFEA403CB55AE3"+
                        "1F03AD62A5EF54E42EE82C3FB35"
            ),
            Arguments.of(
                "The quick brown fox jumps over the lazy eog",
                "C27BA124205F72E6847F3E19834F925CC666D0974167AF915BB462420ED40CC50900D85A1F923219D832357750492D5C14301"+
                        "1A76988344C2635E69D06F2D38C"
            ),
            Arguments.of(
                "",
                "19FA61D75522A4669B44E39C1D2E1726C530232130D407F89AFEE0964997F7A73E83BE698B288FEBCF88E3E03C4F0757EA896"+
                        "4E59B63D93708B138CC42A66EB3"
            )
        )
    }
}