package app.pony.companion.ui

import app.pony.companion.brain.ProviderPreset
import app.pony.companion.brain.ProviderRecord
import app.pony.companion.session.Connection
import app.pony.companion.session.ReadinessState
import app.pony.companion.session.SessionUi
import app.pony.companion.tasks.TaskRecord
import app.pony.companion.tasks.TaskState
import app.pony.companion.ui.design.OrbMood
import app.pony.companion.ui.design.Tone
import java.util.Calendar
import kotlin.random.Random

enum class BadgeAction { NONE, RECONNECT, RETRY, CONNECT, DISCONNECT }

data class Badge(
    val title: String,
    val detail: String?,
    val tone: Tone,
    val live: Boolean,
    val action: BadgeAction,
)

/** The optional, secondary assistant row under the home pill. Null when an assistant is already live or there's no brain to make it optional. */
data class AssistantLink(
    val label: String,
    val detail: String,
    val action: BadgeAction,
)

/**
 * Home's connection line. A key brain on the phone is the primary, positive
 * state: Pony works standalone, so the pill reads ready, not "not connected."
 * An assistant (Grok Bot, Tailscale) is optional and only drives the pill when
 * it's actually live or when there's no brain at all.
 */
fun connectionBadge(session: SessionUi, listening: Boolean, grok: String, resumable: Boolean, now: Long, keyBrain: String? = null): Badge = when (session.connection) {
    Connection.Connected -> when {
        session.peerAway -> Badge("$grok stepped away", "Pony is holding the session for it", Tone.Warning, false, BadgeAction.NONE)
        listening -> Badge("$grok is listening", "Ask for anything", Tone.Success, true, BadgeAction.NONE)
        else -> Badge("Connected to $grok", "It picks up asks when it checks in", Tone.Success, false, BadgeAction.NONE)
    }
    Connection.Reconnecting -> {
        val wait = session.nextRetryAt?.let { ((it - now) / 1000).coerceAtLeast(0) }
        val detail = if (wait != null && wait > 0) "Trying again in ${wait}s" else "Trying now"
        Badge("Reconnecting to $grok…", detail, Tone.Warning, true, BadgeAction.RETRY)
    }
    Connection.Pairing -> Badge("Pairing with $grok…", "Compare the safety code", Tone.Iris, true, BadgeAction.NONE)
    Connection.Ending -> Badge("Disconnecting…", null, Tone.Neutral, false, BadgeAction.NONE)
    Connection.Error -> if (keyBrain != null) {
        readyBadge(keyBrain)
    } else {
        Badge(
            "Connection lost",
            session.lastError?.take(80) ?: "Tap reconnect to try again",
            Tone.Danger,
            false,
            if (resumable || session.resumable) BadgeAction.RECONNECT else BadgeAction.CONNECT,
        )
    }
    Connection.Idle -> when {
        keyBrain != null -> readyBadge(keyBrain)
        resumable -> Badge("Not connected", "Your last session can pick up where it left off", Tone.Neutral, false, BadgeAction.RECONNECT)
        else -> Badge("No brain yet", "Add a key for answers on this phone, or link an assistant", Tone.Warning, false, BadgeAction.CONNECT)
    }
}

private fun readyBadge(keyBrain: String) =
    Badge("Ready · $keyBrain", "$keyBrain runs right here on this phone", Tone.Success, false, BadgeAction.NONE)

/**
 * The optional assistant row beneath the home pill. While a bot is live it's the
 * one-tap Disconnect; while it's away it's the optional link/reconnect. A key
 * brain keeps running either way, so disconnecting never leaves Pony mute.
 */
fun assistantLink(session: SessionUi, keyBrain: String?, resumable: Boolean, grok: String): AssistantLink? {
    if (session.live) {
        return AssistantLink("Disconnect $grok", "Ends the session now. Pony's own brain still answers.", BadgeAction.DISCONNECT)
    }
    if (keyBrain == null) return null
    val quiet = session.connection == Connection.Idle || session.connection == Connection.Error
    if (!quiet) return null
    return if (resumable || session.resumable) {
        AssistantLink("Reconnect your assistant", "Optional — resume remote and scheduled asks", BadgeAction.RECONNECT)
    } else {
        AssistantLink("Link an assistant", "Optional — for remote and scheduled asks", BadgeAction.CONNECT)
    }
}

