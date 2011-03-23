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

	/**
	 * Purpose        : Interval is waited with RTX.
	 * Interface      : input:   - time
	 *                  output:  - telemetry_data.os_wait_error
	 * Preconditions  : none
	 * Postconditions : Interval for wait is set.
	 * Algorithm      : -In case of an error, 'K_IVL' is stored to telemetry
	 *                   as an error indication and error bit is set in
	 *                   software_error register.
	 */
	public static void waitInterval(int /* unsigned char */ time) {
		if (Harness.TRACE) Harness.trace(String.format("[TaskControl] WaitInterval %d", time));
	}

	/**
	 * Purpose        : Timeout is waited with RTX.
	 * Interface      : input:   - time
	 *                  output:  - telemetry_data.os_wait_error
	 * Preconditions  : none
	 * Postconditions : Specified time has elapsed.
	 * Algorithm      : -In case of an error, 'K_TMO' is stored to telemetry
	 *                   as an error indication and error bit is set in
	 *                   software_error register.
	 */
	public static void waitTimeout(int time) {
		if (Harness.TRACE) Harness.trace(String.format("[TaskControl] WaitTimeout %d", time));		
	}
	
	/**
	 * Purpose        : Interrupt is waited in the RTX.
	 * Interface      : input:   - ISR_VectorNumber,timer
	 *                  output:  - telemetry_data.os_wait_error
	 * Preconditions  : none
	 * Postconditions : Interrupt is waited.
	 * Postconditions : Interrupt is enabled.
	 * Algorithm      : -In case of an error, 'K_INT' is stored to telemetry 
	 *                   as an error indication and error bit is set in
	 *                   software_error register.
	 */
	public static void waitInterrupt(byte isrVectorNumber, int timer) {
		if (Harness.TRACE)
			Harness.trace(String.format("[TaskControl] WaitInterrupt %d, time %d",
										(int)isrVectorNumber, timer));				
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
	
	public static void enableInterruptMaster() {
		// TODO Auto-generated method stub		
	}
	
	public static void disableInterruptMaster() {
		// TODO Auto-generated method stub	
	}

	/**
	 * Purpose        : Interrupt with a given number is assigned to a task in
	 * Interface      : input:   - ISR_VectorNumber
	 *                  output:  - telemetry_data.os_attach_interrupt_error
	 * Preconditions  : none
	 * Postconditions : Interrupt is attached to a calling task.
	 * Algorithm      : -In case of an error, 'ISR_VectorNumber' is stored to
	 *                   telemetry as an error indication.
	 */
	public static void attachInterrupt(int intr) {
		if (Harness.TRACE) Harness.trace(String.format("AttachInterrupt %d", intr)); 		
	}

	/**
	 * Purpose        : Interrupt with a given number is enabled in the RTX.
	 * Interface      : input:   - ISR_VectorNumber
	 *                  output:  - telemetry_data.os_enable_isr_error
	 * Preconditions  : none
	 * Postconditions : Interrupt is enabled.
	 * Algorithm      : -In case of an error, 'ISR_VectorNumber' is stored to
	 *                   telemetry as an error indication.
	 */
	public static void enableInterrupt(int intr) {
		if (Harness.TRACE) Harness.trace(String.format("EnableInterrupt %d", intr)); 		
	}
	
	/**
	 * Purpose        : Interrupt with a given number is disabled in the RTX.
	 * Interface      : input:   - ISR_VectorNumber
	 *                  output:  - telemetry_data.os_disable_isr_error
	 * Preconditions  : none
	 * Postconditions : Interrupt is enabled.
	 * Algorithm      : -In case of an error, 'ISR_VectorNumber' is stored to
	 *                   telemetry as an error indication.
	 */
	public static void disableInterrupt(int intr) {
		if (Harness.TRACE) Harness.trace(String.format("DisableInterrupt %d", intr));		
	}
	
	/**
	 * Purpose        : Interrupt mask bit is set is in the RTX.
	 * Interface      : Return value, which describes the execution result, is
	 *                  always zero as this function does no parameter checking.
	 *                  Used to manipulate special bits, which are part of the
	 *                  interrupt enable registers or to modify interrupt enable
	 *                  bits from inside a C51 interrupt function.
	 *                  Not to be used for interrupt sources attached to RTX51
	 *                  tasks.
	 * Preconditions  :
	 * Postconditions : Interrupt mask is set.
	 * Algorithm      : RTX syntax is used.
	 */
	public static int setInterruptMask(int mask) {
		if (Harness.TRACE) Harness.trace(String.format("SetInterruptMask 0x%x", mask));
		return 0;  /* Success. */
	}
	
	/**
	 * Purpose        : Interrupt mask bit is reset is in the RTX.
	 * Interface      : Return value, which describes the execution result, is
	 *                  always zero as this function does no parameter checking.
	 *                  Used to manipulate special bits, which are part of the
	 *                  interrupt enable registers or to modify interrupt enable
	 *                  bits from inside a C51 interrupt function.
	 *                  Not to be used for interrupt sources attached to RTX51
	 *                  tasks.
	 * Preconditions  :
	 * Postconditions : Interrupt mask is reset.
	 * Algorithm      : RTX syntax is used.
	 */
	public static int resetInterruptMask(int mask) {
		if (Harness.TRACE) Harness.trace(String.format("ResetInterruptMask 0x%x", mask));
		return 0;  /* Success. */
	}
}
