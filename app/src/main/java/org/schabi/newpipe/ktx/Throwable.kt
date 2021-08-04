@file:JvmName("ExceptionUtils")

package org.schabi.newpipe.ktx

import java.io.IOException
import java.io.InterruptedIOException

/**
 * @return if throwable is related to Interrupted exceptions, or one of its causes is.
 */
val Throwable.isInterruptedCaused: Boolean
    get() = hasExactCause(InterruptedIOException::class.java, InterruptedException::class.java)

/**
 * @return if throwable is related to network issues, or one of its causes is.
 */
val Throwable.isNetworkRelated: Boolean
    get() = hasAssignableCause<IOException>()

/**
 * Calls [hasCause] with the `checkSubtypes` parameter set to false.
 */
fun Throwable.hasExactCause(vararg causesToCheck: Class<*>) = hasCause(false, *causesToCheck)

/**
 * Calls [hasCause] with a reified [Throwable] type.
 */
inline fun <reified T : Throwable> Throwable.hasExactCause() = hasExactCause(T::class.java)

/**
 * Calls [hasCause] with the `checkSubtypes` parameter set to true.
 */
fun Throwable?.hasAssignableCause(vararg causesToCheck: Class<*>) = hasCause(true, *causesToCheck)

/**
 * Calls [hasCause] with a reified [Throwable] type.
 */
inline fun <reified T : Throwable> Throwable?.hasAssignableCause() = hasAssignableCause(T::class.java)

/**
 * Check if the throwable has some cause from the causes to check, or is itself in it.
 *
 * If `checkIfAssignable` is true, not only the exact type will be considered equals, but also its subtypes.
 *
 * @param checkSubtypes if subtypes are also checked.
 * @param causesToCheck an array of causes to check.
 *
 * @see Class.isAssignableFrom
 */
tailrec fun Throwable?.hasCause(checkSubtypes: Boolean, vararg causesToCheck: Class<*>): Boolean {
    if (this == null) {
        return false
    }

    // Check if throwable is a subtype of any of the causes to check
    causesToCheck.forEach { causeClass ->
        if (checkSubtypes) {
            if (causeClass.isAssignableFrom(this.javaClass)) {
                return true
            }
        } else if (causeClass == this.javaClass) {
            return true
        }
    }

    val currentCause: Throwable? = cause
    // Check if cause is not pointing to the same instance, to avoid infinite loops.
    if (this !== currentCause) {
        return currentCause.hasCause(checkSubtypes, *causesToCheck)
    }

    return false
}
