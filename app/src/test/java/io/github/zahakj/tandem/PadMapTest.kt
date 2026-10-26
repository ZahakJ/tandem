package io.github.zahakj.tandem

import io.github.zahakj.tandem.PadMap.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Controller passthrough's translation table (PadMap.kt): an external pad's codes to the built-in controller's. */
class PadMapTest {
    /** The AYN Thor's built-in "Odin Controller" (Vendor_2020_Product_0111.kl, trimmed). */
    private val odin = KeyLayout.parse(
        """
        key 0x66  HOME
        #key 0x72  BACK
        key 0x9e  BACK
        key 0x244   APP_SWITCH
        key 0x72  VOLUME_DOWN
        key 0x73  VOLUME_UP
        key 0x220 DPAD_UP
        key 0x222 DPAD_LEFT
        key 0x223 DPAD_RIGHT
        key 0x221 DPAD_DOWN
        key 0x130 BUTTON_A
        key 0x131 BUTTON_B
        key 0x132 BUTTON_C
        key 0x133 BUTTON_X
        key 0x134 BUTTON_Y
        key 0x135 BUTTON_Z
        key 0x136 BUTTON_L1
        key 0x137 BUTTON_R1
        key 0x138 BUTTON_L2
        key 0x139 BUTTON_R2
        key 0x13d BUTTON_THUMBL
        key 0x13e BUTTON_THUMBR
        key 0x13a BUTTON_SELECT
        key 0x13b BUTTON_START
        key 0x13c BUTTON_MODE
        axis 0x00 X
        axis 0x01 Y
        axis 0x02 Z
        axis 0x03 RX
        axis 0x04 RY
        axis 0x05 RZ
        axis 0x09 GAS
        axis 0x0a BRAKE
        axis 0x10 HAT_X
        axis 0x11 HAT_Y
        """.trimIndent(),
    )

    /** An Xbox Wireless Controller over Bluetooth (Vendor_045e_Product_02fd.kl as Android ships it). */
    private val xbox = KeyLayout.parse(
        """
        # Xbox One S over Bluetooth
        key 304   BUTTON_A
        key 305   BUTTON_B
        key 307   BUTTON_X
        key 308   BUTTON_Y
        key 310   BUTTON_L1
        key 311   BUTTON_R1
        axis 0x0a LTRIGGER
        axis 0x09 RTRIGGER
        axis 0x00 X flat 4096
        axis 0x01 Y flat 4096
        axis 0x02 Z flat 4096
        axis 0x05 RZ flat 4096
        key 317   BUTTON_THUMBL
        key 318   BUTTON_THUMBR
        axis 0x10 HAT_X
        axis 0x11 HAT_Y
        key 158   BUTTON_SELECT
        key 315   BUTTON_START
        key 172   BUTTON_MODE
        key 316   BUTTON_MODE
        """.trimIndent(),
    )

    @Test fun parsesKeyLayouts() {
        assertEquals("BUTTON_A", odin.keys[0x130])
        assertEquals("BACK", odin.keys[0x9e])
        assertEquals(null, odin.keys[0x72]?.takeIf { it == "BACK" }) // a commented-out line doesn't count
        assertEquals(KeyLayout.Axis("X", flat = 4096), xbox.axes[0])
        val odd = KeyLayout.parse("axis 0x01 invert Y\naxis 0x02 split 0x7f LTRIGGER RTRIGGER\nkey usage 0x0c0067 POWER\nkey 1 ESCAPE")
        assertEquals(KeyLayout.Axis("Y", invert = true), odd.axes[1])
        assertEquals(null, odd.axes[2])
        assertEquals(mapOf(1 to "ESCAPE"), odd.keys)
    }

    @Test fun xboxToTheBuiltInController() {
        val rules = PadMap.build(xbox, odin).toSet()
        val expected = setOf(
            // Sticks keep their dead zone; the triggers land on BRAKE / GAS and press L2 / R2 too.
            Rule.Axis(0, 0, flat = 4096), Rule.Axis(1, 1, flat = 4096), Rule.Axis(2, 2, flat = 4096), Rule.Axis(5, 5, flat = 4096),
            Rule.Axis(10, 10), Rule.TriggerKey(10, 0x138), Rule.Axis(9, 9), Rule.TriggerKey(9, 0x139),
            Rule.Axis(16, 16), Rule.Axis(17, 17),
            Rule.Key(304, 0x130), Rule.Key(305, 0x131), Rule.Key(307, 0x133), Rule.Key(308, 0x134),
            Rule.Key(310, 0x136), Rule.Key(311, 0x137), Rule.Key(317, 0x13d), Rule.Key(318, 0x13e),
            Rule.Key(158, 0x13a), Rule.Key(315, 0x13b),
            // The guide button is the handheld's Home.
            Rule.Key(172, 0x66), Rule.Key(316, 0x66),
        )
        assertEquals(expected, rules)
        assertEquals(listOf(304, 315), PadMap.acceptKeys(xbox))
    }

