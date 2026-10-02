package app.pony.companion.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class BasicsTest {
    @Test
    fun theOnboardingTryOpensTheCalculatorAndAddsTwoPlusTwo() {
        for (text in listOf(
            "Try: open Calculator and add 2+2",
            "open the calculator and add 2 plus 2",
            "Open calculator and then add two plus two.",
            "add 2 and 2 in the calculator",
            "hey pony, open the calculator and add 2 + 2 please",
        )) {
            val skills = Basics.parse(text)
            assertEquals(text, 2, skills?.size)
            assertEquals(text, Skill.OpenApp(Basics.CALCULATOR), skills!![0])
            val calc = skills[1] as Skill.Calculate
            assertEquals(text, "2 + 2", calc.expression)
            assertEquals(text, "4", Basics.format(calc.answer!!))
        }
    }

    @Test
    fun calculatorKeysClearThenTypeTheExpressionThenEquals() {
        val calc = Basics.parse("what's 12 times 7")!!.last() as Skill.Calculate
        assertEquals(listOf("C", "1", "2", "×", "7", "="), calc.keys.map { it.label })
        assertEquals("84", Basics.format(calc.answer!!))
    }

    @Test
    fun arithmeticFollowsOperatorPrecedenceAndDecimals() {
        assertEquals("14", answer("calculate 2 + 3 * 4"))
        assertEquals("5", answer("10 minus 2 minus 3"))
        assertEquals("10", answer("2.5 times 4"))
        assertEquals("25", answer("100 divided by 4"))
        assertEquals("7", answer("subtract 3 from 10"))
        assertEquals("0.3", answer("0.1 plus 0.2"))
        assertEquals("125", answer("add one hundred and twenty five"))
        assertEquals("9", answer("add 2 plus 3 plus 4"))
        assertNull((Basics.parse("divide 1 by 0")!!.last() as Skill.Calculate).answer)
    }

    @Test
    fun opensAppsByName() {
        assertEquals(listOf(Skill.OpenApp("settings")), Basics.parse("open settings"))
        assertEquals(listOf(Skill.OpenApp("camera")), Basics.parse("Launch the camera app"))
        assertEquals(listOf(Skill.OpenApp("photos")), Basics.parse("show me my photos"))
    }

    @Test
    fun timersHomeAndBack() {
        assertEquals(listOf(Skill.SetTimer(300)), Basics.parse("set a timer for 5 minutes"))
        assertEquals(listOf(Skill.SetTimer(600)), Basics.parse("Set a 10-minute timer"))
        assertEquals(listOf(Skill.SetTimer(90)), Basics.parse("start a timer for ninety seconds"))
        assertEquals(listOf(Skill.GoHome), Basics.parse("go to the home screen"))
        assertEquals(listOf(Skill.GoBack), Basics.parse("go back"))
        assertEquals(
            listOf(Skill.SetTimer(60), Skill.OpenApp(Basics.CALCULATOR)),
            Basics.parse("set a timer for 1 minute and then open the calculator"),
        )
    }

    @Test
    fun basicsDeclinesAnythingItCannotFinish() {
        assertNull(Basics.parse("send a message to mom"))
        assertNull(Basics.parse("open whatsapp and send hi to mom"))
        assertNull(Basics.parse("what is the weather tomorrow"))
        assertNull(Basics.parse("open 2 apps"))
        assertNull(Basics.parse(""))
        assertTrue(Basics.canHandle("open the calculator"))
    }

    @Test
    fun ownerSetupAsksGoToTheBrainNotBasics() {
        // These three are the owner's failing tests. Basics must not grab them
        // (and must never turn them into "open Messages") — they belong to the
        // on-phone brain, which the system prompt steers to Settings search.
        assertNull(Basics.parse("When I type there are lots of typos and autocorrect isn't good. Can you fix this?"))
        assertNull(Basics.parse("I want to dictate in Hebrew and have the keyboard type Hebrew text; set that up for me."))
        assertNull(Basics.parse("I want to speak in Hebrew and have my phone text it in Hebrew."))
    }

    @Test
    fun numberWordsBecomeDigits() {
        assertEquals("add 2 plus 2", Basics.normalize("Add two plus two."))
        assertEquals("set a timer for 25 minutes", Basics.normalize("set a timer for twenty five minutes"))
        assertEquals(BigDecimal("1200"), (Basics.parse("what is 1,200 plus 0")!!.last() as Skill.Calculate).numbers.first())
    }

    private fun answer(text: String): String {
        val calc = Basics.parse(text)!!.last() as Skill.Calculate
        return Basics.format(calc.answer!!)
    }
}
