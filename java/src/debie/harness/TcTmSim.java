package debie.harness;

import debie.target.TcTmDev;

public class TcTmSim implements TcTmDev {
	
	int /* char */ tc_msb, tc_lsb;
	/* Simulated TC interface registers. */

	char /* uint16_t */ tc_word;
	/* The simulated TC word, composed of tc_msb and tc_lsb. */


    private int /* unsigned char*/ tm_msb, tm_lsb;
	/* Simulated TM interface registers. */


	@Override
	public int readTcLsb() {
		if(Harness.TRACE) Harness.trace("[TcTmSim] Read_TC_LSB");			
		return tc_lsb;
	}

	@Override
	public int readTcMsb() {
		if(Harness.TRACE) Harness.trace("[TcTmSim] Read_TC_MSB");			
		return tc_msb;
	}

	@Override
	public void writeTmLsb(int value) {
		if(Harness.TRACE) Harness.trace(String.format("[TcTmSim] Write_TM_LSB %d = 0x%x", value, value));			
		tm_lsb = value;
	}

	@Override
	public void writeTmMsb(int value) {
		if(Harness.TRACE) Harness.trace(String.format("[TcTmSim] Write_TM_MSB %d = 0x%x", value, value));			
		tm_msb = value;
	}

	public void setTcRegs (/* unsigned char */ int address, /* unsigned char */ int code)
	/* Invokes TC_InterruptService with a TC composed of the
	 * given address and code, provided with valid (even) parity.
	 */
	{
		/* unsigned char */ int par;
		/* The parity. */

		/* Encode the address and code in the TC interface registers: */

		tc_msb = (address & 0xff) << 1;
		tc_lsb = (code & 0xff);

		/* Generate the even parity bit: */

		par = tc_msb ^ tc_lsb;
		par = (par & 0x0F) ^ (par >>4);
		par = (par & 0x03) ^ (par >>2);
		par = (par & 0x01) ^ (par >>1);

		tc_msb |= par;

		tc_word = (char)((tc_msb << 8) | tc_lsb);
	}

	static /* unsigned char */ int tc_timer_overflow = 1;
	/* Simulated overflow flag on the TC timer. */
	/* 1 = overflow = good, sufficient interval between TCs. */
	/* 0 = no overflow = bad. */

	@Override
	public void clearTimerOverflowFlag() {
		if(Harness.TRACE) Harness.trace("[TcTmSim] Clear_TC_Timer_Overflow_Flag");			
		tc_timer_overflow = 0;
	}

	@Override
	public int getTimerOverflowFlag() {
		if(Harness.TRACE) Harness.trace("[TcTmSim] TC_Timer_Overflow_Flag");			
		return tc_timer_overflow;
	}

	@Override
	public void setTimerOverflowFlag() {
		if(Harness.TRACE) Harness.trace("[TcTmSim] Set_TC_Timer_Overflow_Flag");			
		tc_timer_overflow = 1;
	}

	@Override
	public void clearTcInterruptFlag() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void clearTmInterruptFlag() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void initTcTimerLsb() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void initTcTimerMsb() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void startTcTimer() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void stopTcTimer() {
		// TODO Auto-generated method stub
		
	}

}

