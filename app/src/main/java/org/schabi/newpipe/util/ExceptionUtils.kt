package org.schabi.newpipe.util

import java.io.IOException
import java.io.InterruptedIOException

class ExceptionUtils {
    companion object {
        /**
         * @return if throwable is related to Interrupted exceptions, or one of its causes is.
         */
        @JvmStatic
        fun isInterruptedCaused(throwable: Throwable): Boolean {
            return hasExactCause(throwable,
                    InterruptedIOException::class.java,
                    InterruptedException::class.java)
        }

        /**
         * @return if throwable is related to network issues, or one of its causes is.
         */
        @JvmStatic
        fun isNetworkRelated(throwable: Throwable): Boolean {
            return hasAssignableCause(throwable,
                    IOException::class.java)
        }

        /**
         * Calls [hasCause] with the `checkSubtypes` parameter set to false.
         */
        @JvmStatic
        fun hasExactCause(throwable: Throwable, vararg causesToCheck: Class<*>): Boolean {
            return hasCause(throwable, false, *causesToCheck)
        }

        /**
         * Calls [hasCause] with the `checkSubtypes` parameter set to true.
         */
        @JvmStatic
        fun hasAssignableCause(throwable: Throwable?, vararg causesToCheck: Class<*>): Boolean {
            return hasCause(throwable, true, *causesToCheck)
        }

        /**
         * Check if throwable has some cause from the causes to check, or is itself in it.
         *
         * If `checkIfAssignable` is true, not only the exact type will be considered equals, but also its subtypes.
         *
         * @param throwable throwable that will be checked.
         * @param checkSubtypes if subtypes are also checked.
         * @param causesToCheck an array of causes to check.
         *
         * @see Class.isAssignableFrom
         */
        @JvmStatic
        tailrec fun hasCause(throwable: Throwable?, checkSubtypes: Boolean, vararg causesToCheck: Class<*>): Boolean {
            if (throwable == null) {
                return false
            }

            // Check if throwable is a subtype of any of the causes to check
            causesToCheck.forEach { causeClass ->
                if (checkSubtypes) {
                    if (causeClass.isAssignableFrom(throwable.javaClass)) {
                        return true
                    }
                } else {
                    if (causeClass == throwable.javaClass) {
                        return true
                    }
                }
            }

            val currentCause: Throwable? = throwable.cause
            // Check if cause is not pointing to the same instance, to avoid infinite loops.
            if (throwable !== currentCause) {
                return hasCause(currentCause, checkSubtypes, *causesToCheck)
            }

            return false
        }
    }
}
