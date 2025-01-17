package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.io.*;

import java.nio.Buffer;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Encapsulates the state of a user process that is not contained in its
 * user thread (or threads). This includes its address translation state, a
 * file table, and information about the program being executed.
 *
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
    /**
     * Allocate a new process.
     */
    public UserProcess() {
        int numPhysPages = Machine.processor().getNumPhysPages();
        pageTable = new TranslationEntry[numPhysPages];

        for (int i = 0; i < numPhysPages; i++) {
//            pageTable[i] = new TranslationEntry(i,i, true,false,false,false);
            // initialized pageTable for this process with invalid Translation entries
            pageTable[i] = new TranslationEntry(-1, -1, false, false, false, false);
        }

        processIDLock.acquire();
        processID = processCounter++;
        processIDLock.release();

        exitStatusLock = new Lock();

        parentProcess = null;
        childProcesses = new ArrayList<>();
        childProcessExitStatus = new HashMap<>();
    }


    /**
     * Allocate and return a new process of the correct class. The class name
     * is specified by the <tt>nachos.conf</tt> key
     * <tt>Kernel.processClassName</tt>.
     *
     * @return a new process of the correct class.
     */
    public static UserProcess newUserProcess() {
        return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
    }

    /**
     * Execute the specified program with the specified arguments. Attempts to
     * load the program, and then forks a thread to run it.
     *
     * @param name the name of the file containing the executable.
     * @param args the arguments to pass to the executable.
     * @return <tt>true</tt> if the program was successfully executed.
     */
    public boolean execute(String name, String[] args) {
        System.out.println("Shell program name = " + name);

        if (!load(name, args)) {
            System.out.println("Failed to load");
            return false;
        }

        processThread = (UThread) new UThread(this).setName(name);
        processThread.fork();

        return true;
    }

    /**
     * Save the state of this process in preparation for a context switch.
     * Called by <tt>UThread.saveState()</tt>.
     */
    public void saveState() {
    }

    /**
     * Restore the state of this process after a context switch. Called by
     * <tt>UThread.restoreState()</tt>.
     */
    public void restoreState() {
        Machine.processor().setPageTable(pageTable);
    }

    /**
     * Read a null-terminated string from this process's virtual memory. Read
     * at most <tt>maxLength + 1</tt> bytes from the specified address, search
     * for the null terminator, and convert it to a <tt>java.lang.String</tt>,
     * without including the null terminator. If no null terminator is found,
     * returns <tt>null</tt>.
     *
     * @param vaddr     the starting virtual address of the null-terminated
     *                  string.
     * @param maxLength the maximum number of characters in the string,
     *                  not including the null terminator.
     * @return the string read, or <tt>null</tt> if no null terminator was
     * found.
     */
    public String readVirtualMemoryString(int vaddr, int maxLength) {
        Lib.assertTrue(maxLength >= 0);

        byte[] bytes = new byte[maxLength + 1];

        int bytesRead = readVirtualMemory(vaddr, bytes);

        for (int length = 0; length < bytesRead; length++) {
            if (bytes[length] == 0)
                return new String(bytes, 0, length);
        }
        return null;
    }

    /**
     * Transfer data from this process's virtual memory to all of the specified
     * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param vaddr the first byte of virtual memory to read.
     * @param data  the array where the data will be stored.
     * @return the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data) {
        return readVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from this process's virtual memory to the specified array.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param vaddr  the first byte of virtual memory to read.
     * @param data   the array where the data will be stored.
     * @param offset the first byte to write in the array.
     * @param length the number of bytes to transfer from virtual memory to
     *               the array.
     * @return the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
        Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);

        if (numPages == 0) return 0;  // no physical page allocated

        byte[] memory = Machine.processor().getMemory();

        int transferredBytes = 0;
        int vaddrEnd = vaddr + length - 1;

        // virtual address must be within physical address limit
        if (vaddr < 0 || vaddrEnd > Processor.makeAddress(numPages - 1, pageSize - 1)) {
            return 0;
        }

        int startingPage = Processor.pageFromAddress(vaddr);
        int endingPage = Processor.pageFromAddress(vaddrEnd);

        for (int i = startingPage; i <= endingPage; i++) {
            if (i < 0 || i >= pageTable.length || !pageTable[i].valid) {
                transferredBytes = 0;
                break;
            }

            int startingAddressForThisPage = Processor.makeAddress(i, 0);
            int endingAddressForThisPage = Processor.makeAddress(i, pageSize - 1);

            int transferredBytesForThisPage = 0;
            int addressOffsetForThisPage;

            if (vaddr >= startingAddressForThisPage && vaddrEnd <= endingAddressForThisPage) {
                // this will hit the first page if total space required is less than pageSize
                addressOffsetForThisPage = vaddr - startingAddressForThisPage;
                transferredBytesForThisPage = length;

            } else if (vaddr >= startingAddressForThisPage) {
                // this will hit the first page if total space required is greater than pageSize
                addressOffsetForThisPage = vaddr - startingAddressForThisPage;
                transferredBytesForThisPage = endingAddressForThisPage - vaddr + 1;

            } else if (vaddrEnd <= endingAddressForThisPage) {
                // this will hit the last page
                addressOffsetForThisPage = 0;
                transferredBytesForThisPage = vaddrEnd - startingAddressForThisPage + 1;

            } else {
                // this will hit all intermediate pages that are neither the first page nor the last page
                addressOffsetForThisPage = 0;
                transferredBytesForThisPage = endingAddressForThisPage - startingAddressForThisPage + 1;
            }

            int physicalPageAddressForThisPage = Processor.makeAddress(pageTable[i].ppn, addressOffsetForThisPage);

            System.arraycopy(memory, physicalPageAddressForThisPage, data, offset + transferredBytes, transferredBytesForThisPage);

            transferredBytes += transferredBytesForThisPage;
        }

        return transferredBytes;
    }

    /**
     * Transfer all data from the specified array to this process's virtual
     * memory.
     * Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param vaddr the first byte of virtual memory to write.
     * @param data  the array containing the data to transfer.
     * @return the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data) {
        return writeVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from the specified array to this process's virtual memory.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param vaddr  the first byte of virtual memory to write.
     * @param data   the array containing the data to transfer.
     * @param offset the first byte to transfer from the array.
     * @param length the number of bytes to transfer from the array to
     *               virtual memory.
     * @return the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {

        Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);

        if (numPages == 0) return 0;  // no physical page allocated

        byte[] memory = Machine.processor().getMemory();

        int transferredBytes = 0;
        int vaddrEnd = vaddr + length - 1;

        if (vaddr < 0 || vaddrEnd > Processor.makeAddress(numPages - 1, pageSize - 1)) {
            return 0;  // virtual address must be within physical address limit
        }

        int startingPage = Processor.pageFromAddress(vaddr);
        int endingPage = Processor.pageFromAddress(vaddrEnd);

        for (int i = startingPage; i <= endingPage; i++) {
            if (i < 0 || i >= pageTable.length || !pageTable[i].valid) {
                transferredBytes = 0;
                break;
            }

            int startingAddressForThisPage = Processor.makeAddress(i, 0);
            int endingAddressForThisPage = Processor.makeAddress(i, pageSize - 1);

            int transferredBytesForThisPage = 0;
            int addressOffsetForThisPage;

            if (vaddr >= startingAddressForThisPage && vaddrEnd <= endingAddressForThisPage) {
                // this will hit the first page if total space required is less than pageSize
                addressOffsetForThisPage = vaddr - startingAddressForThisPage;
                transferredBytesForThisPage = length;

            } else if (vaddr >= startingAddressForThisPage) {
                // this will hit the first page if total space required is greater than pageSize
                addressOffsetForThisPage = vaddr - startingAddressForThisPage;
                transferredBytesForThisPage = endingAddressForThisPage - vaddr + 1;

            } else if (vaddrEnd <= endingAddressForThisPage) {
                // this will hit the last page
                addressOffsetForThisPage = 0;
                transferredBytesForThisPage = vaddrEnd - startingAddressForThisPage + 1;

            } else {
                // this will hit all intermediate pages that are neither the first page nor the last page
                addressOffsetForThisPage = 0;
                transferredBytesForThisPage = endingAddressForThisPage - startingAddressForThisPage + 1;
            }

            int physicalPageAddressForThisPage = Processor.makeAddress(pageTable[i].ppn, addressOffsetForThisPage);

            System.arraycopy(data, offset + transferredBytes, memory, physicalPageAddressForThisPage, transferredBytesForThisPage);

            transferredBytes += transferredBytesForThisPage;
        }

        return transferredBytes;
    }

    /**
     * This method takes the virtual page number of the first page to be allocated for a section, for which
     * this method has been called. It attempts to allocate a certain number of pages to this section.
     * It fetches the required number of pages by proportional number of calls to the <b>UserKernel.fetchPhysicalPage</b> method.
     * If any call to this method returns -1, then the number of pages required for this section is not available.
     * In that case, this method returns false.
     *
     * @param virtualPageNumber The virtual page number of the first page that is to be allocated for a section
     * @param sectionLength     The number of virtual pages that are to be allocated for a section
     * @param readOnly          Whether the section is read only
     * @return Upon successful allocation of pages, returns true. Returns false otherwise.
     */
    private boolean tryAllocate(int virtualPageNumber, int sectionLength, boolean readOnly) {
        if (virtualPageNumber + sectionLength - 1 >= pageTable.length) {
            return false;
        }

        for (int i = 0; i < sectionLength; i++) {
            int physicalPage = UserKernel.fetchPhysicalPage();
            if (physicalPage == -1) {
                return false;
            }
            pageTable[virtualPageNumber + i] = new TranslationEntry(virtualPageNumber + i, physicalPage, true, readOnly, false, false);
            numPages++;
        }
        return true;
    }

    /**
     * Load the executable with the specified name into this process, and
     * prepare to pass it the specified arguments. Opens the executable, reads
     * its header information, and copies sections and arguments into this
     * process's virtual memory.
     * It initializes 8 pages as the stack for this process and 1 page for arguments.
     *
     * @param name the name of the file containing the executable.
     * @param args the arguments to pass to the executable.
     * @return <tt>true</tt> if the executable was successfully loaded.
     */
    private boolean load(String name, String[] args) {
        Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

        OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
        if (executable == null) {
            Lib.debug(dbgProcess, "\topen failed");
//            System.out.println("Failed to open file");
            return false;
        }

        try {
            coff = new Coff(executable);
        } catch (EOFException e) {
            executable.close();
//            System.out.println("Failed to load coff");
            Lib.debug(dbgProcess, "\tcoff load failed");
            return false;
        }

        // make sure the sections are contiguous and start at page 0

        // trying to allocate physical pages for each section of the COFF, if failed clearing them all

        numPages = 0;

        for (int s = 0; s < coff.getNumSections(); s++) {
            CoffSection section = coff.getSection(s);
            if (section.getFirstVPN() != numPages) {
                coff.close();
                Lib.debug(dbgProcess, "\tfragmented executable");
                return false;
            }

            boolean hasAllocated = tryAllocate(numPages, section.getLength(), section.isReadOnly());

            if (!hasAllocated) {
                unloadSections();
                return false;
            }
        }

        // make sure the argv array will fit in one page
        byte[][] argv = new byte[args.length][];
        int argsSize = 0;

        for (int i = 0; i < args.length; i++) {
            argv[i] = args[i].getBytes();
            // 4 bytes for argv[] pointer; then string plus one for null byte
            argsSize += 4 + argv[i].length + 1;
        }

        if (argsSize > pageSize) {
            coff.close();
            Lib.debug(dbgProcess, "\targuments too long");
            return false;
        }

        // program counter initially points at the program entry point
        initialPC = coff.getEntryPoint();

        // next comes the stack; stack pointer initially points to top of it. stack is never readOnly
        boolean hasAllocatedStack = tryAllocate(numPages, stackPages, false);

        if (!hasAllocatedStack) {
            unloadSections();
            return false;
        }

        initialSP = numPages * pageSize;

        // and finally reserve 1 page for arguments, which can never be readOnly either
        boolean hasAllocatedArguments = tryAllocate(numPages, 1, false);

        if (!hasAllocatedArguments) {
            unloadSections();
            return false;
        }

        if (!loadSections())
            return false;

        // store arguments in last page
        int entryOffset = (numPages - 1) * pageSize;
        int stringOffset = entryOffset + args.length * 4;

        this.argc = args.length;
        this.argv = entryOffset;

        for (int i = 0; i < argv.length; i++) {
            byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
            Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);

            entryOffset += 4;
            Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) ==
                    argv[i].length);
            stringOffset += argv[i].length;

            Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[]{0}) == 1);

            stringOffset += 1;
        }

        return true;
    }

    /**
     * Allocates memory for this process, and loads the COFF sections into
     * memory. If this returns successfully, the process will definitely be
     * run (this is the last step in process initialization that can fail).
     *
     * @return <tt>true</tt> if the sections were successfully loaded.
     */
    protected boolean loadSections() {
        if (numPages > Machine.processor().getNumPhysPages()) {
            coff.close();
            Lib.debug(dbgProcess, "\tinsufficient physical memory");
            return false;
        }

        // load sections

        for (int s = 0; s < coff.getNumSections(); s++) {

            CoffSection section = coff.getSection(s);

            Lib.debug(dbgProcess, "\tinitializing " + section.getName()
                    + " section (" + section.getLength() + " pages)");

            for (int i = 0; i < section.getLength(); i++) {
                int vpn = section.getFirstVPN() + i;
                section.loadPage(i, pageTable[vpn].ppn);
            }
        }

        return true;
    }

    /**
     * This method frees all pages that have been previously allocated for this UserProcess.
     * For each allocated page, this method calls <b>UserKernel.addPhysicalPage</b> to add the
     * newly freed page to the Kernel's list of available physical pages.
     * <p>
     * Release any resources allocated by <tt>loadSections()</tt>.
     */
    protected void unloadSections() {
        for (int i = 0; i < pageTable.length; i++) {
            if (pageTable[i].valid) {
                UserKernel.addPhysicalPage(pageTable[i].ppn);

                pageTable[i].valid = false;
                pageTable[i].readOnly = false;
                pageTable[i].vpn = -1;
                pageTable[i].ppn = -1;
            }
        }
        // Enforces the knowledge that currently no pages are allocated for this UserProcess
        numPages = 0;
    }

    /**
     * Initialize the processor's registers in preparation for running the
     * program loaded into this process. Set the PC register to point at the
     * start function, set the stack pointer register to point at the top of
     * the stack, set the A0 and A1 registers to argc and argv, respectively,
     * and initialize all other registers to 0.
     */
    public void initRegisters() {
        Processor processor = Machine.processor();

        // by default, everything's 0
        for (int i = 0; i < Processor.numUserRegisters; i++)
            processor.writeRegister(i, 0);

        // initialize PC and SP according
        processor.writeRegister(Processor.regPC, initialPC);
        processor.writeRegister(Processor.regSP, initialSP);

        // initialize the first two argument registers to argc and argv
        processor.writeRegister(Processor.regA0, argc);
        processor.writeRegister(Processor.regA1, argv);
    }

    /**
     * Handle the halt() system call.
     */
    private int handleHalt() {
        if (processID != ROOT_PROCESS) return -1;

        Machine.halt();

        Lib.assertNotReached("Machine.halt() did not halt machine!");

        return 0;
    }

    /**
     * Attempt to read up to count bytes into buffer from the file or stream
     * referred to by fileDescriptor.
     * <p>
     * On success, the number of bytes read is returned. If the file descriptor
     * refers to a file on disk, the file position is advanced by this number.
     * <p>
     * It is not necessarily an error if this number is smaller than the number of
     * bytes requested. If the file descriptor refers to a file on disk, this
     * indicates that the end of the file has been reached. If the file descriptor
     * refers to a stream, this indicates that the fewer bytes are actually
     * available right now than were requested, but more bytes may become available
     * in the future. Note that read() never waits for a stream to have more data;
     * it always returns as much as possible immediately.
     * <p>
     * On error, -1 is returned, and the new file position is undefined. This can
     * happen if fileDescriptor is invalid, if part of the buffer is read-only or
     * invalid, or if a network stream has been terminated by the remote host and
     * no more data is available.
     *
     * @param fileDescriptor       the integer indexing the file from which to read
     * @param virtualMemoryAddress the virtual memory address where the read bytes are to be stored
     * @param byteCount            the number of bytes to be read
     * @return Returns -1 upon failure. Returns the number of bytes that have been read upon success.
     */
    private int handleRead(int fileDescriptor, int virtualMemoryAddress, int byteCount) {
        if (fileDescriptor > 1 || fileDescriptor < 0 || byteCount < 0) return -1;

        if (virtualMemoryAddress < 0) return 0;

        byte[] buffer = new byte[byteCount];

        int bytesRead = fileDescriptor == 0 ? inputStream.read(buffer, 0, byteCount) : -1;

        if ((bytesRead * bytesRead + bytesRead) == 0) {
            return -1;
        }

        return writeVirtualMemory(virtualMemoryAddress, buffer, 0, bytesRead);
    }


    /**
     * Attempt to write up to count bytes from buffer to the file or stream
     * referred to by fileDescriptor. write() can return before the bytes are
     * actually flushed to the file or stream. A write to a stream can block,
     * however, if kernel queues are temporarily full.
     * <p>
     * On success, the number of bytes written is returned (zero indicates nothing
     * was written), and the file position is advanced by this number. It IS an
     * error if this number is smaller than the number of bytes requested. For
     * disk files, this indicates that the disk is full. For streams, this
     * indicates the stream was terminated by the remote host before all the data
     * was transferred.
     * <p>
     * On error, -1 is returned, and the new file position is undefined. This can
     * happen if fileDescriptor is invalid, if part of the buffer is invalid, or
     * if a network stream has already been terminated by the remote host.
     *
     * @param fileDescriptor       the integer indexing the file to which the data is to be written
     * @param virtualMemoryAddress the virtual memory address where the bytes to be written are stored
     * @param byteCount            the number of bytes to be written
     * @return Returns -1 upon failure. Returns the number of bytes that have been written upon success.
     */
    private int handleWrite(int fileDescriptor, int virtualMemoryAddress, int byteCount) {
        if (fileDescriptor > 1 || fileDescriptor < 0 || byteCount < 0) return -1;

        byte[] buffer = new byte[byteCount];

        int bytesWritten = fileDescriptor == 0 ? inputStream.write(buffer, 0, readVirtualMemory(virtualMemoryAddress, buffer, 0, byteCount))
                : outputStream.write(buffer, 0, readVirtualMemory(virtualMemoryAddress, buffer, 0, byteCount));

        return (bytesWritten * (bytesWritten - byteCount)) == 0 ? bytesWritten : -1;
    }

    /**
     * Execute the program stored in the specified file, with the specified
     * arguments, in a new child process. The child process has a new unique
     * process ID, and starts with stdin opened as file descriptor 0, and stdout
     * opened as file descriptor 1.
     * <p>
     * file is a null-terminated string that specifies the name of the file
     * containing the executable. Note that this string must include the ".coff"
     * extension.
     * <p>
     * argc specifies the number of arguments to pass to the child process. This
     * number must be non-negative.
     * <p>
     * argv is an array of pointers to null-terminated strings that represent the
     * arguments to pass to the child process. argv[0] points to the first
     * argument, and argv[argc-1] points to the last argument.
     * <p>
     * exec() returns the child process's process ID, which can be passed to
     * join(). On error, returns -1.
     *
     * @param fileNameVirtualAddress     The virtual address of the location where the file name is stored
     * @param argc                       The number of arguments to be passed
     * @param argvStartingVirtualAddress The virtual address of the location where the virtual address
     *                                   of the first argument is stored
     * @return The processID of the child process created within this method
     */
    private int handleExec(int fileNameVirtualAddress, int argc, int argvStartingVirtualAddress) {
        if (fileNameVirtualAddress < 0) {
            Lib.debug(dbgProcess, "handleExec: Invalid virtual address for filename");
            return -1;
        }

        if (argc < 0) {
            Lib.debug(dbgProcess, "handleExec: Invalid number of arguments");
            return -1;
        }

        if (argvStartingVirtualAddress < 0) {
            Lib.debug(dbgProcess, "handleExec: Invalid virtual address for the virtual address of first argument");
            return -1;
        }

        String fileName = readVirtualMemoryString(fileNameVirtualAddress, MAX_STRING_SIZE);

        if (fileName == null) {
            Lib.debug(dbgProcess, "handleExec: File not found");
            return -1;
        }

        if (!fileName.endsWith(".coff")) {
            Lib.debug(dbgProcess, "handleExec: Incorrect file format");
            return -1;
        }

        String[] arguments = new String[argc];

        for (int i = 0; i < argc; i++) {
            byte[] argAddressBuffer = new byte[4];

            if (readVirtualMemory(argvStartingVirtualAddress + i * 4, argAddressBuffer) != 4) {
                Lib.debug(dbgProcess, "handleExec: Invalid virtual address for argument");
                return -1;
            }
            int argVirtualAddress = Lib.bytesToInt(argAddressBuffer, 0);

            String argument = readVirtualMemoryString(argVirtualAddress, 256);

            if (argument == null) {
                Lib.debug(dbgProcess, "handleExec: Argument is null");
                return -1;
            }

            arguments[i] = argument;
        }

        UserProcess childProcess = UserProcess.newUserProcess();

        if (!childProcess.execute(fileName, arguments)) {
            Lib.debug(dbgProcess, "handleExec: Could not execute in child process");
        }

        childProcess.parentProcess = this;

        childProcesses.add(childProcess);

        return childProcess.processID;
    }

    /**
     * Suspend execution of the current process until the child process specified
     * by the processID argument has exited. If the child has already exited by the
     * time of the call, returns immediately. When the current process resumes, it
     * disowns the child process, so that join() cannot be used on that process
     * again.
     * <p>
     * processID is the process ID of the child process, returned by exec().
     * <p>
     * status points to an integer where the exit status of the child process will
     * be stored. This is the value the child passed to exit(). If the child exited
     * because of an unhandled exception, the value stored is not defined.
     * <p>
     * If the child exited normally, returns 1. If the child exited as a result of
     * an unhandled exception, returns 0. If processID does not refer to a child
     * process of the current process, returns -1.
     */
    private int handleJoin(int childProcessID, int virtualAddressOfChildExitStatus) {
        if (processID < 0) {
            Lib.debug(dbgProcess, "handleJoin: Invalid ID for child process");
            return -1;
        }

        if (virtualAddressOfChildExitStatus < 0) {
            Lib.debug(dbgProcess, "handleJoin: Invalid virtual address for storing the exit status of child process");
            return -1;
        }

        UserProcess childProcess = null;

        int numberOfChildProcesses = childProcesses.size();

        for (int i = 0; i < numberOfChildProcesses; i++) {
            if (childProcesses.get(i).processID == childProcessID) {
                childProcess = childProcesses.get(i);
                break;
            }
        }

        if (childProcess == null) {
            Lib.debug(dbgProcess, "handleJoin: Child process with provided processID does not exist");
            return -1;
        }

        childProcess.processThread.join();
        childProcess.parentProcess = null;
        childProcesses.remove(childProcess);

        exitStatusLock.acquire();
        Integer status = childProcessExitStatus.get(childProcessID);
        exitStatusLock.release();

        if (status == null) {
            Lib.debug(dbgProcess, "handleJoin: Child process exited through an unhandled exception");
            return 0;
        } else {
            if (writeVirtualMemory(virtualAddressOfChildExitStatus, Lib.bytesFromInt(status)) == 4) {
                return 1;
            } else {
                Lib.debug(dbgProcess, "handleJoin: Could not write exit status of child to specified virtual address");
                return 0;
            }
        }
    }

    /**
     * Terminate the current process immediately. Any open file descriptors
     * belonging to the process are closed. Any children of the process no longer
     * have a parent process.
     * <p>
     * status is returned to the parent process as this process's exit status and
     * can be collected using the join syscall. A process exiting normally should
     * (but is not required to) set status to 0.
     * <p>
     * exit() never returns.
     *
     * @param status The exit status of the current process
     */
    private void handleExit(int status) {

        if (parentProcess != null) {
            parentProcess.exitStatusLock.acquire();
            parentProcess.childProcessExitStatus.put(processID, status);
            parentProcess.exitStatusLock.release();
        }

        unloadSections();

        int numberOfChildProcesses = childProcesses.size();

        for (int i = 0; i < numberOfChildProcesses; i++) {
            UserProcess childProcess = childProcesses.remove(0);
            childProcess.parentProcess = null;
        }

        if (processID == 0) {
            Kernel.kernel.terminate();
        } else {
            UThread.finish();
        }
    }

    private static final int
            syscallHalt = 0,
            syscallExit = 1,
            syscallExec = 2,
            syscallJoin = 3,
            syscallCreate = 4,
            syscallOpen = 5,
            syscallRead = 6,
            syscallWrite = 7,
            syscallClose = 8,
            syscallUnlink = 9;

    /**
     * Handle a syscall exception. Called by <tt>handleException()</tt>. The
     * <i>syscall</i> argument identifies which syscall the user executed:
     *
     * <table>
     * <tr><td>syscall#</td><td>syscall prototype</td></tr>
     * <tr><td>0</td><td><tt>void halt();</tt></td></tr>
     * <tr><td>1</td><td><tt>void exit(int status);</tt></td></tr>
     * <tr><td>2</td><td><tt>int  exec(char *name, int argc, char **argv);
     * 								</tt></td></tr>
     * <tr><td>3</td><td><tt>int  join(int pid, int *status);</tt></td></tr>
     * <tr><td>4</td><td><tt>int  creat(char *name);</tt></td></tr>
     * <tr><td>5</td><td><tt>int  open(char *name);</tt></td></tr>
     * <tr><td>6</td><td><tt>int  read(int fd, char *buffer, int size);
     * 								</tt></td></tr>
     * <tr><td>7</td><td><tt>int  write(int fd, char *buffer, int size);
     * 								</tt></td></tr>
     * <tr><td>8</td><td><tt>int  close(int fd);</tt></td></tr>
     * <tr><td>9</td><td><tt>int  unlink(char *name);</tt></td></tr>
     * </table>
     *
     * @param syscall the syscall number.
     * @param a0      the first syscall argument.
     * @param a1      the second syscall argument.
     * @param a2      the third syscall argument.
     * @param a3      the fourth syscall argument.
     * @return the value to be returned to the user.
     */
    public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
        switch (syscall) {
            case syscallHalt:
                return handleHalt();
            case syscallExit:
                handleExit(a0);
            case syscallExec:
                return handleExec(a0, a1, a2);
            case syscallJoin:
                return handleJoin(a0, a1);
            case syscallRead:
                return handleRead(a0, a1, a2);
            case syscallWrite:
                return handleWrite(a0, a1, a2);
            default:
                Lib.debug(dbgProcess, "Unknown syscall " + syscall);
                Lib.assertNotReached("Unknown system call!");
        }
        return 0;
    }

    /**
     * Handle a user exception. Called by
     * <tt>UserKernel.exceptionHandler()</tt>. The
     * <i>cause</i> argument identifies which exception occurred; see the
     * <tt>Processor.exceptionZZZ</tt> constants.
     *
     * @param cause the user exception that occurred.
     */
    public void handleException(int cause) {
        Processor processor = Machine.processor();

        switch (cause) {
            case Processor.exceptionSyscall:
                int result = handleSyscall(processor.readRegister(Processor.regV0),
                        processor.readRegister(Processor.regA0),
                        processor.readRegister(Processor.regA1),
                        processor.readRegister(Processor.regA2),
                        processor.readRegister(Processor.regA3)
                );
                processor.writeRegister(Processor.regV0, result);
                processor.advancePC();
                break;

            default:
                Lib.debug(dbgProcess, "Unexpected exception: " +
                        Processor.exceptionNames[cause]);
                Lib.assertNotReached("Unexpected exception");
        }
    }

    /**
     * The program being run by this process.
     */
    protected Coff coff;

    /**
     * This process's page table.
     */
    protected TranslationEntry[] pageTable;
    /**
     * The number of contiguous pages occupied by the program.
     */
    protected int numPages;

    /**
     * The number of pages in the program's stack.
     */
    protected final int stackPages = 8;

    private int initialPC, initialSP;
    private int argc, argv;

    private static final int pageSize = Processor.pageSize;
    private static final char dbgProcess = 'a';


    // Task-1 Variables
    private final OpenFile inputStream = UserKernel.console.openForReading();
    private final OpenFile outputStream = UserKernel.console.openForWriting();
    private static int processCounter = 0;
    private int processID;
    private static final int ROOT_PROCESS = 0;
    private static final int MAX_STRING_SIZE = 64;

    protected Lock processIDLock = new Lock();

    // Task-3 Variables
    private ArrayList<UserProcess> childProcesses;
    private UserProcess parentProcess;
    private Lock exitStatusLock;
    private HashMap<Integer, Integer> childProcessExitStatus;
    protected UThread processThread;
}
