package org.schabi.newpipe.util

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Guard the given data so that it can only be accessed by locking the mutex first.
 *
 * Inspired by [this blog post](https://jonnyzzz.com/blog/2017/03/01/guarded-by-lock/)
 * */
class GuardedByMutex<T>(
    private var data: T,
    private val lock: Mutex = Mutex(locked = false)
) {

    /** Lock the mutex and access the data, blocking the current thread.
     * @param action to run with locked mutex
     * */
    fun <Y> runWithLockSync(
        action: MutexData<T>.() -> Y
    ) = runBlocking {
        lock.withLock {
            MutexData(data, { d -> data = d }).action()
        }
    }

    /** Lock the mutex and access the data, suspending the coroutine.
     * @param action to run with locked mutex
     * */
    suspend fun <Y> runWithLock(action: MutexData<T>.() -> Y) = lock.withLock {
        MutexData(data, { d -> data = d }).action()
    }
}

/** The data inside a [GuardedByMutex], which can be accessed via [lockData].
 *  [lockData] is a `var`, so you can `set` it as well.
 * */
class MutexData<T>(data: T, val setFun: (T) -> Unit) {
    /** The data inside this [GuardedByMutex] */
    var lockData: T = data
        set(t) {
            setFun(t)
            field = t
        }
}
