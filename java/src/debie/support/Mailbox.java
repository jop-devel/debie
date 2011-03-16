/*------------------------------------------------------------------------------
 *
 *    Copyright (C) 1998 : Space Systems Finland Ltd.
 *
 * Space Systems Finland Ltd (SSF) allows you to use this version of
 * the DEBIE-I DPU software for the specific purpose and under the
 * specific conditions set forth in the Terms Of Use document enclosed
 * with or attached to this software. In particular, the software
 * remains the property of SSF and you must not distribute the software
 * to third parties without written and signed authorization from SSF.
 *
 *   Ported to Java
 *   Based on the SSF DHI file msg_ctrl.h, rev 1.11, Mon May 17 22:50:44 1999.
 *   (C) 2011 : Wolfgang Puffitsch, Benedikt Huber, Martin Schoeberl 
 *
 *    System Name:   DEBIE DPU SW
 *    Module     :   Based on 'msg_ctrl.h'
 *      
 *- * --------------------------------------------------------------------------
 */
package debie.support;

/**
 * This class is a port of the interface 'msg_ctrl' of the DEBIE benchmark
 * @author Benedikt Huber <benedikt.huber@gmail.com>
 *
 */
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
	public abstract void sendISRMail(char message);

	public abstract void waitMail();

	/* Fields of struct incoming_mail_t of the C implementation */
	/* XXX: should rethink access rules (public vs private+getters/setters, final) */

	public Mailbox(byte mailbox_number) {
		this.mailbox_number = mailbox_number;
	}
	
	/** Id for this mailbox, initialized in the constructor */
	/* XXX: was unsigned char */
	public final byte mailbox_number;

	public int /* unsigned char */ timeout;

	public void setTimeout(int timeout) {
		this.timeout = timeout;
	} 

	/** Actual message */
	/* XXX: why the hell is this a pointer in the C implementation? */
	/* bh: In the C code, 'message' usually points to a static variable
	 *     inside the recipients module, which would then be update by the
	 *     mailbox handler. I think there is no need to simulate this
	 */
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
