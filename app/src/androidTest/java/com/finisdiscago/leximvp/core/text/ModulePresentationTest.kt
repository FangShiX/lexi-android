package com.finisdiscago.leximvp.core.text

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ModulePresentationTest {
    @Test fun packagedReferenceIntervalsLoadOnAndroid() {
        assertEquals(24, com.finisdiscago.leximvp.core.scheduler.IntervalLookupTable.intervalByDay(0, 5, "1"))
        assertEquals(15, com.finisdiscago.leximvp.core.scheduler.IntervalLookupTable.intervalByDay(1, 5, "1"))
    }
    @Test fun structuredModulesKeepTheirFields() {
        val table = ModulePresentationParser.parse("table", """{"headers":["word","meaning"],"rows":[["cat","猫"],["dog","狗"]]}""") as ModulePresentation.Table
        assertEquals(listOf("word", "meaning"), table.headers)
        assertEquals(listOf("dog", "狗"), table.rows[1])
        val examples = ModulePresentationParser.parse("examples", """[{"en":"A cat.","zh":"一只猫。"}]""") as ModulePresentation.Examples
        assertEquals(ModulePresentation.Example("A cat.", "一只猫。"), examples.values.single())
        val relations = ModulePresentationParser.parse("relations", """[{"target":"dog","type":"related","note":"animal"}]""") as ModulePresentation.Relations
        assertEquals(ModulePresentation.Relation("dog", "related", "animal"), relations.values.single())
    }

    @Test fun extensionPayloadAndPlainTextRemainReadable() {
        assertEquals(ModulePresentation.Text("plain text"), ModulePresentationParser.parse("notes", "plain text"))
        assertEquals(ModulePresentation.Fallback("x：one\ntwo"), ModulePresentationParser.parse("custom", """{"x":["one","two"]}"""))
        assertEquals(emptyList<List<String>>(), (ModulePresentationParser.parse("table", "{}") as ModulePresentation.Table).rows)
        assertEquals(ModulePresentation.Media("content://test/file", "caption"), ModulePresentationParser.parse("media", """{"uri":"content://test/file","caption":"caption"}"""))
    }
}
