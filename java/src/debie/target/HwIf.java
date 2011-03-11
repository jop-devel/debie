package debie.target;

import debie.target.SensorUnit.Delays;

public class HwIf {
	public static final int MAX_EVENTS = 1261;
	/* Same as in the original DEBIE-1 SW */

	/* XXX: was unsigned char  */ 
	public static byte readRiseTimeCounter()
	/* Purpose        :  Plasma1+ rise time counter is read from the specified   */
	/*                   address.                                                */
	/* Interface      :  Result is returned as an unsigned char.                 */
	/* Preconditions  :                                                          */
	/* Postconditions :  Data is gained.                                         */
	/* Algorithm      :  Counter is read with XBYTE.                             */
	{
		// TODO: stub. This is a hardware interface
		//return GET_DATA_BYTE(RISE_TIME_COUNTER);
		return 0;
	}

	public static void readDelayCounters(Delays delay_counters) {
		// TODO Auto-generated method stub
		return;
	}

	public static void enableInterruptMaster() {
		// TODO Auto-generated method stub		
	}

	public static void disableInterruptMaster() {
		// TODO Auto-generated method stub
		
	}

	public static void resetPeakDetector(int trigger_unit) {
		// TODO Auto-generated method stub
		
	}

	public static void resetDelayCounters() {
		// TODO Auto-generated method stub
		
	}

}
