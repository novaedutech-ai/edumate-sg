package sg.edumate.domain.util

import java.util.UUID

fun interface Clock { fun nowMs(): Long
    companion object { val SYSTEM = Clock { System.currentTimeMillis() } }
}

fun interface IdGenerator { fun newId(): String
    companion object { val UUIDS = IdGenerator { UUID.randomUUID().toString() } }
}

/** Typed result for operations that can fail in user-visible ways. */
sealed interface Outcome<out T> {
    data class Ok<T>(val value: T) : Outcome<T>
    data class Blocked(val reason: String) : Outcome<Nothing>
    data class Failed(val message: String, val cause: Throwable? = null) : Outcome<Nothing>
}

inline fun <T, R> Outcome<T>.map(f: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Ok -> Outcome.Ok(f(value))
    is Outcome.Blocked -> this
    is Outcome.Failed -> this
}
