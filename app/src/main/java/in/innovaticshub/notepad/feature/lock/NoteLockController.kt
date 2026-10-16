package `in`.innovaticshub.notepad.feature.lock

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Gate for locked notes. Authentication uses biometrics when enrolled with
 * device-credential (PIN/pattern/password) fallback.
 *
 * This is an access gate, not encryption: locked notes are hidden behind the
 * prompt but the data on disk is not cryptographically sealed.
 */
class NoteLockController(private val context: Context) {

    private val authenticators =
        BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

    /** True when the device has biometric hardware or a screen lock set up. */
    fun canAuthenticate(): Boolean {
        return when (
            BiometricManager.from(context)
                .canAuthenticate(authenticators)
        ) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            else -> false
        }
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(context)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onFailure(errString.toString())
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(authenticators)
            .build()
        prompt.authenticate(info)
    }
}
