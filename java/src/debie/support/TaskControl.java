package debie.support;

public interface TaskControl {

	public int delayLimit(int time);

	/* Computes the number of ShortDelay() argument-units that corresponds */
	/* to a certain delay TIME in microseconds. Note that this formula can */
	/* yield values larger than ShortDelay() can implement in one call.    */
	/* This formula is mainly intended for use with compile-time constant  */
	/* values for TIME.                                                    */

	public static final int MAX_SHORT_DELAY = 255;
	/* The largest possible argument for ShortDelay(). */
	public static final int OK = 8;
	public static final int NOT_OK = 9;

	public void shortDelay(int delay_loops);

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
	public void waitInterval(int /* unsigned char */time);

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
	public void waitTimeout(int time);

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
	public void waitInterrupt(byte isrVectorNumber, int timer);

	public Mailbox getMailbox(byte id);

	/**
	 * Purpose        : Task is created in the RTX.
	 * Interface      : input:   - new_task
	 *                  output:  - telemetry_data.os_create_task_error
	 * Preconditions  : none
	 * Algorithm      : -In case of an error, 'new_task' is stored to telemetry
	 *                   as an error indication.
	 */
	public void createTask(int task_number);

	public void enableInterruptMaster();

	public void disableInterruptMaster();

	/**
	 * Purpose        : Interrupt with a given number is assigned to a task in
	 * Interface      : input:   - ISR_VectorNumber
	 *                  output:  - telemetry_data.os_attach_interrupt_error
	 * Preconditions  : none
	 * Postconditions : Interrupt is attached to a calling task.
	 * Algorithm      : -In case of an error, 'ISR_VectorNumber' is stored to
	 *                   telemetry as an error indication.
	 */
	public void attachInterrupt(int intr);

	/**
	 * Purpose        : Interrupt with a given number is enabled in the RTX.
	 * Interface      : input:   - ISR_VectorNumber
	 *                  output:  - telemetry_data.os_enable_isr_error
	 * Preconditions  : none
	 * Postconditions : Interrupt is enabled.
	 * Algorithm      : -In case of an error, 'ISR_VectorNumber' is stored to
	 *                   telemetry as an error indication.
	 */
	public void enableInterrupt(int intr);

	/**
	 * Purpose        : Interrupt with a given number is disabled in the RTX.
	 * Interface      : input:   - ISR_VectorNumber
	 *                  output:  - telemetry_data.os_disable_isr_error
	 * Preconditions  : none
	 * Postconditions : Interrupt is enabled.
	 * Algorithm      : -In case of an error, 'ISR_VectorNumber' is stored to
	 *                   telemetry as an error indication.
	 */
	public void disableInterrupt(int intr);

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
	public int setInterruptMask(int mask);

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
	public int resetInterruptMask(int mask);

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
	public void setTimeSlice(int time_slice);

	public void clearHitTriggerISRFlag();

}