/** One brain in the Connect picker: a supported provider and whether it's set up. */
data class BrainChoice(
    val preset: ProviderPreset,
    val connected: Boolean,
    val active: Boolean,
    val recordId: String?,
    val keyLast4: String?,
)

/**
 * The API-key brains the picker offers, in a fixed order, each tagged with
 * whether the owner has a key saved and whether it's the one in use. A preset
 * maps to the first saved brain on it. [active] is only true when the phone
 * brain is the chosen one — a saved key that Grok Bot is shadowing reads as
 * connected, not in use.
 */
fun brainChoices(providers: List<ProviderRecord>, phoneBrainSelected: Boolean): List<BrainChoice> =
    ProviderPreset.pickable.map { preset ->
        val record = providers.firstOrNull { it.preset == preset }
        BrainChoice(
            preset = preset,
            connected = record != null,
            active = phoneBrainSelected && record?.active == true,
            recordId = record?.id,
            keyLast4 = record?.keyLast4,
        )
    }

fun homeMood(session: SessionUi, listening: Boolean, live: TaskRecord?): OrbMood = when {
    live != null && (live.state == TaskState.Waiting || live.state == TaskState.Queued) -> OrbMood.Waiting
    live != null && live.running -> OrbMood.Working
    session.connection == Connection.Reconnecting || session.connection == Connection.Pairing -> OrbMood.Thinking
    session.connection == Connection.Connected && listening -> OrbMood.Listening
    session.connection == Connection.Connected -> OrbMood.Idle
    else -> OrbMood.Idle
}

fun greeting(now: Long = System.currentTimeMillis()): String {
    val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Hello, night owl"
    }
}

enum class SuggestionIcon {
    Car, Bag, Bed, Utensils, Photos, Package, Home, Plane, Pin, Mail, Bolt, Music, Play,
}

/**
 * A one-tap example of something impressive Pony can do. [app] is the package the ask
 * lives in — every showcase ask runs inside a real third-party app, so the chip is only
 * offered when that app is installed. Asks that pay, book, order, or send are phrased so
 * Pony drives up to the app's own confirm step and waits for the user there.
 */
data class Suggestion(
    val label: String,
    val prompt: String,
    val icon: SuggestionIcon,
    val app: String? = null,
)

/**
 * A pool of real, multi-step, cross-app asks — booking, ordering, shopping, travel, and
 * finding things inside specific apps. We show a handful at a time (see [pickSuggestions])
 * and reshuffle whenever the surface opens, filtered to apps actually on this phone, so the
 * home and ask screens show off things Gemini/Bixby can't — never toy settings commands.
 */
