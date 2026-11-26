package io.github.ninetailsflip.settings

import android.content.Intent

/**
 * The shade ([ShadeScreen]) as a panel of its own: started by the system with the panel to open
 * on, closed by the same key that opened it, and gone once anything else comes to the front -
 * the app a notification opened, a settings page, the home screen.
 */
class ShadeActivity : ScreenActivity() {

    override fun pageFor(intent: Intent): Pair<String, String?> = Screens.SHADE to intent.getStringExtra(EXTRA_PANEL)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_TOGGLE, false)) {
            finish()
            return
        }
        (screen as? ShadeScreen)?.reopen(intent.getStringExtra(EXTRA_PANEL))
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) finish()
    }

    /** A settings page opens as Settings itself, in Settings' task; the shade closes. */
    override fun open(name: String, arg: String?) {
        startActivity(intentFor(this, name, arg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object {
        /** What the system starts it with (patch 0010). */
        const val EXTRA_PANEL = "org.thor2.shade.extra.PANEL"
        const val EXTRA_TOGGLE = "org.thor2.shade.extra.TOGGLE"
    }
}