    @Test fun nintendoLabelsSwapFaceButtons() {
        val rules = PadMap.build(xbox, odin, nintendo = true)
        assertTrue(Rule.Key(304, 0x131) in rules)
        assertTrue(Rule.Key(305, 0x130) in rules)
        assertTrue(Rule.Key(307, 0x134) in rules)
        assertTrue(Rule.Key(308, 0x133) in rules)
        // The button printed A (which sends B) accepts.
        assertEquals(listOf(305, 315), PadMap.acceptKeys(xbox, nintendo = true))
    }

    @Test fun genericPadBackIsSelectAndRightStickOnRxRy() {
        val pad = KeyLayout.parse(
            "key 158 BACK\nkey 172 HOME\nkey 312 BUTTON_L2\nkey 313 BUTTON_R2\nkey 114 VOLUME_DOWN\nkey 30 A\n" +
                "axis 0x03 RX\naxis 0x04 RY\naxis 0x01 invert Y",
        )
        val target = KeyLayout.parse("key 0x9e BACK\nkey 0x66 HOME\nkey 0x13a BUTTON_SELECT\nkey 0x138 BUTTON_L2\nkey 0x139 BUTTON_R2\nkey 0x72 VOLUME_DOWN\n" +
            "axis 0x01 Y\naxis 0x02 Z\naxis 0x05 RZ\naxis 0x09 GAS\naxis 0x0a BRAKE")
        val rules = PadMap.build(pad, target).toSet()
        assertEquals(
            setOf(
                Rule.Key(158, 0x13a), Rule.Key(172, 0x66),
                // Digital triggers pull the analog ones all the way too.
                Rule.Key(312, 0x138), Rule.KeyAxis(312, 0x0a), Rule.Key(313, 0x139), Rule.KeyAxis(313, 0x09),
                // RX / RY find the target's Z / RZ; an inverted axis stays inverted.
                Rule.Axis(3, 2), Rule.Axis(4, 5), Rule.Axis(1, 1, invert = true),
            ),
            rules,
        )
    }

    @Test fun ruleArguments() {
        assertEquals("k304:305", Rule.Key(304, 305).arg)
        assertEquals("a0:0:f4096", Rule.Axis(0, 0, flat = 4096).arg)
        assertEquals("a1:1:i", Rule.Axis(1, 1, invert = true).arg)
        assertEquals("t10:312", Rule.TriggerKey(10, 312).arg)
        assertEquals("b312:10", Rule.KeyAxis(312, 10).arg)
    }

    @Test fun findsInputNodes() {
        val dump = """
            Input Manager State:
              Interactive: true
            Event Hub State:
              BuiltInKeyboardId: -2
              Devices:
                10: ODIN Station Virtual Mouse
                  Classes: CURSOR_ICON | EXTERNAL
                  Path: /dev/input/event10
                  Descriptor: 215005827afddf4e97a68b3679bcdfdf7635af91
                  KeyLayoutFile: 
                9: Odin Controller
                  Classes: KEYBOARD | GAMEPAD | JOYSTICK | EXTERNAL
                  Path: /dev/input/event9
                  Enabled: true
                  Descriptor: 8e1073ea5832500672194344d81498833991c43c
                  Identifier: bus=0x0003, vendor=0x2020, product=0x0111, version=0x0000
                  KeyLayoutFile: /system/usr/keylayout/Vendor_2020_Product_0111.kl
                12: Xbox Wireless Controller
                  Path: /dev/input/event11
                  Descriptor: 1a2b
                  KeyLayoutFile: /system/usr/keylayout/Generic.kl
            Input Reader State:
              Device 10: Odin Controller
                Path: /dev/input/nothing
        """.trimIndent()
        assertEquals(
            listOf(
                InputNodes.Node("ODIN Station Virtual Mouse", "/dev/input/event10", "215005827afddf4e97a68b3679bcdfdf7635af91", null),
                InputNodes.Node("Odin Controller", "/dev/input/event9", "8e1073ea5832500672194344d81498833991c43c", "/system/usr/keylayout/Vendor_2020_Product_0111.kl"),
                InputNodes.Node("Xbox Wireless Controller", "/dev/input/event11", "1a2b", "/system/usr/keylayout/Generic.kl"),
            ),
            InputNodes.parse(dump),
        )
    }
}
