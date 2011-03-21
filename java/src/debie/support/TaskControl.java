package debie.support;

import debie.harness.Harness;

public class TaskControl {

	private static DebieSystem system;
	
	public static DebieSystem getSystem() {
		return system;
	}
	public static void setSystem(DebieSystem sys) {
		system = sys;
	}
	
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

	public static void shortDelay (int delay_loops) {
		if (Harness.TRACE) Harness.trace(String.format("[TaskControl] ShortDelay %d", delay_loops));
		
		/* Any on-going A/D conversion is assumed to end during the delay.
		 * ShortDelay is sometimes used, instead of End_Of_ADC, when the
		 * A/D converter is switched between unipolar and bipolar modes.
		 */
		system.getAdcDevice().clearADConverting();
	}

	// void CreateTask(task_info_t EXTERNAL *new_task);

	public static void waitInterval(int /* unsigned char */ time) {
		if (Harness.TRACE) Harness.trace(String.format("[TaskControl] WaitInterval %d", time));				
	}

	public static void waitTimeout(int delay) {
		// TODO Auto-generated method stub		
	}
	
	public static void waitInterrupt(byte isrVectorNumber, int timer) {
		// TODO Auto-generated method stub
		
	}

	// extern void SetTimeSlice(unsigned int time_slice);

	// extern void StartSystem(unsigned char task_number);
	
	public static Mailbox getMailbox(byte id) {
		switch (id) {
		case KernelObjects.ACQUISITION_MAILBOX:
			return system.getAcqMailbox();
		case KernelObjects.TCTM_MAILBOX:
			return system.getTcTmMailbox();
		default:
			return null;
		}
	}
	
	/**
	 * Purpose        : Task is created in the RTX.
	 * Interface      : input:   - new_task
	 *                  output:  - telemetry_data.os_create_task_error
	 * Preconditions  : none
	 * Algorithm      : -In case of an error, 'new_task' is stored to telemetry
	 *                   as an error indication.
	 */
	public static void createTask(int task_number) {
		if (Harness.TRACE) Harness.trace(String.format("CreateTask %d", task_number));
		
		// XXX: initialization takes actually place in constructors
		
		switch (task_number) {

		   case KernelObjects.TC_TM_INTERFACE_TASK:

		      // TelecommandExecutionTask.init();

		      break;

		   case KernelObjects.ACQUISITION_TASK:

		      // AcquisitionTask.init();

		      break;

		   case KernelObjects.HIT_TRIGGER_ISR_TASK:

		      // HitTriggerTask.init();

		      break;

		   default:

				if (Harness.TRACE) Harness.trace("CreateTask: unknown task number");

		      break;
		   }
	}
}
