package nachos.threads;

import nachos.machine.Lib;
import nachos.machine.Machine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

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
     * periodically (approximately every 500 clock ticks).
     * <p>
     * Checks whether any of the sleeping threads are due to be woken up.
     * Wakes threads up accordingly.
     * Then causes the current thread to yield, forcing a context switch
     * if there is another thread that should be run.
     */
    public void timerInterrupt() {
        long time = Machine.timer().getTime();

        boolean intStatus = Machine.interrupt().disable();

        for (int i = 0; i < sleepQueue.size(); i++) {
            if (time >= sleepTimerMap.get(sleepQueue.get(i).toString())) {
                sleepQueue.get(i).ready();

//                System.out.println(sleepQueue.get(i).getName() + " waited for at least " + sleepTimerMap.get(sleepQueue.get(i).toString()) + " ticks and has been woken up at " + time);
                sleepTimerMap.remove(sleepQueue.get(i).toString());
                sleepQueue.remove(i);
                i--;
            }
        }

        Machine.interrupt().restore(intStatus);

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

        boolean intStatus = Machine.interrupt().disable();

        long currentTime = Machine.timer().getTime();

        long wakeTime = currentTime + x;

        sleepQueue.add(KThread.currentThread());

        sleepTimerMap.put(KThread.currentThread().toString(), wakeTime);

//        System.out.println(KThread.currentThread().getName() + " is being sent to sleep at " + currentTime + " for " + x + " ticks");

        KThread.sleep();

        Machine.interrupt().restore(intStatus);
    }

    /**
     * sleepQueue - An arraylist of KThreads that have been put to sleep for a certain time
     * sleepTimerMap - A hashmap that maps sleeping KThreads to how long they are supposed to sleep
     */
    ArrayList<KThread> sleepQueue = new ArrayList<>();
    private HashMap<String, Long> sleepTimerMap = new HashMap<>();
    private static final char dbgAlarm = 'a';

    public static void selfTest() {
        Lib.debug(dbgAlarm, "Entering Alarm.selfTest");

        System.out.println("\n--------------------------------------");
        System.out.println("ENTERING TEST - Alarm.selfTest\n");

        System.out.println("Creating 5 threads with random timed waitUntil calls inside their runnable targets.");
        for (int i = 0; i < 5; i++) {
            new KThread(new Runnable() {
                @Override
                public void run() {
                    ThreadedKernel.alarm.waitUntil(Math.abs(new Random().nextInt(1000)));
                }
            }).setName("Thread-" + i).fork();
        }

        KThread.yield();

        while (ThreadedKernel.alarm.sleepQueue.size() > 0) KThread.yield();

        System.out.println("\nEXITING TEST - Alarm.selfTest");
        System.out.println("--------------------------------------\n");

        Lib.debug(dbgAlarm, "Exiting Alarm.selfTest");
    }


}
