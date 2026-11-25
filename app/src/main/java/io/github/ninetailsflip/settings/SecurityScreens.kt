package io.github.ninetailsflip.settings

import android.accounts.AccountManager
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Process
import android.os.UserHandle
import android.provider.Settings
import android.util.Log

// ── Screen lock ─────────────────────────────────────────────────────────────────────────────

/** The screen lock PIN, through LockPatternUtils (hidden): set, change, remove. Changing or
 *  removing one needs the current PIN first, as Android requires. */
object Lock {
    private const val TAG = "Settings"

    fun hasPin(context: Context) = context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    /** UserHandle.getIdentifier is @SystemApi; a uid is user * 100000 + app. */
    private fun userId(): Int = Process.myUid() / 100_000

    private fun credential(pin: String?): Any? {
        val cls = Class.forName("com.android.internal.widget.LockscreenCredential")
        return if (pin == null) cls.getMethod("createNone").invoke(null)
        else cls.getMethod("createPin", CharSequence::class.java).invoke(null, pin)
    }

    /** Sets [newPin] (null: no lock) where the current one is [oldPin] (null: none now). */
    fun setPin(context: Context, newPin: String?, oldPin: String?): Boolean = try {
        val utilsClass = Class.forName("com.android.internal.widget.LockPatternUtils")
        val utils = utilsClass.getConstructor(Context::class.java).newInstance(context)
        val credentialClass = Class.forName("com.android.internal.widget.LockscreenCredential")
        utilsClass.getMethod("setLockCredential", credentialClass, credentialClass, Int::class.javaPrimitiveType)
            .invoke(utils, credential(newPin), credential(oldPin), userId()) as Boolean
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't change the screen lock", e)
        false
    }

    fun isValidPin(pin: String) = pin.length in 4..16 && pin.all { it in '0'..'9' }
}

