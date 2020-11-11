package nachos.threads;

import nachos.machine.*;

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
     * @param	word	the integer to transfer.
     */
    public void speak(int word) {

        this.communicateLock.acquire();

        while (hasSpoken){
            this.communicateLock.release();
            this.speakCondition.sleep();
            this.communicateLock.acquire();
        }

        this.spokenWord = word;
        this.hasSpoken = true;

        this.listenCondition.wakeAll(); /// wake all before lock release or after release? Not sure, is it same? whatever the sequence?

        this.communicateLock.release();
    }

    /**
     * Wait for a thread to speak through this communicator, and then return
     * the <i>word</i> that thread passed to <tt>speak()</tt>.
     *
     * @return	the integer transferred.
     */    
    public int listen() {

        this.communicateLock.acquire();

        while (!hasSpoken) {   /// I'm quite confused -_-
            this.communicateLock.release();
            this.listenCondition.sleep();
            this.communicateLock.acquire();
        }

        int transferWord = this.spokenWord;
        hasSpoken = false;

        this.speakCondition.wakeAll(); /// wake all before lock release or after release? Not sure, is it same? whatever the sequence?

        this.communicateLock.release();

        return transferWord;
    }

    private Lock communicateLock;
    private Condition2 speakCondition;
    private Condition2 listenCondition;

    private int spokenWord;
    private boolean hasSpoken;
}
