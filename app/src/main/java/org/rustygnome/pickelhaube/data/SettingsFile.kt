package org.rustygnome.pickelhaube.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** The app options a settings file carries besides the connections. */
data class AppOptions(
    val deviceContactsEnabled: Boolean,
    val callLogEnabled: Boolean,
    val phoneSpeedDialsEnabled: Boolean,
    val phoneSpeedDials: List<SpeedDial>,
    /** Follow the calls placed on the box' call monitor. */
    val callStatusEnabled: Boolean = true,
    /** Per connection (id) the call deflection the quick settings tile switches. */
    val tileDeflections: Map<String, Int>,
    /** The chosen language tag; null for the system language. */
    val language: String?,
)

/**
 * The app's connections and options, as written to or read from a settings file. The passwords of
 * [profiles] are empty when the file did not carry them (or they were not unlocked).
 */
data class SettingsBackup(val profiles: List<BoxProfile>, val activeProfileId: String?, val options: AppOptions)

/** What a settings file holds, before it is imported: how many connections, and whether passwords. */
data class SettingsFileInfo(val boxes: Int, val withPasswords: Boolean)

/** Not a settings file of this app (or one of a newer version). */
class InvalidSettingsFileException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** The passphrase does not unlock the passwords of a settings file. */
class WrongPassphraseException : IOException("Wrong passphrase")

/**
 * Settings files: JSON, to take the settings to another installation – e.g. after the app's package
 * name changed, which leaves the passwords' key in the Android Keystore behind. Passwords are only
 * written on request, then encrypted with a passphrase (PBKDF2 → AES-GCM); everything else is in
 * clear text, none of it secret.
 */
object SettingsFile {

    private const val FORMAT = "pickelhaube-settings"
    private const val VERSION = 1
    private const val KDF = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 600_000
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128

    /** [backup] as a file's text; with [passphrase] including the passwords, encrypted with it. */
    fun write(backup: SettingsBackup, passphrase: CharArray?, iterations: Int = ITERATIONS): String {
        val o = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("boxes", JSONArray().apply { backup.profiles.forEach { put(it.toJson(storedPassword = null)) } })
        backup.activeProfileId?.let { o.put("activeBox", it) }
        o.put("options", optionsToJson(backup.options))
        if (passphrase != null) {
            val passwords = JSONObject()
            backup.profiles.forEach { passwords.put(it.id, it.config.password) }
            o.put("passwords", encrypt(passwords.toString(), passphrase, iterations))
        }
        return o.toString(2)
    }

    @Throws(InvalidSettingsFileException::class)
    fun inspect(text: String): SettingsFileInfo {
        val o = parse(text)
        return SettingsFileInfo(o.getJSONArray("boxes").length(), o.has("passwords"))
    }

    /**
     * The settings in [text]; the passwords only with the [passphrase] they were encrypted with
     * (without, they stay empty).
     */
    @Throws(InvalidSettingsFileException::class, WrongPassphraseException::class)
    fun read(text: String, passphrase: CharArray?): SettingsBackup {
        val o = parse(text)
        val passwords = o.optJSONObject("passwords")
            ?.takeIf { passphrase != null }
            ?.let { JSONObject(decrypt(it, passphrase!!)) }
        return try {
            val boxes = o.getJSONArray("boxes")
            SettingsBackup(
                profiles = (0 until boxes.length()).map { i ->
                    val box = boxes.getJSONObject(i)
                    boxProfileFromJson(box, passwords?.optString(box.getString("id")).orEmpty())
                },
                activeProfileId = o.optString("activeBox").takeIf { it.isNotEmpty() },
                options = optionsFromJson(o.optJSONObject("options") ?: JSONObject()),
            )
        } catch (e: JSONException) {
            throw InvalidSettingsFileException("Unreadable settings file", e)
        }
    }

    private fun parse(text: String): JSONObject {
        val o = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw InvalidSettingsFileException("Not JSON", e)
        }
        if (o.optString("format") != FORMAT || !o.has("boxes")) throw InvalidSettingsFileException("Not a settings file")
        if (o.optInt("version") > VERSION) throw InvalidSettingsFileException("Settings file of a newer version")
        return o
    }

    private fun optionsToJson(options: AppOptions) = JSONObject().apply {
        put("deviceContacts", options.deviceContactsEnabled)
        put("callLog", options.callLogEnabled)
        put("phoneSpeedDials", options.phoneSpeedDialsEnabled)
        put("speedDials", options.phoneSpeedDials.toJson())
        put("callStatus", options.callStatusEnabled)
        put("tileDeflections", JSONObject(options.tileDeflections))
        options.language?.let { put("language", it) }
    }

    private fun optionsFromJson(o: JSONObject): AppOptions {
        val deflections = o.optJSONObject("tileDeflections")
        return AppOptions(
            deviceContactsEnabled = o.optBoolean("deviceContacts", false),
            callLogEnabled = o.optBoolean("callLog", false),
            phoneSpeedDialsEnabled = o.optBoolean("phoneSpeedDials", true),
            phoneSpeedDials = o.optJSONArray("speedDials")?.let(::speedDialsFromJson).orEmpty(),
            callStatusEnabled = o.optBoolean("callStatus", true),
            tileDeflections = deflections?.keys()?.asSequence()?.associateWith { deflections.getInt(it) }.orEmpty(),
            language = o.optString("language").takeIf { it.isNotEmpty() },
        )
    }

    private fun encrypt(plain: String, passphrase: CharArray, iterations: Int): JSONObject {
        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key(passphrase, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(FORMAT.toByteArray())
        val data = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val base64 = Base64.getEncoder()
        return JSONObject()
            .put("kdf", KDF)
            .put("iterations", iterations)
            .put("salt", base64.encodeToString(salt))
            .put("iv", base64.encodeToString(iv))
            .put("data", base64.encodeToString(data))
    }

    private fun decrypt(o: JSONObject, passphrase: CharArray): String = try {
        val base64 = Base64.getDecoder()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val key = key(passphrase, base64.decode(o.getString("salt")), o.getInt("iterations"))
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, base64.decode(o.getString("iv"))))
        cipher.updateAAD(FORMAT.toByteArray())
        String(cipher.doFinal(base64.decode(o.getString("data"))), Charsets.UTF_8)
    } catch (_: AEADBadTagException) {
        throw WrongPassphraseException()
    } catch (e: GeneralSecurityException) {
        throw InvalidSettingsFileException("Passwords not readable", e)
    } catch (e: JSONException) {
        throw InvalidSettingsFileException("Passwords not readable", e)
    } catch (e: IllegalArgumentException) {
        throw InvalidSettingsFileException("Passwords not readable", e)
    }

    private fun key(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, iterations, 256)
        try {
            return SecretKeySpec(SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