val SUGGESTIONS = listOf(
    Suggestion("Uber to the airport", "Book an Uber to the airport tomorrow at 6am", SuggestionIcon.Car, app = "com.ubercab"),
    Suggestion("Spicy tuna, Postmates", "Order spicy tuna rolls on Postmates now", SuggestionIcon.Bag, app = "com.postmates.android"),
    Suggestion("2 nights at the Wynn", "Book 2 nights at the Wynn on HotelTonight", SuggestionIcon.Bed, app = "com.hoteltonight.android.prod"),
    Suggestion("Dinner on Resy", "Book dinner tomorrow night on Resy", SuggestionIcon.Utensils, app = "com.resy.android"),
    Suggestion("Album of beach photos", "Make an album of my best beach photos", SuggestionIcon.Photos, app = "com.google.android.apps.photos"),
    Suggestion("Reorder on Amazon", "Reorder my last Amazon order", SuggestionIcon.Package, app = "com.amazon.mShop.android.shopping"),
    Suggestion("2-bed in Venice", "Find a 2-bed in Venice on Zillow under \$5k", SuggestionIcon.Home, app = "com.zillow.android.zillowmap"),
    Suggestion("Check in on United", "Check in for my flight in the United app", SuggestionIcon.Plane, app = "com.united.mobile.android"),
    Suggestion("Sushi on Yelp", "Find the best-rated sushi near me on Yelp and book a table", SuggestionIcon.Pin, app = "com.yelp.android"),
    Suggestion("Email my Uber receipt", "Find my latest Uber receipt and email it to me", SuggestionIcon.Mail, app = "com.ubercab"),
    Suggestion("My Tesla's charge", "Check my Tesla's charge level", SuggestionIcon.Bolt, app = "com.teslamotors.tesla"),
    Suggestion("Lyft XL to the game", "Book a Lyft XL for 4 to Dodger Stadium", SuggestionIcon.Car, app = "me.lyft.android"),
    Suggestion("Airbnb in Joshua Tree", "Find a quiet Airbnb in Joshua Tree this weekend", SuggestionIcon.Bed, app = "com.airbnb.android"),
    Suggestion("AirPods at Target", "Add AirPods to my Target cart for pickup", SuggestionIcon.Bag, app = "com.target.ui"),
    Suggestion("DoorDash my usual", "Reorder my usual on DoorDash", SuggestionIcon.Bag, app = "com.dd.doordash"),
    Suggestion("Queue Discover Weekly", "Queue up my Discover Weekly on Spotify", SuggestionIcon.Music, app = "com.spotify.music"),
    Suggestion("Instacart milk & eggs", "Add milk and eggs to my Instacart cart", SuggestionIcon.Package, app = "com.instacart.client"),
    Suggestion("Coffee on the way", "Find a top-rated coffee shop on the way downtown and start navigation", SuggestionIcon.Pin, app = "com.google.android.apps.maps"),
    Suggestion("Email from my bank", "Find the last email from my bank in Gmail", SuggestionIcon.Mail, app = "com.google.android.gm"),
    Suggestion("Resume my podcast", "Resume the podcast I was watching on YouTube", SuggestionIcon.Play, app = "com.google.android.youtube"),
)

/**
 * Pick up to [count] suggestions to show. App-specific asks are dropped when [isInstalled]
 * reports their package missing — we never advertise an app the user doesn't have — and the
 * survivors are shuffled so each screen open feels fresh. Returns fewer than [count] (or
 * none) if too few apps are installed; callers simply render what comes back.
 */
fun pickSuggestions(
    pool: List<Suggestion> = SUGGESTIONS,
    isInstalled: (String) -> Boolean = { true },
    count: Int = 4,
    random: Random = Random,
): List<Suggestion> {
    val available = pool.filter { it.app == null || isInstalled(it.app) }
    return available.shuffled(random).take(count)
}

enum class AskFix { PAIR, ADD_KEY, USE_GROK, LISTEN_HELP }

data class AskStatus(
    val title: String,
    val body: String,
    val tone: Tone,
    val fixes: List<AskFix> = emptyList(),
)

/** What happens to an ask right now, before the owner sends it. */
fun askStatus(phoneMode: Boolean, session: SessionUi, listening: Boolean, keyBrain: String?, grok: String): AskStatus {
    val connected = session.connection == Connection.Connected && !session.peerAway
    if (phoneMode) {
        return when {
            keyBrain != null -> AskStatus("$keyBrain is ready", "It runs right here on the phone.", Tone.Success)
            connected && listening -> AskStatus("No key on this phone", "$grok is listening, so your ask goes to it.", Tone.Iris, listOf(AskFix.ADD_KEY))
            else -> AskStatus(
                "No brain on this phone yet",
                "Pony can still open apps, set timers, and do quick math. For anything else, add an API key or switch to $grok.",
                Tone.Warning,
                listOf(AskFix.ADD_KEY, AskFix.USE_GROK),
            )
        }
    }
    return when {
        connected && listening -> AskStatus("$grok is listening", "Your ask goes straight to it.", Tone.Success)
        connected && keyBrain != null -> AskStatus(
            "$grok is connected",
            "If it doesn't pick this up within a few seconds, $keyBrain on this phone will.",
            Tone.Iris,
        )
        connected -> AskStatus(
            "$grok isn't listening right now",
            "Your ask waits until it checks in. Run pony-phone listen on your computer and it answers right away.",
            Tone.Warning,
            listOf(AskFix.LISTEN_HELP, AskFix.ADD_KEY),
        )
        keyBrain != null -> AskStatus("$grok isn't connected", "$keyBrain on this phone will handle your ask.", Tone.Iris, listOf(AskFix.PAIR))
        session.connection == Connection.Reconnecting -> AskStatus(
            "Reconnecting to $grok…",
            "Your ask will wait for it. Simple things like apps, timers, and math run on the phone right away.",
            Tone.Warning,
        )
        else -> AskStatus(
            "$grok isn't connected",
            "Pony can still open apps, set timers, and do quick math. For anything else, pair $grok or add a key.",
            Tone.Warning,
            listOf(AskFix.PAIR, AskFix.ADD_KEY),
        )
    }
}

