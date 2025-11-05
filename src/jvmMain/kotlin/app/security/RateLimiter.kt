package app.security

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Simple in-memory rate limiter using sliding window algorithm
 * Thread-safe implementation
 */
class RateLimiter(
    private val maxAttempts: Int,
    private val windowSeconds: Long
) {
    private val attempts = ConcurrentHashMap<String, MutableList<Instant>>()
    private val lock = ReentrantLock()

    /**
     * Check if request is allowed and record the attempt
     * @param key Identifier (IP address, user ID, etc.)
     * @return Pair<allowed: Boolean, remainingAttempts: Int>
     */
    fun tryAcquire(key: String): Pair<Boolean, Int> {
        return lock.withLock {
            val now = Instant.now()
            val windowStart = now.minusSeconds(windowSeconds)

            // Get or create attempts list for this key
            val keyAttempts = attempts.getOrPut(key) { mutableListOf() }

            // Remove expired attempts (outside sliding window)
            keyAttempts.removeIf { it.isBefore(windowStart) }

            // Check if limit exceeded
            val currentCount = keyAttempts.size

            if (currentCount >= maxAttempts) {
                // Calculate remaining time until oldest attempt expires
                val oldestAttempt = keyAttempts.firstOrNull()
                return Pair(false, 0)
            }

            // Record this attempt
            keyAttempts.add(now)
            val remaining = maxAttempts - keyAttempts.size

            return Pair(true, remaining)
        }
    }

    /**
     * Get remaining attempts without recording
     */
    fun getRemainingAttempts(key: String): Int {
        return lock.withLock {
            val now = Instant.now()
            val windowStart = now.minusSeconds(windowSeconds)

            val keyAttempts = attempts[key] ?: return maxAttempts

            // Remove expired attempts
            keyAttempts.removeIf { it.isBefore(windowStart) }

            val currentCount = keyAttempts.size
            return maxOf(0, maxAttempts - currentCount)
        }
    }

    /**
     * Reset attempts for a specific key (e.g., after successful login)
     */
    fun reset(key: String) {
        lock.withLock {
            attempts.remove(key)
        }
    }

    /**
     * Get time until next attempt is allowed (in seconds)
     */
    fun getRetryAfterSeconds(key: String): Long {
        return lock.withLock {
            val now = Instant.now()
            val keyAttempts = attempts[key] ?: return 0

            if (keyAttempts.isEmpty()) return 0

            val oldestAttempt = keyAttempts.first()
            val expiresAt = oldestAttempt.plusSeconds(windowSeconds)
            val secondsUntilExpiry = expiresAt.epochSecond - now.epochSecond

            return maxOf(0, secondsUntilExpiry)
        }
    }

    /**
     * Clean up expired entries (should be called periodically)
     */
    fun cleanup() {
        lock.withLock {
            val now = Instant.now()
            val windowStart = now.minusSeconds(windowSeconds)

            attempts.entries.removeIf { (_, attemptList) ->
                attemptList.removeIf { it.isBefore(windowStart) }
                attemptList.isEmpty()
            }
        }
    }
}

/**
 * Global rate limiters for different endpoints
 */
object RateLimiters {
    // Login: 5 attempts per 15 minutes per IP
    val login = RateLimiter(maxAttempts = 5, windowSeconds = 15 * 60)

    // Registration: 3 attempts per hour per IP
    val registration = RateLimiter(maxAttempts = 3, windowSeconds = 60 * 60)

    // Password reset: 3 attempts per hour per IP
    val passwordReset = RateLimiter(maxAttempts = 3, windowSeconds = 60 * 60)

    // API general: 100 requests per minute per user
    val api = RateLimiter(maxAttempts = 100, windowSeconds = 60)
}
