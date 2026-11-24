package org.schabi.newpipe.extractor.downloader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Call;

public class CancellableCallTest {
    @Test
    public void completedCallReturnsImmediatelyWithNoRemainingBudget() throws Exception {
        final CancellableCall call = new CancellableCall(mock(Call.class));
        assertFalse(call.awaitFinished(0, TimeUnit.NANOSECONDS));
        call.setFinished();
        assertTrue(call.awaitFinished(0, TimeUnit.NANOSECONDS));
    }

    @Test
    public void completionWakesSleepingThread() throws Exception {
        final CancellableCall call = new CancellableCall(mock(Call.class));
        final CountDownLatch entered = new CountDownLatch(1);
        final AtomicBoolean completed = new AtomicBoolean();
        final Thread waiter = new Thread(() -> {
            entered.countDown();
            try {
                completed.set(call.awaitFinished(5, TimeUnit.SECONDS));
            } catch (final InterruptedException error) {
                Thread.currentThread().interrupt();
            }
        });
        waiter.start();
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (waiter.getState() != Thread.State.TIMED_WAITING
                    && System.nanoTime() < deadline) {
                Thread.sleep(1);
            }
            assertTrue(waiter.getState() == Thread.State.TIMED_WAITING);
            assertFalse(completed.get());
        } finally {
            call.setFinished();
            waiter.join(5_000);
        }
        assertFalse(waiter.isAlive());
        assertTrue(completed.get());
    }

    @Test
    public void timeoutDoesNotMarkAnUnfinishedCallComplete() throws Exception {
        final CancellableCall call = new CancellableCall(mock(Call.class));
        assertFalse(call.awaitFinished(10, TimeUnit.MILLISECONDS));
        assertFalse(call.isFinished());
        call.setFinished();
        assertTrue(call.awaitFinished(0, TimeUnit.NANOSECONDS));
    }

    @Test
    public void interruptionStopsWaiting() throws Exception {
        final CancellableCall call = new CancellableCall(mock(Call.class));
        Thread.currentThread().interrupt();
        try {
            call.awaitFinished(5, TimeUnit.SECONDS);
            throw new AssertionError("Expected interruption");
        } catch (final InterruptedException expected) {
            assertFalse(call.isFinished());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    public void pipePipeAwaitAliasObservesTheSameCompletion() throws Exception {
        final CancellableCall call = new CancellableCall(mock(Call.class));
        assertFalse(call.await(0, TimeUnit.NANOSECONDS));
        call.setFinished();
        assertTrue(call.await(0, TimeUnit.NANOSECONDS));
        assertTrue(call.awaitFinished(0, TimeUnit.NANOSECONDS));
    }

    @Test
    public void completedDurationRemainsFixedAcrossRepeatedCompletion() throws Exception {
        final CancellableCall call = new CancellableCall(mock(Call.class));
        final long elapsed = call.getElapsedTimeMillis();
        assertTrue(elapsed >= 0);
        call.setFinished();
        final long completed = call.getElapsedTimeMillis();
        assertTrue(completed >= elapsed);
        Thread.sleep(20);
        call.setFinished();
        assertEquals(completed, call.getElapsedTimeMillis());
    }

    @Test
    public void pipePipeAwaitHonorsInterruptionEvenAfterCompletion() throws Exception {
        final CancellableCall call = new CancellableCall(mock(Call.class));
        call.setFinished();
        Thread.currentThread().interrupt();
        try {
            call.await(0, TimeUnit.NANOSECONDS);
            throw new AssertionError("Expected interruption");
        } catch (final InterruptedException expected) {
            assertTrue(call.isFinished());
        } finally {
            Thread.interrupted();
        }
    }
}