class SecurityScreen : Screen("Security") {
    override fun rows(): List<Row> {
        val pin = Lock.hasPin(context)
        return buildList {
            add(header("Screen lock"))
            if (pin) {
                add(Row("Change the PIN", "the current one first") { askCurrent { old -> askNew(old) } })
                add(Row("Remove the PIN", "anyone can open the phone") {
                    askCurrent { old -> if (Lock.setPin(context, null, old)) say("no screen lock now") else say("wrong PIN") }
                })
            } else {
                add(Row("Set a PIN", "4 to 16 digits") { askNew(null) })
            }
            add(header("Privacy"))
            add(Row("Permission manager", "which apps may use what") {
                start(Intent("android.intent.action.MANAGE_PERMISSIONS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            })
            val showPasswords = context.systemInt(Settings.System.TEXT_SHOW_PASSWORD, 1) == 1
            add(Row("Show passwords", if (showPasswords) "each letter briefly as typed" else "never", adjust = {
                Settings.System.putInt(context.contentResolver, Settings.System.TEXT_SHOW_PASSWORD, if (showPasswords) 0 else 1)
            }))
            add(Row("Install unknown apps", "which apps may install others") {
                start(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            })
        }
    }

    private fun askCurrent(then: (String) -> Unit) {
        host.ask(FieldRequest("The current PIN", FieldKind.PIN) { then(it) })
    }

    private fun askNew(old: String?) {
        host.ask(FieldRequest("New PIN, 4 to 16 digits", FieldKind.PIN) { first ->
            if (!Lock.isValidPin(first)) {
                say("a PIN is 4 to 16 digits")
                askNew(old)
            } else {
                host.ask(FieldRequest("The same PIN again", FieldKind.PIN) { second ->
                    when {
                        second != first -> {
                            say("the two PINs differ: once more")
                            askNew(old)
                        }
                        Lock.setPin(context, first, old) -> say("PIN set")
                        else -> say(if (old != null) "wrong current PIN" else "Android didn't take the PIN")
                    }
                })
            }
        })
    }
}

// ── Location ────────────────────────────────────────────────────────────────────────────────

class LocationScreen : Screen("Location") {
    override fun rows(): List<Row> {
        val lm = context.getSystemService(LocationManager::class.java)
        val on = lm?.isLocationEnabled == true
        return listOf(
            Row("Use location", onOff(on), adjust = {
                // LocationManager.setLocationEnabledForUser: @SystemApi.
                if (Hidden.call(lm!!, "setLocationEnabledForUser", Hidden.BOOLEAN to !on, UserHandle::class.java to Process.myUserHandle()) == null) {
                    say("Android didn't change it")
                }
            }),
            Row("App access", "which apps may know where you are") {
                start(
                    Intent("android.intent.action.MANAGE_PERMISSION_APPS")
                        .putExtra("android.intent.extra.PERMISSION_NAME", "android.permission-group.LOCATION")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
        )
    }
}

// ── Accounts ────────────────────────────────────────────────────────────────────────────────

object Accounts {
    fun summary(context: Context): String {
        val count = runCatching { AccountManager.get(context).accounts.size }.getOrDefault(0)
        return if (count == 0) "none" else "$count"
    }
}

/** The phone's accounts, and adding one: the account's own app asks for what it needs. */
class AccountsScreen : Screen("Accounts") {
    override fun rows(): List<Row> {
        val am = AccountManager.get(context)
        val pm = context.packageManager
        val types = am.authenticatorTypes.associateBy { it.type }
        fun typeLabel(type: String) = types[type]?.let { runCatching { pm.getResourcesForApplication(it.packageName).getString(it.labelId) }.getOrNull() } ?: type
        return buildList {
            val accounts = am.accounts
            add(header("On this phone"))
            if (accounts.isEmpty()) add(Row("No accounts"))
            accounts.forEach { account -> add(Row(account.name, typeLabel(account.type))) }
            add(header("Add an account"))
            if (types.isEmpty()) add(Row("No app here offers accounts"))
            types.values.forEach { type ->
                add(Row(typeLabel(type.type), type.packageName) {
                    am.addAccount(type.type, null, null, null, null, { future ->
                        val intent = runCatching { future.result.getParcelable<Intent>(AccountManager.KEY_INTENT) }.getOrNull()
                        if (intent != null) start(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }, null)
                })
            }
            val sync = runCatching { android.content.ContentResolver.getMasterSyncAutomatically() }.getOrDefault(true)
            add(header("Sync"))
            add(Row("Sync data automatically", onOff(sync), adjust = {
                android.content.ContentResolver.setMasterSyncAutomatically(!sync)
            }))
        }
    }
}

// ── Accessibility ───────────────────────────────────────────────────────────────────────────

class AccessibilityScreen : Screen("Accessibility") {
    override fun hint() = "←→ change · ‹ back"

    override fun rows(): List<Row> {
        val resolver = context.contentResolver
        val inversion = context.secureInt("accessibility_display_inversion_enabled", 0) == 1
        val highContrast = context.secureInt("high_text_contrast_enabled", 0) == 1
        val noAnimations = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        val scale = Settings.System.getFloat(resolver, Settings.System.FONT_SCALE, 1.0f)
        return listOf(
            Row("Text size", "${(scale * 100).toInt()} % · Display has the same") { open(Screens.DISPLAY) },
            Row("High-contrast text", onOff(highContrast), adjust = {
                Settings.Secure.putInt(resolver, "high_text_contrast_enabled", if (highContrast) 0 else 1)
            }),
            Row("Color inversion", onOff(inversion), adjust = {
                Settings.Secure.putInt(resolver, "accessibility_display_inversion_enabled", if (inversion) 0 else 1)
            }),
            Row("Remove animations", onOff(noAnimations), adjust = {
                val value = if (noAnimations) 1f else 0f
                Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, value)
                Settings.Global.putFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, value)
                Settings.Global.putFloat(resolver, Settings.Global.WINDOW_ANIMATION_SCALE, value)
            }),
        ) + services()
    }

    /** Installed accessibility services (screen readers and such), each on or off. */
    private fun services(): List<Row> {
        val am = context.getSystemService(android.view.accessibility.AccessibilityManager::class.java) ?: return emptyList()
        val installed = am.installedAccessibilityServiceList
        if (installed.isEmpty()) return emptyList()
        val resolver = context.contentResolver
        val enabled = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            .split(':').filter { it.isNotEmpty() }.toSet()
        return listOf(header("Services")) + installed.map { service ->
            val id = service.id
            val on = id in enabled
            Row(service.resolveInfo.loadLabel(context.packageManager).toString(), onOff(on), adjust = {
                val next = if (on) enabled - id else enabled + id
                Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, next.joinToString(":"))
                Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, if (next.isEmpty()) 0 else 1)
            })
        }
    }
}
