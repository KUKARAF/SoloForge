package com.kbul.spicycrab

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kbul.spicycrab.data.prefs.SecureKeyStore
import com.kbul.spicycrab.ui.nav.AppNav
import com.kbul.spicycrab.ui.theme.SpicyCrabTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var secureKeyStore: SecureKeyStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleAuthDeepLink(intent)
        enableEdgeToEdge()
        setContent {
            SpicyCrabTheme {
                AppNav()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthDeepLink(intent)
    }

    /**
     * Consumes the notes-server OIDC redirect `dev.rustnote.app://auth?token=<raw>` and stores the
     * bearer token. The browser/Custom Tab returns here; the settings screen re-reads the token on
     * resume to show the connected state.
     */
    private fun handleAuthDeepLink(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val data = intent.data ?: return
        if (data.scheme != AUTH_SCHEME || data.host != AUTH_HOST) return
        val token = data.getQueryParameter("token")?.takeIf { it.isNotBlank() } ?: return
        secureKeyStore.setNotesToken(token)
    }

    private companion object {
        const val AUTH_SCHEME = "dev.rustnote.app"
        const val AUTH_HOST = "auth"
    }
}
