package com.lagradost.cloudstream3.utils

/** Minimal CloudStream Event used by plugins and CommonActivity. */
class Event<T> {
    private val listeners = mutableListOf<(T) -> Unit>()

    operator fun plusAssign(listener: (T) -> Unit) {
        listeners += listener
    }

    operator fun minusAssign(listener: (T) -> Unit) {
        listeners -= listener
    }

    operator fun invoke(value: T) {
        listeners.toList().forEach { runCatching { it(value) } }
    }
}
