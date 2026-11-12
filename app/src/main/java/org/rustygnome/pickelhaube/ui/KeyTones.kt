package org.rustygnome.pickelhaube.ui

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * The keypad's tones, as a phone's dial pad plays them: the key's DTMF tone, briefly, on this phone
 * only – the box dials the number on its own, nothing of this goes into the call. Silent while the
 * phone is set to silent or vibrate. The tone generator is made on the first key and kept until
 * [release].
 */
class KeyTones(context: Context) {

    private val audio = context.getSystemService(AudioManager::class.java)
    private var generator: ToneGenerator? = null

    fun play(key: Char) {
        val tone = toneFor(key) ?: return
        if (audio?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val player = generator ?: runCatching { ToneGenerator(AudioManager.STREAM_DTMF, VOLUME) }.getOrNull()
            ?.also { generator = it } ?: return
        player.startTone(tone, DURATION_MILLIS)
    }

    fun release() {
        generator?.release()
        generator = null
    }

    companion object {
        /** Of [ToneGenerator.MAX_VOLUME] (100): as loud as a phone's dial pad, not more. */
        private const val VOLUME = 80
        private const val DURATION_MILLIS = 120

        /** The DTMF tone of a key of the keypad; null for anything else. */
        fun toneFor(key: Char): Int? = when (key) {
            '0' -> ToneGenerator.TONE_DTMF_0
            '1' -> ToneGenerator.TONE_DTMF_1
            '2' -> ToneGenerator.TONE_DTMF_2
            '3' -> ToneGenerator.TONE_DTMF_3
            '4' -> ToneGenerator.TONE_DTMF_4
            '5' -> ToneGenerator.TONE_DTMF_5
            '6' -> ToneGenerator.TONE_DTMF_6
            '7' -> ToneGenerator.TONE_DTMF_7
            '8' -> ToneGenerator.TONE_DTMF_8
            '9' -> ToneGenerator.TONE_DTMF_9
            '*' -> ToneGenerator.TONE_DTMF_S
            '#' -> ToneGenerator.TONE_DTMF_P
            else -> null
        }
    }
}

/** The keypad's tones while [enabled] (switched on in the settings); null otherwise. */
@Composable
fun rememberKeyTones(enabled: Boolean): KeyTones? {
    val context = LocalContext.current
    val tones = remember(enabled) { if (enabled) KeyTones(context) else null }
    DisposableEffect(tones) { onDispose { tones?.release() } }
    return tones
}
