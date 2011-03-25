package debie.harness;

import debie.support.DebieSystem;
import debie.support.KernelObjects;
import debie.support.Mailbox;
import debie.support.TaskControl;

public class TaskControlSim implements TaskControl {

	private DebieSystem system;
	
	TaskControlSim(DebieSystem system) {
		this.system = system;
	}
	
	private static final double MACHINE_CYCLE = 1.085;
	/* The machine (processor) cycle time, in microseconds. */

	// XXX: any decent compiler would replace this with compile-time constants
//	public int delayLimit(int time) { 
//		return (int)((((time) / MACHINE_CYCLE) - 4) / 2);
//	}
	// XXX: we use fixed-point arithmetic to avoid expensive soft-float
	public int delayLimit(int time) { 
		return ((((time << 16) / (int)(MACHINE_CYCLE*0x10000)) - 4) / 2);
	}	
	/* Computes the number of ShortDelay() argument-units that corresponds */
	/* to a certain delay TIME in microseconds. Note that this formula can */
	/* yield values larger than ShortDelay() can implement in one call.    */
	/* This formula is mainly intended for use with compile-time constant  */
	/* values for TIME.                                                    */

	public void shortDelay (int delay_loops) {
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
	public void waitInterval(int /* unsigned char */ time) {
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
	public void waitTimeout(int time) {
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
	public void waitInterrupt(byte isrVectorNumber, int timer) {
		if (Harness.TRACE)
			Harness.trace(String.format("[TaskControl] WaitInterrupt %d, time %d",
										(int)isrVectorNumber, timer));				
	}

	// extern void StartSystem(unsigned char task_number);
	
	/**
	 * Purpose        : Task is created in the RTX.
	 * Interface      : input:   - new_task
	 *                  output:  - telemetry_data.os_create_task_error
	 * Preconditions  : none
	 * Algorithm      : -In case of an error, 'new_task' is stored to telemetry
	 *                   as an error indication.
	 */
	public void createTask(int task_number) {
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
	
	public void enableInterruptMaster() {
		// NOP
	}
	
	public void disableInterruptMaster() {
		// NOP	
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
	public void attachInterrupt(int intr) {
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
	public void enableInterrupt(int intr) {
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
	public void disableInterrupt(int intr) {
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
	public int setInterruptMask(int mask) {
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
	public int resetInterruptMask(int mask) {
		if (Harness.TRACE) Harness.trace(String.format("ResetInterruptMask 0x%x", mask));
		return 0;  /* Success. */
	}
	
	/**
	 * Purpose        : Time slice in the RTX is set.
	 * Interface      : input:   - time_slice
	 *                  output:  - telemetry_data.os_set_slice_error
	 * Preconditions  : none
	 * Postconditions : Timeslice which defines the time interval in number of
	 *                  processor cycles is set.
	 * Algorithm      :  In case of an error, indication bit is set in
	 *                   the software_error register.
	 */
	public void setTimeSlice(int time_slice) {
		if (Harness.TRACE) Harness.trace(String.format("SetTimeSlice %d", time_slice));
	}
	
	public void clearHitTriggerISRFlag() {
		// NOP
	}
}
