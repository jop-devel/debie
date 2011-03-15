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

	static void waitInterval(int /* unsigned char */ time) {
		// TODO Auto-generated method stub				
	}

	public static void waitTimeout(int delay) {
		// TODO Auto-generated method stub		
	}

	// extern void SetTimeSlice(unsigned int time_slice);

	// extern void StartSystem(unsigned char task_number);

//	extern void SendTaskMail (
//	   unsigned char mailbox, 
//	   uint16_t      message,
//	   unsigned char timeout);



	// FIXME: Need to think about global mailbox access
	public static /* unsigned char */ int isrSendMessage(int /*unsigned char */ mailbox,
			int  /* uint16_t */ message) {
		// FIXME: hack!!
		if(HarnessMailbox.sendMessageTo(mailbox, message)) return OK;
		else return NOT_OK;
	}
}
