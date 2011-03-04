package debie.particles;

import joprt.RtThread;


/* Purpose        : Implements the Acquisition task.                         */
/* Interface      : inputs      - Acquisition task mailbox                   */
/*                              - Mail from Hit Trigger interrupt service    */
/*                              - Buffer with sampled Peak detector outputs  */
/*                              - Housekeeping Telemetry registers           */
/*                  outputs     - Science data                               */
/* Preconditions  : none                                                     */
/* Postconditions : This function does not return.                           */
/* Algorithm      : - InitAcquisitionTask                                    */
/*                  - loop forever:                                          */
/*                    -   wait for mail to Acquisition task mailbox          */
/*                    -   handleAcquisition                                  */
public class AquisitionTask implements Runnable {

	public AquisitionTask() {
		initAcquisition();
	}

	@Override
	public void run() {
		for(;;) {
			/* FIXME: wait for mail to Acquisition task mailbox */			
			handleAcquisition();
		}
	}

	private void initAcquisition() {
		// TODO Auto-generated method stub
		
	}

	private void handleAcquisition() {
		// TODO Auto-generated method stub
		
	}

}
