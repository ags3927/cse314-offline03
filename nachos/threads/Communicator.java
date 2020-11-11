package nachos.threads;

import nachos.machine.Lib;
import nachos.machine.Machine;

import java.util.Random;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>,
 * and multiple threads can be waiting to <i>listen</i>. But there should never
 * be a time when both a speaker and a listener are waiting, because the two
 * threads can be paired off at this point.
 */
public class Communicator {
    /**
     * Allocate a new communicator.
     */

    public Communicator() {
        this.communicateLock = new Lock();
        this.speakCondition = new Condition2(communicateLock);
        this.listenCondition = new Condition2(communicateLock);

        this.hasSpoken = false;
    }

    /**
     * Wait for a thread to listen through this communicator, and then transfer
     * <i>word</i> to the listener.
     *
     * <p>
     * Does not return until this thread is paired up with a listening thread.
     * Exactly one listener should receive <i>word</i>.
     *
     * @param    word    the integer to transfer.
     */
    public void speak(int word) {

        this.communicateLock.acquire();

        while (hasSpoken) {
            this.listenCondition.wakeAll();
            this.speakCondition.sleep();
        }

        this.spokenWord = word;
        this.hasSpoken = true;

        this.listenCondition.wakeAll();
        this.speakCondition.sleep();

        this.communicateLock.release();
    }

    /**
     * Wait for a thread to speak through this communicator, and then return
     * the <i>word</i> that thread passed to <tt>speak()</tt>.
     *
     * @return the integer transferred.
     */
    public int listen() {

        this.communicateLock.acquire();

        while (!hasSpoken) {   /// I'm wrong U_U
            this.listenCondition.sleep();
        }

        int transferWord = this.spokenWord;
        hasSpoken = false;
        this.speakCondition.wakeAll();

        this.communicateLock.release();

        return transferWord;
    }

    private Lock communicateLock;
    private Condition2 speakCondition;
    private Condition2 listenCondition;
    private static final char dbgCommunicator = 'j';

    private int spokenWord;
    private boolean hasSpoken;


    public static void selfTest() {
        Lib.debug(dbgCommunicator, "Entering Communicator.selfTest");
        System.out.println("\n--------------------------------------");
        System.out.println("ENTERING TEST - Communicator.selfTest\n");

        Communicator communicator = new Communicator();


        for (int j = 0; j < 5; j++) {

            int finalJ = j;

            new KThread(new Runnable() {
                @Override
                public void run() {

                    for (int i = 0; i < 5; i++) {
                        System.out.println("Thread-" + finalJ + " is listening to hear its word-" + i);
                        int transfered = communicator.listen();
                        System.out.println("Thread-" + finalJ + " heard its word-" + i + ", which is the speaker's word-" + transfered);
                    }
                }
            }).fork();
        }

        KThread tempThread = new KThread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 5; i++) {
                    System.out.println("Thread-" + 6 + " speak(" + i + ")");
                    communicator.speak(i);
                }
            }
        });
        tempThread.fork();
        tempThread.join();

        System.out.println("\nEXITING TEST - Communicator.selfTest");
        System.out.println("--------------------------------------\n");
        Lib.debug(dbgCommunicator, "Exiting Communicator.selfTest");

    }


}
