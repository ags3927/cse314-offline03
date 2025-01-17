package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.util.LinkedList;

/**
 * A kernel that can support multiple user processes.
 */
public class UserKernel extends ThreadedKernel {
    /**
     * Allocate a new user kernel.
     */
    public UserKernel() {
        super();
    }

    /**
     * Initialize this kernel. Creates a synchronized console and sets the
     * processor's exception handler.
     */
    public void initialize(String[] args) {
        super.initialize(args);

        console = new SynchConsole(Machine.console());

        Machine.processor().setExceptionHandler(new Runnable() {
            public void run() {
                exceptionHandler();
            }
        });
        pageListLock = new Lock();
        for (int i = 0; i < numberOfPhysicalPages; i++)
            physicalPageList.add(i);
    }

    /**
     * Test the console device.
     */
    public void selfTest() {
        super.selfTest();

        System.out.println("Testing the console device. Typed characters");
        System.out.println("will be echoed until q is typed.");

        char c;

        do {
            c = (char) console.readByte(true);
            console.writeByte(c);
        }
        while (c != 'q');
        System.out.println();
    }

    /**
     * Returns the current process.
     *
     * @return the current process, or <tt>null</tt> if no process is current.
     */
    public static UserProcess currentProcess() {
        if (!(KThread.currentThread() instanceof UThread))
            return null;

        return ((UThread) KThread.currentThread()).process;
    }

    /**
     * The exception handler. This handler is called by the processor whenever
     * a user instruction causes a processor exception.
     *
     * <p>
     * When the exception handler is invoked, interrupts are enabled, and the
     * processor's cause register contains an integer identifying the cause of
     * the exception (see the <tt>exceptionZZZ</tt> constants in the
     * <tt>Processor</tt> class). If the exception involves a bad virtual
     * address (e.g. page fault, TLB miss, read-only, bus error, or address
     * error), the processor's BadVAddr register identifies the virtual address
     * that caused the exception.
     */
    public void exceptionHandler() {
        Lib.assertTrue(KThread.currentThread() instanceof UThread);

        UserProcess process = ((UThread) KThread.currentThread()).process;
        int cause = Machine.processor().readRegister(Processor.regCause);
        process.handleException(cause);
    }

    /**
     * Start running user programs, by creating a process and running a shell
     * program in it. The name of the shell program it must run is returned by
     * <tt>Machine.getShellProgramName()</tt>.
     *
     * @see    nachos.machine.Machine#getShellProgramName
     */
    public void run() {
        super.run();

        UserProcess process = UserProcess.newUserProcess();

        String shellProgram = Machine.getShellProgramName();

        Lib.assertTrue(process.execute(shellProgram, new String[]{}));

        KThread.finish();
    }

    /**
     * Terminate this kernel. Never returns.
     */
    public void terminate() {
        super.terminate();
    }

    // pageList access functions

    /**
     * Takes the first page from the list of available physical pages and returns its number.
     * This is used to fetch individual free physical pages for usage.
     * When a process requires more than one page, it will be required to call this method multiple times.
     * This enables the process to use the gaps created when pages are freed, since the pages are not necessarily
     * accessed in a block or sequential fashion.
     *
     * @return Returns a page number from the list of available physical pages. If not available, returns -1.
     */
    public static int fetchPhysicalPage() {
        int pageReturned = -1;
        pageListLock.acquire();

        if (physicalPageList.size() > 0) {
            pageReturned = physicalPageList.removeFirst();
        }

        pageListLock.release();
        return pageReturned;
    }

    /**
     * When a physical page is freed, it can be added to the list of physical pages added to the Kernel.
     * This method enables the Kernel to utilize the gaps created due to freeing of physical pages because they
     * are stored as a Linked list and never assessed in a sequential or block-wise fashion.
     *
     * @param pageNumber The number of the page which is to be added to the list of available physical pages
     * @return Upon successful addition of the freed page to the list of available physical pages, returns true.
     * Returns false if the pageNumber provided for addition is invalid.
     */
    public static boolean addPhysicalPage(int pageNumber) { // if its a faulty page number , should I throw an error? or ignore?
        boolean pageAdded = false;
        pageListLock.acquire();

        if (pageNumber >= 0 && pageNumber < numberOfPhysicalPages) {
            physicalPageList.add(pageNumber);
            pageAdded = true;
        }
        pageListLock.release();
        return pageAdded;
    }

    /**
     * Globally accessible reference to the synchronized console.
     */
    public static SynchConsole console;

    // dummy variables to make javac smarter
    private static Coff dummy1 = null;

    private static int numberOfPhysicalPages = Machine.processor().getNumPhysPages();   // fetch total available physical page count
    private static LinkedList<Integer> physicalPageList = new LinkedList<>();  // linked list to keep track of used and unused physical pages
    private static Lock pageListLock;  // lock to synchronize access in the linked list of pages
}
