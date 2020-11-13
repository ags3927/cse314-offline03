/* halt.c
 *	Simple program to test whether running a user program works.
 *	
 *	Just do a "syscall" that shuts down the OS.
 *
 * 	NOTE: for some reason, user programs with global data structures 
 *	sometimes haven't worked in the Nachos environment.  So be careful
 *	out there!  One option is to allocate data structures as 
 * 	automatics within a procedure, but if you do this, you have to
 *	be careful to allocate a big enough stack to hold the automatics!
 */

#include "syscall.h"

void main()
{
    char b[10];
    readline(b, 10);
    char *execArgs[256];
    int status1,processID, processID1, processID2, status2;
    
    printf("\n\n********************************** mypgr Program Loading-test **********************************\n\n");
    printf("mypgr forking echo.coff and joining... \n");
    processID = exec("echo.coff", 1,  execArgs);
    int k = join(processID, &status1);
    printf("********* Join On Process %d Finished\nStatus Value:  %d    ***************\n", processID, status1);
    
//    printf("mypgr forking halt.coff first time and joining... \n");
    processID = exec("halt.coff", 1,  execArgs);


//    printf("mypgr forking halt.coff second time and joining... \n");
    processID1 = exec("halt.coff", 2,  execArgs);

    k = join(processID, &status1);
//    printf("********* Join On Process %d Finished\nStatus Value:  %d    ***************\n", processID, status1);

    int l = join(processID1, &status1);
//    printf("*********   Join On Process %d Finished\nStatus Value:  %d   ***************\n", processID1, status1);
    
    halt();
    /* not reached */
}
