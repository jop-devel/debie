package debie.support;

public class KernelObjects {

	public static final byte HEALTH_MONITORING_TASK = 0;
	public static final byte TC_TM_INTERFACE_TASK = 1;
	public static final byte ACQUISITION_TASK = 2;

	public static final byte HIT_TRIGGER_ISR_TASK = 3;

	/* Task priorities */

	public static final byte HEALTH_MONITORING_PR = 0;
	public static final byte ACQUISITION_PR = 1;
	public static final byte TC_TM_INTERFACE_PR = 2;

	public static final byte HIT_TRIGGER_PR = 3;

	/* Mailbox numbers */

	public static final byte TCTM_MAILBOX = 0;
	public static final byte ACQUISITION_MAILBOX = 1;

	/* ISR source numbers */

	public static final byte TC_ISR_SOURCE = 0;
	public static final byte TM_ISR_SOURCE = 2;
	public static final byte HIT_TRIGGER_ISR_SOURCE = 5;
}
