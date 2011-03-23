package debie.harness;

import debie.support.DebieSystem;
import debie.support.Mailbox;
import debie.support.TaskControl;

public class HarnessMailbox extends Mailbox {

	private DebieSystem system;
	
	public HarnessMailbox(byte mailbox_number, DebieSystem system) {
		super(mailbox_number);
		this.system = system;
	}

	/** The last message in the mailbox. */
	private static char mail_message [] = new char[8];

	/** The number of messages in the mailbox. Should be between 0 and 1. */
	private static char mail_count [] = new char[8];	/* was originally {0, 0, 0, 0, 0, 0, 0, 0}; */

	/** The number of times a mailbox has overflowed. */
	private static int mail_overflows = 0;
		
	/** Purpose        : Send mail to a requested mailbox.
	 *  Interface      : input:   - mailbox, message, timeout
	 *                   output:  - telemetry_data.os_send_message_error
	 *  Preconditions  : Mailbox number should be a value 0 - 7
	 *                   timeout should be a value 0 - 255
	 *  Postconditions : Mail is send to a given mailbox.
	 *  Algorithm      : - In case of an error, failed 'mailbox' is stored to
	 *                     telemetry.
	 * XXX: Description of algorithm makes no sense, maybe a bug in the original comment?
	 */
	@Override
	public void sendTaskMail (char message, byte timeout) {
		if (Harness.TRACE)
			Harness.trace(String.format("[HarnessMailbox] SendTaskMail to %d, message %d, timeout %d",
										(int)mailbox_number, (int)message, (int)timeout)); 
		if (mail_count[mailbox_number] == 0) {
			mail_message[mailbox_number] = message;
		} else {
			mail_overflows++;
		}
		mail_count[mailbox_number] ++;
	}

	/** Purpose        : Send mail from ISR to a requested mailbox.
	 *  Interface      : input:   - mailbox, message 
	 *                   output:  - telemetry_data.os_send_message_error
	 *  Preconditions  : Mailbox number should be a value 0 - 7
	 *  Postconditions : Mail is send to a given mailbox.
	 *  Algorithm      : - In case of an error, failed 'mailbox' is stored to
	 *                     telemetry.
	 * XXX: Description of algorithm makes no sense, maybe a bug in the original comment?
	 */
	private int isrSendMessage(char message) {
		if (Harness.TRACE)
			Harness.trace(String.format("[HarnessMailbox] isr_send_message to %d, message %d = 0x%x",
										(int)mailbox_number, (int)message, (int)message)); 
		sendTaskMail(message, (byte)0);
		return TaskControl.OK;
	}

	/* Send_ISR_Mail is to be used from C51 interrupt routines to send
	 * mail messages to tasks. If RTX-51 reports an error, the mailbox
	 * number is set in telemetry. The reason is probably the following:
	 * -Specified mailbox does not exist(wrong mailbox parameter).
	 * Send_ISR_Mail is made a macro instead of a function to avoid using
	 * reentrant functions from interrupt routines.
	 * Users of Send_ISR_Mail must have access to telemetry_data. */
	@Override
	public void sendISRMail(char message) {
		if (isrSendMessage(message) == TaskControl.NOT_OK) {
			system.getTelemetryData().setISRSendMessageError(mailbox_number);
		}
	}
	
	/** Purpose        : Mail is waited from the given mailbox.                   
	 *  Interface      : Return value, which describes the execution result, is   
	 *                   stored in to a struct.                                   
	 *  Preconditions  : Mailbox number should be a value 0 - 7                   
	 *                   Time-out should have a value 0 - 255.                    
	 *  Postconditions : Message is received or timeout has occurred or error has 
	 *                   occurred due to unvalid parameter values.                
	 *  Algorithm      : -In case of an error, 'event_selector' is stored to      
	 *                    telemetry as an error indication and error bit is set   
	 *                    in software_error register.
	 * XXX: Description of algorithm makes no sense, maybe a bug in the original comment?
	 * FIXME: at most one mail is stored in the mailbox?
	 */
	@Override
	public void waitMail() {
		if (Harness.TRACE)
			Harness.trace(String.format("[HarnessMailbox] WaitMail from %d, timeout %d",
										(int)mailbox_number, timeout));
		
		if (mail_count[mailbox_number] > 0) {
			wait_result = MSG_RECEIVED;
			execution_result = MSG_RECEIVED;
			message = mail_message[mailbox_number];

			if (Harness.TRACE)
				Harness.trace(String.format("[HarnessMailbox] Message from %d is %d = 0x%x",
											(int)mailbox_number, (int)message, (int)message));
			
			mail_count[mailbox_number]--;
		} else {
			wait_result = TIMEOUT_OCCURRED;
			execution_result = TIMEOUT_OCCURRED;
			message = 0;
		}
	}

	void flushMail() {
		if (Harness.TRACE)
			Harness.trace(String.format("[HarnessMailbox] FlushMail from box %d, which had %d messages.",
										(int)mailbox_number, (int)mail_count[mailbox_number])); 

		mail_count[mailbox_number] = 0;
	}

	/* Debugging / Testing interface */

	int getMailCount() {
		return mail_count[mailbox_number];
	}

	int getMessage() {
		return mail_message[mailbox_number];
	}
}