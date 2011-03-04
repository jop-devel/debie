package debie.harness;

import debie.support.Mailbox;

public class HarnessMailbox extends Mailbox {

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
	public void sendTaskMail (char message, byte timeout) {
		if (mail_count[mailbox_number] == 0) {
			mail_message[mailbox_number] = message;
		} else {
			mail_overflows++;
		}
		mail_count[mailbox_number] ++;
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
	 */
	public void waitMail() {
		if (mail_count[mailbox_number] > 0) {
			wait_result = MSG_RECEIVED;
			execution_result = MSG_RECEIVED;
			message = mail_message[mailbox_number];

			mail_count[mailbox_number]--;
		} else {
			wait_result = TIMEOUT_OCCURRED;
			execution_result = TIMEOUT_OCCURRED;
			message = 0;
		}
	}

	void flushMail() {
		mail_count[mailbox_number] = 0;
	}
}