data class SetupItem(
    val key: String,
    val title: String,
    val body: String,
    val done: Boolean,
    val required: Boolean,
    val action: String,
)

fun setupItems(state: ReadinessState, samsung: Boolean): List<SetupItem> = listOf(
    SetupItem(
        "control",
        "Pony control",
        if (state.accessibility) "On. Pony can read the screen and tap for you." else "Lets Pony see the screen and tap, type, and open apps. Nothing happens until you ask.",
        state.accessibility,
        true,
        "Turn on",
    ),
    SetupItem(
        "notifications",
        "Notifications",
        if (state.notifications) "On. You'll always see when Pony is working, with Stop." else "Shows when Pony is connected or working, with a Stop button.",
        state.notifications,
        false,
        "Allow",
    ),
    SetupItem(
        "battery",
        "Keep Pony awake",
        when {
            state.battery -> "Done. Pony stays connected in the background."
            samsung -> "Samsung puts idle apps to sleep, which drops Grok Bot's connection. Pony only uses battery while it's connected."
            else -> "Android pauses idle apps, which drops Grok Bot's connection. Pony only uses battery while it's connected."
        },
        state.battery,
        false,
        "Allow",
    ),
    SetupItem(
        "microphone",
        "Microphone",
        if (state.microphone) "Allowed. Tap the mic and talk to Pony." else "So you can talk to Pony. Audio is recognized on the phone.",
        state.microphone,
        false,
        "Allow",
    ),
    SetupItem(
        "keyboard",
        "Pony keyboard",
        if (state.keyboard) "Enabled. Your usual keyboard stays the default." else "Helps Pony type in apps like Samsung Notes. Your keyboard stays the default.",
        state.keyboard,
        false,
        "Enable",
    ),
)

fun relativeTime(then: Long, now: Long): String {
    val seconds = ((now - then) / 1000).coerceAtLeast(0)
    return when {
        seconds < 45 -> "Just now"
        seconds < 90 -> "1 min ago"
        seconds < 3600 -> "${seconds / 60} min ago"
        seconds < 7200 -> "1 hr ago"
        seconds < 86_400 -> "${seconds / 3600} hr ago"
        seconds < 172_800 -> "Yesterday"
        else -> "${seconds / 86_400} days ago"
    }
}

fun durationText(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    return when {
        seconds < 1 -> "under a second"
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    }
}

fun remainingText(endsAt: Long?, now: Long): String? {
    val end = endsAt ?: return null
    val minutes = ((end - now) / 60_000).coerceAtLeast(0)
    return when {
        minutes >= 120 -> "${minutes / 60} hr left"
        minutes >= 60 -> "1 hr ${minutes - 60} min left"
        else -> "$minutes min left"
    }
}

fun taskTone(state: TaskState): Tone = when (state) {
    TaskState.Done -> Tone.Success
    TaskState.Stopped -> Tone.Neutral
    TaskState.Failed, TaskState.Expired -> Tone.Danger
    TaskState.Waiting, TaskState.Queued -> Tone.Warning
    else -> Tone.Iris
}

fun taskStateLabel(state: TaskState): String = when (state) {
    TaskState.Queued -> "Waiting"
    TaskState.Sent -> "Sent"
    TaskState.Running -> "Working"
    TaskState.Waiting -> "Waiting"
    TaskState.Done -> "Done"
    TaskState.Stopped -> "Stopped"
    TaskState.Failed -> "Didn't finish"
    TaskState.Expired -> "Expired"
}
