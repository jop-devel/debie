package debie.support;

import debie.harness.HarnessMailbox;

public class TaskControl {

	public static final double MACHINE_CYCLE = 1.085;
	/* The machine (processor) cycle time, in microseconds. */

	public static final int DELAY_LIMIT(int TIME) { 
		return (int)((((TIME) / MACHINE_CYCLE) - 4) / 2);
	}
	/* Computes the number of ShortDelay() argument-units that corresponds */
	/* to a certain delay TIME in microseconds. Note that this formula can */
	/* yield values larger than ShortDelay() can implement in one call.    */
	/* This formula is mainly intended for use with compile-time constant  */
	/* values for TIME.                                                    */

	public static final int MAX_SHORT_DELAY = 255;
	/* The largest possible argument for ShortDelay(). */

	public static final int OK =     8;
	public static final int NOT_OK = 9;
	
//	typedef struct {
//	   unsigned char  rtx_task_number;
//	   void           (*task_main_function)(void);
//	} task_info_t;

	/* Function prototypes */

	int shortDelay (/* uint_least8_t */ int delay_loops) {
		// TODO Auto-generated method stub		
		return 0;
	}

	// void CreateTask(task_info_t EXTERNAL *new_task);

	public static void waitInterval(int /* unsigned char */ time) {
		// TODO Auto-generated method stub				
	}

	public static void waitTimeout(int delay) {
		// TODO Auto-generated method stub		
	}

	// extern void SetTimeSlice(unsigned int time_slice);

	// extern void StartSystem(unsigned char task_number);
	
	/* XXX: maybe move mailbox handling entirely to Mailbox/HarnessMailbox */
	private static Mailbox acqMailbox;
	private static Mailbox tctmMailbox;

	public static Mailbox getMailbox(byte id) {
		switch (id) {
		case KernelObjects.ACQUISITION_MAILBOX:
			return acqMailbox;
		case KernelObjects.TCTM_MAILBOX:
			return tctmMailbox;
		default:
			return null;
		}
	}
	
	public static void setMailbox(byte id, Mailbox box) {
		switch (id) {
		case KernelObjects.ACQUISITION_MAILBOX:
			acqMailbox = box;
		case KernelObjects.TCTM_MAILBOX:
			tctmMailbox = box;
		}		
	}
}
