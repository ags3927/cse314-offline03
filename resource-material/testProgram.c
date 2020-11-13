#include "syscall.h"

void main()
{
    char b[10];
    readline (b,10);
    char *execArgs[256];
    int status1, processID;

    printf("\n\n**************** Test Program Loading Test **************** \n\n"); 
    printf("testProgram forking echo.coff and joining... \n");

    processID = exec("echo.coff", 1 execArgs);

    int k = join(processID, &status1); 
    printf("********* Join On Process %d Finished \nStatus Value: %d   ********************\n", processID,status1);

    printf("testProgram forking halt.coff and joining... \n");

    processID = exec("halt.coff", 1, execArgs);
    k = join(processID, &status1);

    printf("******* Join On Process %d Finished\nStatus Value: %d   ****************** \n", processID, status1);

    halt();

//not reached
}
