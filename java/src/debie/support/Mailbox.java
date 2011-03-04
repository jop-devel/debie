package debie.support;

public abstract class Mailbox {

	/** The value of execution_result in incoming_mail_t that
	 * signifies that a mail message has been received.     
	 * Must be different from NOT_OK as defined in RTX51.h. */
	public static final int MSG_RECEIVED = 1;

	/** The value of execution_result in incoming_mail_t that
	 * signifies that the wait for mail has timed out.
	 * Must be different from NOT_OK as defined in RTX51.h. */
	public static final int TIMEOUT_OCCURRED = 4;
		
	public abstract void sendTaskMail(char message, byte timeout);

	public abstract void waitMail();

	/* Fields of struct incoming_mail_t of the C implementation */
	/* XXX: should rethink access rules (public vs private+getters/setters, final) */

	/** Id for this mailbox. Probably could be final */
	/* XXX: was unsigned char */
	public byte mailbox_number;

	/* XXX: was unsigned char */
	public byte timeout;

	/** Actual message */
	/* XXX: why the hell is this a pointer in the C implementation? */
	/* XXX: was uint16_t* */
	public char message;

	/** This variable is used to indicate execution results. */
	/* XXX: was signed char */
	public byte execution_result;

	/** Result from a RTX operation. */
	/* XXX: was signed char */
	public byte wait_result;
 
	/** The value of this variable defines the
	 * execution of the wait-task.  */
	/* XXX: was unsigned char */
	public byte event_selector; 
}
