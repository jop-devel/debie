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
 *   Ported to Java by
 *   (C) 2011 : Benedikt Huber, Wolfgang Puffitsch, Martin Schoeberl 

 *    System Name:   DEBIE DPU SW
 *    Based on the SSF DHI file ttc_ctrl.h, rev 1.11,  Sun May 16 09:20:10 1999.
 *
 * Macros and function prototypes for handling the Telecommand
 * and Telemetry interface.
 *
 * This version uses the harness functions for I/O and kernel simulation.
 *
 *- * --------------------------------------------------------------------------
 */

package debie.target;

/** Telecommand / Telemetry target interface */
public interface TcTm {
	/* Error Status register bits concerning TM/TC interface */
	public static final int TC_ERROR = 1;
	public static final int PARITY_ERROR = 2;
	public static final int TC_OR_PARITY_ERROR = (TC_ERROR + PARITY_ERROR);

  	/* TM interrupt service handling */
	public static final int TM_ISR_MASK  = 0x04;

	/* TC/TM interface functions, simulated */	
	/* unsigned char */ int readTcMsb ();
	/* unsigned char */ int readTcLsb ();

	void  writeTmLsb(/* unsigned char */ int value);
	void  writeTmMsb(/* unsigned char */ int value);

	/*    TC timer operations : isr_ctrl.h  (FIXME: is this the right place?)   */

	/* unsigned char */ int getTimerOverflowFlag();
	void clearTimerOverflowFlag();
	void setTimerOverflowFlag();

	/* TM Interrupt flag */
	void clearTmInterruptFlag();

	/* TC Interrupt flag*/
	void clearTcInterruptFlag();

//	/* TM and TC interrupt controls*/
//
//	#define SET_INT_TYPE1_EDGE {}
//	#define SET_INT_TYPE0_EDGE {}
//
//	/* TC timer controls */
//
//	#define SET_TC_TIMER_MODE  {}
//	/* Set TC timer (0) mode : Mode 1, counter operation, SW control */
//

	void initTcTimerMsb();
	void initTcTimerLsb();
	/* TC timer initialization macros */ 

	void startTcTimer();
	void stopTcTimer();
	/* TC timer run control macros    */

}
