package dev.memoji.flashcards.core.testing

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * A [Clock] the test moves by hand. Last-seen ordering decides which Cards a Session draws, so
 * a test that let the real clock run would be asserting on how fast the machine executed it.
 */
internal class MutableClock(private var now: Instant = START) : Clock() {

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = now

    fun advance(duration: Duration) {
        now = now.plus(duration)
    }

    fun advanceOneMinute() = advance(Duration.ofMinutes(1))

    companion object {
        /** An arbitrary fixed instant, so a failure message reads the same on every run. */
        val START: Instant = Instant.parse("2026-08-04T09:00:00Z")
    }
}
