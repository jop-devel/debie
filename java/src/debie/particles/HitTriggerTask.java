package debie.particles;

import debie.support.DebieSystem;

public class HitTriggerTask implements Runnable {

	/* reference to the DEBIE system */
	private DebieSystem system;

	/**
	 * Purpose        : Initialize the global state of Hit Trigger handling
	 * Interface      : inputs  - none
	 *                  outputs - none
	 * Preconditions  : none
	 * Postconditions : Calling task attached as Hit Trigger ISR.
	 *                  Hit Trigger interrupt enabled
	 * Algorithm      : - attach current task as Hit Trigger ISR
	 *                  - enable Hit Trigger interrupt
	 */
	public HitTriggerTask(DebieSystem system) {
		this.system = system;
		
//		attachInterrupt(HIT_TRIGGER_ISR_SOURCE);
//		/*Now 'HitTriggerTask()' will listen for Hit trigger interrupt.           */

		system.getSensorUnitDevice().enableHitTrigger();
	}
	
	@Override
	public void run() {
		// TODO Auto-generated method stub		
	}



}
