package nachos.threads;

import nachos.machine.Lib;
import nachos.machine.Machine;

import javax.crypto.Mac;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
    /**
     * Allocate a new Alarm. Set the machine's timer interrupt handler to this
     * alarm's callback.
     *
     * <p>
     * <b>Note</b>: Nachos will not function correctly with more than one alarm.
     */
    public Alarm() {
        Machine.timer().setInterruptHandler(new Runnable() {
            public void run() {
                timerInterrupt();
            }
        });
    }

    /**
     * The timer interrupt handler. This is called by the machine's timer
     * periodically (approximately every 500 clock ticks). Causes the current thread
     * to yield, forcing a context switch if there is another thread that should be
     * run.
     */
    public void timerInterrupt() {
        for (KThread sleepingThread : sleepQueue) {
            if (Machine.timer().getTime() > sleepTimerMap.get(sleepingThread)) {
                boolean intStatus = Machine.interrupt().disable();
                sleepingThread.ready();
                Machine.interrupt().restore(intStatus);
            }
        }
        KThread.yield();
    }

    /**
     * Put the current thread to sleep for at least <i>x</i> ticks, waking it up in
     * the timer interrupt handler. The thread must be woken up (placed in the
     * scheduler ready set) during the first timer interrupt where
     *
     * <p>
     * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
     *
     * @param x the minimum number of clock ticks to wait.
     * @see nachos.machine.Timer#getTime()
     */
    public void waitUntil(long x) {
        // for now, cheat just to get something working (busy waiting is bad)
        long wakeTime = Machine.timer().getTime() + x;

        Lib.assertTrue(Machine.interrupt().disabled());

        sleepQueue.add(KThread.currentThread());
        sleepTimerMap.put(KThread.currentThread(), wakeTime);

        KThread.sleep();

    }

    /**
     * Check if the currentThread has slept for the intended duration after a call of waitUntil(long x).
     * If yes, add it to the readyQueue.
     *
     * @param wakeTime the time after which the currentThread should be added back to the readyQueue
     */
    public void setReadyWhenTime(long wakeTime) {
        if (Machine.timer().getTime() >= wakeTime) {
            boolean intStatus = Machine.interrupt().disable();
            KThread.currentThread().ready();
            Machine.interrupt().restore(intStatus);
        }

    }

    private static ArrayList<KThread> sleepQueue = new ArrayList<>();
    private static HashMap<KThread, Long> sleepTimerMap = new HashMap<>();
}
