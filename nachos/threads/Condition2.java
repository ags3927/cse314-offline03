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
 * @see nachos.threads.Condition
 */
public class Condition2 {
    /**
     * Allocate a new condition variable.
     *
     * @param conditionLock the lock associated with this condition
     *                      variable. The current thread must hold this
     *                      lock whenever it uses <tt>sleep()</tt>,
     *                      <tt>wake()</tt>, or <tt>wakeAll()</tt>.
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
            KThread tempThread = threadArrayList.remove(0);
            tempThread.ready();
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
    private static final char dbgCondition2 = 'n';

    public static void selfTest() {
        Lib.debug(dbgCondition2, "Entering Condition2.selfTest");
        System.out.println("\n--------------------------------------");
        System.out.println("ENTERING TEST - Condition2.selfTest\n");

        final Lock lock = new Lock();
        final Condition2 condition = new Condition2(lock);

        System.out.println("Creating Thread-1 and sending it to sleep on a condition variable");

        KThread sleep = new KThread(new Runnable() {
            //Test 1: Sleep
            public void run() {
                //get the Lock
                lock.acquire();
                System.out.println("Testing sleep()");
                System.out.println("Thread-1 sleeping");
                condition.sleep();
                System.out.println("Thread-1 woke up");
                System.out.println("Finished testing sleep()");
                lock.release();
            }

        });
        sleep.fork();

        KThread wake = new KThread(new Runnable() {
            //Test 2: Wake
            public void run() {
                lock.acquire();
                System.out.println("Testing wake()");
                System.out.println("Thread-2 waking up the sleeping thread");
                condition.wake();;
                System.out.println("Thread-2 woke up the sleeping thread");
                System.out.println("Finished testing wake()");
                lock.release();
            }
        });
        wake.fork();
        sleep.join();

        System.out.println("\nTesting wakeAll()");

        KThread sleep1 = new KThread(new Runnable() {
            //Test 3: Wake All sleeping thread 1
            public void run() {
                lock.acquire();
                System.out.println("Thread-3 sleeping");
                condition.sleep();
                System.out.println("Thread-3 woke up");
                lock.release();
            }
        });
        sleep1.fork();

        KThread sleep2 = new KThread(new Runnable() {
            //Test 3: Wake All sleeping thead 2
            public void run() {
                lock.acquire();
                System.out.println("Thread-4 sleeping");
                condition.sleep();
                System.out.println("Thread-4 woke up");
                lock.release();
            }
        });
        sleep2.fork();


        KThread wakeall = new KThread(new Runnable() {
            //Test 3: Wake all
            public void run() {
                lock.acquire();
                System.out.println("Thread-5 waking up all sleeping threads");
                condition.wakeAll();
                System.out.println("Thread-5 woke up all sleeping threads");
                lock.release();
            }
        });
        wakeall.fork();
        sleep1.join();
        sleep2.join();

        System.out.println("Finished testing wakeAll()");

        System.out.println("\nEXITING TEST - Condition2.selfTest");
        System.out.println("--------------------------------------\n");
        Lib.debug(dbgCondition2, "Exiting Condition2.selfTest");
    }

}
