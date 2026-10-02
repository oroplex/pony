package app.pony.companion.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect

sealed interface Route {
    val key: String get() = this::class.simpleName.orEmpty()
    val onboarding: Boolean get() = false

    data object Welcome : Route { override val onboarding = true }
    data object Privacy : Route { override val onboarding = true }
    data object Setup : Route { override val onboarding = true }
    data object TryIt : Route { override val onboarding = true }
    data object Home : Route
    data class Ask(val prefill: String? = null, val listen: Boolean = false, val send: Boolean = false) : Route {
        override val key: String get() = "Ask"
    }
    data object History : Route
    data class Task(val id: String) : Route {
        override val key: String get() = "Task-$id"
    }
    data object Pair : Route
    data object Settings : Route
    data object Memory : Route
    data object Schedules : Route
    data object Routines : Route
    data object Voice : Route
    data object Brains : Route
    data object AddBrain : Route
    data object Connection : Route
    data object Advanced : Route
    data object ShizukuSetup : Route
    data object Updates : Route
    data object Permissions : Route
    data object PrivacyReview : Route
    data object ListenHelp : Route
}

enum class NavAction { NONE, PUSH, POP, REPLACE }

class Navigator(start: Route) {
    val stack = mutableStateListOf(start)
    var action by mutableStateOf(NavAction.NONE)
        private set

    val current: Route get() = stack.last()
    val canPop: Boolean get() = stack.size > 1

    fun push(route: Route) {
        if (current == route) return
        if (current.key == route.key) {
            stack[stack.lastIndex] = route
            return
        }
        action = NavAction.PUSH
        stack.add(route)
    }

    fun pop(): Boolean {
        if (!canPop) return false
        action = NavAction.POP
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun replaceAll(route: Route) {
        action = NavAction.REPLACE
        stack.clear()
        stack.add(route)
    }

    /** Onboarding pages move forward as one flow, replacing each other. */
    fun advance(route: Route) {
        action = NavAction.PUSH
        stack[stack.lastIndex] = route
    }

    fun retreat(route: Route) {
        action = NavAction.POP
        stack[stack.lastIndex] = route
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalShared = staticCompositionLocalOf<SharedTransitionScope?> { null }

val LocalVisibility = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
private val sharedMotion = BoundsTransform { _: Rect, _: Rect -> spring(dampingRatio = 0.86f, stiffness = 380f) }

/** Shares [key] between two screens during a transition. A no-op outside navigation (and in tests). */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.shared(key: String, bounds: Boolean = false): Modifier {
    val shared = LocalShared.current ?: return this
    val scope = LocalVisibility.current ?: return this
    return with(shared) {
        if (bounds) {
            this@shared.sharedBounds(rememberSharedContentState(key), scope, boundsTransform = sharedMotion)
        } else {
            this@shared.sharedElement(rememberSharedContentState(key), scope, boundsTransform = sharedMotion)
        }
    }
}
