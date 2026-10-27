package com.example

import android.net.Uri
import com.example.util.SharedLocationParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SharedLocationParserTest {

    @Test
    fun testParseGeoDirectCoordinates() {
        val uri = Uri.parse("geo:-6.208800,106.845600")
        val parsed = SharedLocationParser.parseUri(uri)
        assertNotNull(parsed)
        assertEquals(-6.208800, parsed!!.latitude, 0.0001)
        assertEquals(106.845600, parsed.longitude, 0.0001)
    }

    @Test
    fun testParseGeoQueryWithLabel() {
        val uri = Uri.parse("geo:0,0?q=-6.208800,106.845600(Monumen+Nasional)")
        val parsed = SharedLocationParser.parseUri(uri)
        assertNotNull(parsed)
        assertEquals(-6.208800, parsed!!.latitude, 0.0001)
        assertEquals(106.845600, parsed.longitude, 0.0001)
        assertEquals("Monumen Nasional", parsed.label)
    }

    @Test
    fun testParseGoogleMapsUrl() {
        val uri = Uri.parse("https://maps.google.com/?q=-7.253427,112.707224")
        val parsed = SharedLocationParser.parseUri(uri)
        assertNotNull(parsed)
        assertEquals(-7.253427, parsed!!.latitude, 0.0001)
        assertEquals(112.707224, parsed.longitude, 0.0001)
    }

    @Test
    fun testParseGoogleMapsAtCoordinates() {
        val uri = Uri.parse("https://www.google.com/maps/@-6.175392,106.827153,17z")
        val parsed = SharedLocationParser.parseUri(uri)
        assertNotNull(parsed)
        assertEquals(-6.175392, parsed!!.latitude, 0.0001)
        assertEquals(106.827153, parsed.longitude, 0.0001)
    }

    @Test
    fun testParseSharedTextWithCoordinates() = runBlocking {
        val text = "Halo, ini lokasiku saat ini: -6.195400, 106.823500 silakan mampir"
        val parsed = SharedLocationParser.parseText(text)
        assertNotNull(parsed)
        assertEquals(-6.195400, parsed!!.latitude, 0.0001)
        assertEquals(106.823500, parsed.longitude, 0.0001)
    }
}
