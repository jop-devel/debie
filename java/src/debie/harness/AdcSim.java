package debie.harness;

import debie.target.AdConverter;


/*  A/D channels

Channel     Meaning
-------     -------
dec hex
  0  00     SU 1, plasma 1 +
  1  01     SU 1, plasma 1 -
  2  02     SU 1, piezo 1
  3  03     SU 1, piezo 2
  4  04     SU 1, plasma 2 +
  5  05     SU 1, temperature 0
  6  06     SU 1, temperature 1
  7  07
  8  08     SU 2, plasma 1 +
  9  09     SU 2, plasma 1 -
 10  0A     SU 2, piezo 1
 11  0B     SU 2, piezo 2
 12  0C     SU 2, plasma 2 +
 13  0D     SU 2, temperature 0
 14  0E     SU 2, temperature 1
 15  0F
 16  10     SU 1 & SU 2, +5V  supply
 17  11     SU 3 & SU 4, +5V  supply
 18  12     SU 1,2,3,4 , +50V supply 
 19  13     DPU          +5V  supply
 20  14     SU 1 & SU 2, -5V  supply
 21  15     SU 3 & SU 4, -5V  supply
 22  16     SU 1,2,3,4,  -50V supply
 23  17
 24  18     SU 3, plasma 1 +
 25  19     SU 3, plasma 1 -
 26  1A     SU 3, piezo 1
 27  1B     SU 3, piezo 2
 28  1C     SU 3, plasma 2 +
 29  1D     SU 3, temperature 0
 30  1E     SU 3, temperature 1
 31  1F
 32  20     SU 4, plasma 1 +
 33  21     SU 4, plasma 1 -
 34  22     SU 4, piezo 1
 35  23     SU 4, piezo 2
 36  24     SU 4, plasma 2 +
 37  25     SU 4, temperature 0
 38  26     SU 4, temperature 1
 39  27
 40  28

A/D channel-selector register:

Bit         Meaning
---         -------
  7         1 = do not use interleave calibration
  6         1 = bipolar, 0 = unipolar
  5         Channel index, 32
  4         Channel index, 16
  3         Channel index,  8
  2         Channel index,  4
  1         Channel index,  2
  0         Channel index,  1

*/

public class AdcSim implements AdConverter {
	// better to simulate using multi-dim array?
	//	public static class AdLimit {
	//		int min;
	//		int max;
	//	} 
	
	/* Limits on the value of a simulated A/D reading. */
	private static final int MIN = 0;
	private static final int MAX = 1;
	public static int[][] ad_limit = {
	   {0, 0xffff}, {0, 0xffff}, {0, 0xffff}, {0, 0xffff}, {0, 0xffff},
	   {0, 0xffff}, {0, 0xffff}, {0, 0xffff}, {0, 0xffff}, {0, 0xffff},
	   {0, 0xffff}, {0, 0xffff}, {0, 0xffff}, {0, 0xffff}, {0, 0xffff},
	   {0, 0xffff}, {0, 0xffff}, {0, 0xffff}, {0, 0xffff}, {0, 0xffff},
	   {0, 0xffff}, {0, 0xffff}, {0, 0xffff}, {0, 0xffff}, {0, 0xffff},
	   {0, 0xffff}, {0, 0xffff}, {0, 0xffff}, {0, 0xffff}, {0, 0xffff},
	   {0, 0xffff}, {0, 0xffff}, {0, 0xffff}, {0, 0xffff}, {0, 0xffff},
	   {0, 0xffff}, {0, 0xffff}, {0, 0xffff}, {0, 0xffff}, {0, 0xffff} };
	/* Limits on the simulated A/D readings for all channels. */

	public void setAD_Delay(int i) {
		// TODO Auto-generated method stub		
	}

	@Override
	public int End_Of_ADC() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getResult() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void setDAC_Output(int level) {
		// TODO Auto-generated method stub

	}

	@Override
	public void startConversion() {
		// TODO Auto-generated method stub

	}

	@Override
	public void updateADC_ChannelReg(int channel) {
		// TODO Auto-generated method stub

	}
	
	public void setAD_Nominal ()
	/* Sets A/D limits to ensure nominal (in-range) readings. */
	{
		if(Harness.TRACE) Harness.trace("Set AD Nominal\n");

	  /* SU +5V supply: */

	  ad_limit[16][MIN] = 0xba00;
	  ad_limit[16][MAX] = 0xe4ff;

	  ad_limit[17][MIN] = 0xba00;
	  ad_limit[17][MAX] = 0xe4ff;

	  /* SU -5V supply: */

	  ad_limit[20][MIN] = 0x0d00;
	  ad_limit[20][MAX] = 0x22ff;

	  ad_limit[21][MIN] = 0x0d00;
	  ad_limit[21][MAX] = 0x22ff;

	  /* SU +50V supply: */

	  ad_limit[18][MIN] = 0xa800;
	  ad_limit[18][MAX] = 0xe3ff;

	  /* SU -50V supply: */

	  ad_limit[22][MIN] = 0x0e00;
	  ad_limit[22][MAX] = 0x2cff;

	  /* DPU +5V supply: */

	  ad_limit[19][MIN] = 0xba00;
	  ad_limit[19][MAX] = 0xe4ff;

	  /* SU 1 temperatures: */

	  ad_limit[5][MIN] = 0x0000;
	  ad_limit[5][MAX] = 0xfaff;
	  ad_limit[6][MIN] = 0x0000;
	  ad_limit[6][MAX] = 0xf4ff;

	  /* SU 2 temperatures: */

	  ad_limit[13][MIN] = 0x0000;
	  ad_limit[13][MAX] = 0xfaff;
	  ad_limit[14][MIN] = 0x0000;
	  ad_limit[14][MAX] = 0xf4ff;

	  /* SU 3 temperatures: */

	  ad_limit[29][MIN] = 0x0000;
	  ad_limit[29][MAX] = 0xfaff;
	  ad_limit[30][MIN] = 0x0000;
	  ad_limit[30][MAX] = 0xf4ff;

	  /* SU 4 temperatures: */

	  ad_limit[37][MIN] = 0x0000;
	  ad_limit[37][MAX] = 0xfaff;
	  ad_limit[38][MIN] = 0x0000;
	  ad_limit[38][MAX] = 0xf4ff;
	}


}
