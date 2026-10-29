package `in`.innovaticshub.notepad.ui.canvas.collab

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Device-local parental controls. Excluded from backup and device transfer. */
class SharingSafetyStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    val configured get() = preferences.contains("pin_hash")
    val enabled get() = preferences.getBoolean("enabled", false)
    fun setEnabled(enabled: Boolean) { check(preferences.edit().putBoolean("enabled", enabled).commit()) }
    /** Only call after device authentication by the supervising adult. */
    fun resetPin() { check(preferences.edit().remove("pin_hash").remove("pin_salt").remove("pin_algorithm")
        .remove("failures").remove("locked_until").putBoolean("enabled", false).commit()) }
    fun configure(pin: String) {
        require(pin.matches(Regex("[0-9]{6}")))
        val salt = ByteArray(24).also { SecureRandom().nextBytes(it) }
        val algorithm = if (android.os.Build.VERSION.SDK_INT >= 26) "PBKDF2WithHmacSHA256" else "PBKDF2WithHmacSHA1"
        check(preferences.edit().putString("pin_salt", encode(salt)).putString("pin_hash", encode(derive(pin, salt, algorithm)))
            .putString("pin_algorithm", algorithm).putBoolean("enabled", false).commit())
    }
    fun verify(pin: String): Boolean {
        if (!configured || System.currentTimeMillis() < preferences.getLong("locked_until", 0)) return false
        val salt = Base64.decode(preferences.getString("pin_salt", ""), Base64.NO_WRAP)
        val expected = Base64.decode(preferences.getString("pin_hash", ""), Base64.NO_WRAP)
        val valid = pin.matches(Regex("[0-9]{6}")) && MessageDigest.isEqual(expected,
            derive(pin, salt, preferences.getString("pin_algorithm", "PBKDF2WithHmacSHA256")!!))
        val failures = if (valid) 0 else preferences.getInt("failures", 0) + 1
        check(preferences.edit().putInt("failures", failures)
            .putLong("locked_until", if (failures >= 3) System.currentTimeMillis() + 60_000 else 0).commit())
        return valid
    }
    fun isBlocked(room: String) = key(room) in preferences.getStringSet("blocked", emptySet()).orEmpty()
    fun block(room: String) {
        val blocked = preferences.getStringSet("blocked", emptySet()).orEmpty() + key(room)
        check(preferences.edit().putStringSet("blocked", blocked).commit())
    }
    fun saveOwner(room: String, token: String) { check(preferences.edit().putString("owner_${key(room)}", token).commit()) }
    fun ownerToken(room: String): String? = preferences.getString("owner_${key(room)}", null)
    fun forgetOwner(room: String) { check(preferences.edit().remove("owner_${key(room)}").commit()) }
    private fun key(room: String) = encode(MessageDigest.getInstance("SHA-256").digest(room.toByteArray()))
    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun derive(pin: String, salt: ByteArray, algorithm: String): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 210_000, 256)
        return try { SecretKeyFactory.getInstance(algorithm).generateSecret(spec).encoded } finally { spec.clearPassword() }
    }
    companion object { const val FILE = "drawly_sharing_safety" }
}
