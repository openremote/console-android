import io.openremote.app.util.UrlUtils
import org.junit.Test
import org.junit.Assert.assertEquals

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest=Config.NONE)
class UrlUtilsTest {

    @Test
    fun fqdnWithScheme() {
        assertEquals("http://www.example.com", UrlUtils.hostToUrl("http://www.example.com"))
        assertEquals("https://www.example.com", UrlUtils.hostToUrl("https://www.example.com"))
    }

    @Test
    fun fqdnWithNonWebScheme() {
        assertEquals("ftp://www.example.com", UrlUtils.hostToUrl("ftp://www.example.com"))
    }

    @Test
    fun fqdnNoScheme() {
        assertEquals("https://www.example.com", UrlUtils.hostToUrl("www.example.com"))
    }

    @Test
    fun fqdnAndPortWithScheme() {
        assertEquals("http://www.example.com:8080", UrlUtils.hostToUrl("http://www.example.com:8080"))
        assertEquals("https://www.example.com:443", UrlUtils.hostToUrl("https://www.example.com:443"))
    }

    @Test
    fun fqdnAndPortWithNonWebScheme() {
        assertEquals("ftp://www.example.com:21", UrlUtils.hostToUrl("ftp://www.example.com:21"))
    }

    @Test
    fun fqdnAndPortNoScheme() {
        assertEquals("https://www.example.com:8080", UrlUtils.hostToUrl("www.example.com:8080"))
    }

    @Test
    fun hostnameNoScheme() {
        assertEquals("https://example.openremote.app", UrlUtils.hostToUrl("example"))
    }

    @Test
    fun ipAddressWithScheme() {
        assertEquals("http://192.168.1.1", UrlUtils.hostToUrl("http://192.168.1.1"))
    }

    @Test
    fun ipAddressWithNonWebScheme() {
        assertEquals("ftp://192.168.1.1", UrlUtils.hostToUrl("ftp://192.168.1.1"))
    }

    @Test
    fun ipAddressAndPortWithScheme() {
        assertEquals("http://192.168.1.1:8080", UrlUtils.hostToUrl("http://192.168.1.1:8080"))
    }

    @Test
    fun ipAddressAndPortWithNonWebScheme() {
        assertEquals("ftp://192.168.1.1:25", UrlUtils.hostToUrl("ftp://192.168.1.1:25"))
    }

    @Test
    fun ipAddressAndInvalidPortWithScheme() {
        assertEquals("http://192.168.1.1:InvalidPort", UrlUtils.hostToUrl("http://192.168.1.1:InvalidPort"))
    }

    @Test
    fun ipAddressNoScheme() {
        assertEquals("https://192.168.1.1", UrlUtils.hostToUrl("192.168.1.1"))
    }

    @Test
    fun ipAddressAndPortNoScheme() {
        assertEquals("https://192.168.1.1:8080", UrlUtils.hostToUrl("192.168.1.1:8080"))
    }

    @Test
    fun ipv6AddressWithScheme() {
        assertEquals("http://[2001:0db8:85a3:0000:0000:8a2e:0370:7334]",
            UrlUtils.hostToUrl("http://[2001:0db8:85a3:0000:0000:8a2e:0370:7334]"))
    }

    @Test
    fun ipv6AddressAndPortWithScheme() {
        assertEquals("http://[2001:0db8:85a3:0000:0000:8a2e:0370:7334]:8080",
            UrlUtils.hostToUrl("http://[2001:0db8:85a3:0000:0000:8a2e:0370:7334]:8080"))
    }

    @Test
    fun ipv6AddressNoScheme() {
        assertEquals("https://[2001:0db8:85a3:0000:0000:8a2e:0370:7334]",
            UrlUtils.hostToUrl("2001:0db8:85a3:0000:0000:8a2e:0370:7334"))
        assertEquals("https://[2001:0db8:85a3:0000:0000:8a2e:0370:7334]",
            UrlUtils.hostToUrl("[2001:0db8:85a3:0000:0000:8a2e:0370:7334]"))
    }

    @Test
    fun ipv6AddressAndPortNoScheme() {
        assertEquals("https://[2001:0db8:85a3:0000:0000:8a2e:0370:7334]:8080",
            UrlUtils.hostToUrl("[2001:0db8:85a3:0000:0000:8a2e:0370:7334]:8080"))
    }

    @Test
    fun ipv6CompressedAddressWithScheme() {
        assertEquals("http://[2001:db8:85a3::8a2e:370:7334]",
            UrlUtils.hostToUrl("http://[2001:db8:85a3::8a2e:370:7334]"))
    }

    @Test
    fun ipv6CompressedAddressAndPortWithScheme() {
        assertEquals("http://[2001:db8:85a3::8a2e:370:7334]:8080",
            UrlUtils.hostToUrl("http://[2001:db8:85a3::8a2e:370:7334]:8080"))
    }

    @Test
    fun ipv6CompressedAddressNoScheme() {
        assertEquals("https://[2001:db8:85a3::8a2e:370:7334]",
            UrlUtils.hostToUrl("2001:db8:85a3::8a2e:370:7334"))
        assertEquals("https://[2001:db8:85a3::8a2e:370:7334]",
            UrlUtils.hostToUrl("[2001:db8:85a3::8a2e:370:7334]"))
    }

    @Test
    fun ipv6CompressedAddressAndPortNoScheme() {
        assertEquals("https://[2001:db8:85a3::8a2e:370:7334]:8080",
            UrlUtils.hostToUrl("[2001:db8:85a3::8a2e:370:7334]:8080"))
    }
}