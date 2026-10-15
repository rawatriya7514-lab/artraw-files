package com.artraw.files

import com.artraw.files.monitor.AppUsageHelper
import com.artraw.files.monitor.CpuCoreInfo
import com.artraw.files.monitor.DeviceHardwareHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceMonitorTest {

    @Test
    fun testCpuSnapshotAvailable() {
        val cpu = DeviceHardwareHelper.getCpuSnapshot()
        assertNotNull("CPU snapshot must not be null", cpu)
        assertTrue("Core count must be at least 1", cpu.coreCount >= 1)
        assertNotNull("Governor should be resolved", cpu.governor)
        assertTrue("Core list size should match coreCount", cpu.cores.size == cpu.coreCount)
    }

    @Test
    fun testCpuCoreInfoPercentage() {
        val core = CpuCoreInfo(coreIndex = 0, currentFreqKHz = 1800000L, minFreqKHz = 600000L, maxFreqKHz = 2400000L)
        assertEquals(1800L, core.currentFreqMhz)
        assertEquals(2400L, core.maxFreqMhz)
        assertEquals(75, core.percent)
    }

    @Test
    fun testAppUsageFormatDuration() {
        assertEquals("2h 15m", AppUsageHelper.formatDuration(2 * 3600 * 1000L + 15 * 60 * 1000L))
        assertEquals("45m", AppUsageHelper.formatDuration(45 * 60 * 1000L))
        assertEquals("3h", AppUsageHelper.formatDuration(3 * 3600 * 1000L))
        assertEquals("< 1m", AppUsageHelper.formatDuration(20 * 1000L))
    }

    @Test
    fun testActivityRegisteredInManifest() {
        val manifest = java.io.File("AndroidManifest.xml").readText()
        assertTrue("DeviceMonitorActivity must be declared in AndroidManifest.xml", manifest.contains(".ui.DeviceMonitorActivity"))
        assertTrue("PACKAGE_USAGE_STATS permission must be declared in AndroidManifest.xml", manifest.contains("android.permission.PACKAGE_USAGE_STATS"))
    }
}
