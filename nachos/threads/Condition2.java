package nachos.threads;

import nachos.machine.*;

import java.util.ArrayList;

/**
 * An implementation of condition variables that disables interrupt(s) for
 * synchronization.
 *
 * <p>
 * You must implement this.
 *
 * @see    nachos.threads.Condition
 */
public class Condition2 {
    /**
     * Allocate a new condition variable.
     *
     * @param    conditionLock    the lock associated with this condition
     * variable. The current thread must hold this
     * lock whenever it uses <tt>sleep()</tt>,
     * <tt>wake()</tt>, or <tt>wakeAll()</tt>.
     */
    public Condition2(Lock conditionLock) {
        this.conditionLock = conditionLock;
        this.threadArrayList = new ArrayList<>();
    }

    /**
     * Atomically release the associated lock and go to sleep on this condition
     * variable until another thread wakes it using <tt>wake()</tt>. The
     * current thread must hold the associated lock. The thread will
     * automatically reacquire the lock before <tt>sleep()</tt> returns.
     */
    public void sleep() {
        Lib.assertTrue(conditionLock.isHeldByCurrentThread());

        conditionLock.release();

        // replacing the use of Semaphore P() function
        boolean intStatus = Machine.interrupt().disable();
        threadArrayList.add(KThread.currentThread());
        KThread.sleep();
        Machine.interrupt().restore(intStatus);

        conditionLock.acquire();
    }

    /**
     * Wake up at most one thread sleeping on this condition variable. The
     * current thread must hold the associated lock.
     */
    public void wake() {
        Lib.assertTrue(conditionLock.isHeldByCurrentThread());

        // replacing the use of Semaphore V() function
        boolean intStatus = Machine.interrupt().disable();

        if (threadArrayList.size() > 0) {
            threadArrayList.remove(0).ready();
        }

        Machine.interrupt().restore(intStatus);

    }

    /**
     * Wake up all threads sleeping on this condition variable. The current
     * thread must hold the associated lock.
     */
    public void wakeAll() {

        Lib.assertTrue(conditionLock.isHeldByCurrentThread());

        while (!threadArrayList.isEmpty()) {
            wake();
        }

    }

    private Lock conditionLock;
    private ArrayList<KThread> threadArrayList;
}
