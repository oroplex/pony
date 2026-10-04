package app.pony.companion

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Non-exported entry for internal actions (mic prompt). Another app cannot
 * start this, so it cannot mint the process-local nonce MainActivity checks.
 */
class InternalActionsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra(MainActivity.EXTRA_REQUEST_MIC, false)) {
            val nonce = InternalIntents.issueMicNonce()
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .setAction(InternalIntents.ACTION_REQUEST_MIC)
                    .putExtra(MainActivity.EXTRA_REQUEST_MIC, true)
                    .putExtra(InternalIntents.EXTRA_NONCE, nonce),
            )
        }
        finish()
    }
}
