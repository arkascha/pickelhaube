package org.rustygnome.pickelhaube

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.rustygnome.pickelhaube.tile.TileDeflection
import org.rustygnome.pickelhaube.tr064.DemoBox

/**
 * The call forwarding the quick settings tile and the launcher shortcut switch, on the demo box: read,
 * switched and switched back – and none to read while no box answers.
 */
@RunWith(AndroidJUnit4::class)
class TileDeflectionTest : FlowTest() {

    @After
    fun answerAgain() {
        DemoBox.answering = true
    }

    @Test
    fun readSwitchedAndBack() = runBlocking {
        setUpDemoBox()
        awaitBoxIdle()
        val found = TileDeflection.read(context) as TileDeflection.Reading.Found
        val before = found.deflection.enabled

        val switched = TileDeflection.switch(context, found.box, found.deflection)!!
        assertEquals(found.deflection.id, switched.id)
        assertEquals(!before, switched.enabled)
        // Read anew, as the box has it now.
        assertEquals(!before, (TileDeflection.read(context) as TileDeflection.Reading.Found).deflection.enabled)

        val back = TileDeflection.switch(context, found.box, switched)!!
        assertEquals(before, back.enabled)
    }

    @Test
    fun noBoxWhileNoneAnswers() = runBlocking {
        setUpDemoBox()
        awaitBoxIdle()
        DemoBox.answering = false
        assertTrue(TileDeflection.read(context) is TileDeflection.Reading.NoBox)
    }
}
