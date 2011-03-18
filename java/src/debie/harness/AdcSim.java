package debie.harness;

import debie.health.HealthMonitoringTask;
import debie.particles.AcquisitionTask;
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

	private static final int AD_NUM_CONV = 6;
	
	private static int ad_conv_delay [] = new int[AD_NUM_CONV];
	
	/** Counts the consecutive conversions for ad_conv_delay[]. */
	private int ad_conv_num = 0;

	private static final int AD_NUM_RAND = 319;
	
	/** Random A/D data. */
	private static final int ad_random [] = {
		   0x6a, 0xde, 0xba, 0x90, 0xf2, 0x18, 0x48, 0xf3,
		   0x9e, 0x2b, 0x31, 0xdb, 0xe0, 0x7e, 0xc6, 0x18,
		   0x43, 0xd0, 0xd7, 0x6e, 0xbc, 0xee, 0x93, 0x9a,
		   0x06, 0xb2, 0x3d, 0x1f, 0xc1, 0x51, 0x66, 0x69,
		   0xbf, 0x1c, 0x9c, 0xfc, 0x9b, 0xf7, 0xf2, 0xd0,
		   0xf4, 0x26, 0x60, 0x69, 0xc4, 0xd9, 0xdb, 0xd4,
		   0xe7, 0x2b, 0x8a, 0xea, 0x9f, 0xab, 0x40, 0x3e,
		   0xc3, 0xd8, 0x21, 0x61, 0x3b, 0x0f, 0xc1, 0x49,
		   0xd3, 0x09, 0x9a, 0x4d, 0x33, 0x52, 0x7b, 0x8e,
		   0x7e, 0x7b, 0x6a, 0x88, 0x4f, 0x84, 0xa2, 0xb4,
		   0x83, 0xd9, 0xba, 0x79, 0x7d, 0x8f, 0xdf, 0xb2,
		   0x8c, 0x86, 0x77, 0x4f, 0x29, 0x86, 0xd4, 0x8b,
		   0x11, 0x65, 0x55, 0x74, 0xf4, 0x76, 0x83, 0x88,
		   0xd6, 0xa6, 0xa7, 0x33, 0x22, 0xa3, 0x2e, 0x88,
		   0x06, 0x54, 0x90, 0x37, 0xd5, 0xdb, 0xce, 0x7c,
		   0x0b, 0xd1, 0xe1, 0xc0, 0x7d, 0xa5, 0x0b, 0xc9,
		   0xaf, 0xe3, 0x75, 0xc5, 0xf5, 0xaf, 0xaa, 0xe2,
		   0x2a, 0xff, 0x6e, 0x84, 0x0e, 0x04, 0x10, 0xf0,
		   0x78, 0xdc, 0x96, 0x3d, 0x22, 0x96, 0x64, 0x5b,
		   0x7b, 0x9e, 0x83, 0x45, 0xba, 0xb8, 0xe1, 0x31,
		   0xc7, 0x0a, 0xe0, 0x31, 0xce, 0x29, 0x3d, 0x01,
		   0xb8, 0xfc, 0x79, 0x83, 0x3d, 0xd1, 0x40, 0xe1,
		   0x46, 0xfa, 0xe7, 0xc5, 0xdc, 0xc4, 0x1c, 0x24,
		   0x29, 0x5a, 0xef, 0xeb, 0x92, 0x57, 0xba, 0x06,
		   0x13, 0x1d, 0x35, 0xef, 0xb0, 0x2d, 0x69, 0x20,
		   0x92, 0xb1, 0x82, 0x00, 0x8e, 0x3b, 0x12, 0xb3,
		   0x78, 0xd7, 0x18, 0xb3, 0x54, 0x0f, 0xd1, 0x8e,
		   0x88, 0x5d, 0x4e, 0x2b, 0x30, 0x30, 0x2d, 0x85,
		   0xaa, 0x21, 0x01, 0xe1, 0x2c, 0x35, 0xa1, 0xee,
		   0xa2, 0x17, 0xed, 0x60, 0x1b, 0x98, 0xea, 0x12,
		   0x85, 0x21, 0xde, 0x45, 0x26, 0xef, 0x12, 0x3c,
		   0x02, 0x8c, 0xd7, 0x49, 0xbd, 0x02, 0xa7, 0x7d,
		   0xe7, 0x1c, 0x15, 0xf9, 0xaa, 0x44, 0x15, 0xb1,
		   0xaa, 0x76, 0x5e, 0xf2, 0xb4, 0xfb, 0x85, 0x77,
		   0xb9, 0x32, 0xb4, 0xc9, 0x70, 0xe1, 0xdb, 0x44,
		   0x9f, 0x5b, 0x87, 0xca, 0xaa, 0xcb, 0x43, 0x53,
		   0x7e, 0x49, 0xec, 0x1a, 0x13, 0x1d, 0xe1, 0x1b,
		   0x13, 0xc3, 0x34, 0x95, 0x5d, 0x5a, 0xc3, 0xd0,
		   0x33, 0x05, 0x82, 0x4a, 0x2e, 0x6d, 0x39, 0xeb,
		   0x9c, 0x65, 0x81, 0x7f, 0xa1, 0x62, 0x11};

	/* Go on to the next random value. */
	private int next(int index) {
		index++;
		if (index > AD_NUM_RAND) index = 0;
		return index;
	}
	
	/* Go on to the next random value and return it. */
	private int nextRand(int index) {
		return ad_random[index];
	}
	
	// better to simulate using multi-dim array?
	//	public static class AdLimit {
	//		int min;
	//		int max;
	//	} 
	
	/* Limits on the value of a simulated A/D reading. */
	private static final int MIN = 0;
	private static final int MAX = 1;
	public static final int[][] ad_limit = {
	   {0, 0xffff}, {0, 0xffff}, {0, 0xffff}, {0, 0xffff}, {0, 0xffff},
	   {0, 0xffff}, {0, 0xffff}, {0, 0xffff}, {0, 0xffff}, {0, 0xffff},
	   {0, 0xffff}, {0, 0xffff}, {0, 0xffff}, {0, 0xffff}, {0, 0xffff},
	   {0, 0xffff}, {0, 0xffff}, {0, 0xffff}, {0, 0xffff}, {0, 0xffff},
	   {0, 0xffff}, {0, 0xffff}, {0, 0xffff}, {0, 0xffff}, {0, 0xffff},
	   {0, 0xffff}, {0, 0xffff}, {0, 0xffff}, {0, 0xffff}, {0, 0xffff},
	   {0, 0xffff}, {0, 0xffff}, {0, 0xffff}, {0, 0xffff}, {0, 0xffff},
	   {0, 0xffff}, {0, 0xffff}, {0, 0xffff}, {0, 0xffff}, {0, 0xffff} };
	/* Limits on the simulated A/D readings for all channels. */

	int ad_converting;
	
	/** A roving index to the random A/D data. */
	private int ad_rand_index = 0;

	/** Maximum number of particle "hits" that may occur during
	 * A/D conversions (in the Monitoring task, we assume).
	 */
	int max_adc_hits;

	/** A roving index to the random A/D data, for randomizing
	 * the occurrence of "hits" during A/D conversions.
	 */
	int ad_hit_rand_index = 0;

	/** The number of simulated particle "hits" during A/D conversion. */
	int total_adc_hits;

	/** The number of A/D conversion failures (conversion never done)
	 * to be simulated, at random times, in the next conversions
	 * (calls of Start_Conversion).
	 */
	int ad_random_failures = 0;

	/** A roving index to the random A/D data, for randomizing
	 * the occurrence of A/D failures (conversion never done).
	 */
	private int ad_fail_index = 0;

	int end_of_adc_count;
	
	/** Counts the number of calls of Start_Conversion. */
	int start_conversion_count;
	
	/** Simulated ADC value, set at Start_Conversion and reported
	 * byte by byte.
	 */
	int ad_value = 0;
	
	int ad_conv_timer;
	
	/** Sets ad_conv_delay[all] = delay. */
	public void setADDelay(int delay) {
		for (int i = 0; i < AD_NUM_CONV; i++)
			ad_conv_delay[i] = delay;
	}

	@Override
	/** Is the A/D conversion done, that is, ready for readout?
	 * 0 (CONVERSION_ACTIVE) means yes, any other value means no.
	 */
	public int endOfADC() {
		if (Harness.TRACE) Harness.trace("End_Of_ADC");
		
		if (ad_converting == 0) {
			if (Harness.TRACE) Harness.trace("- conversion not going on.");
		}

		end_of_adc_count ++;

		ad_conv_timer ++;

		if (ad_conv_timer >= ad_conv_delay[ad_conv_num])
		{  /* Conversion done. */

			ad_converting = 0;

			return 0;
		}
		else
		{  /* Conversion still going on. */

			/* Perhaps get a preemptive "hit": */

			if (max_adc_hits > 0)
			{
				/* We have some hits in reserve. */

				ad_hit_rand_index = next(ad_hit_rand_index);
				if (nextRand(ad_hit_rand_index) > 0x7f)
				{
					/* Hit me again, Sam. */

					if (Harness.TRACE) Harness.trace("Hit during A/D"); 
					AcquisitionTask.confirm_hit_result = 1;

					total_adc_hits ++;
					max_adc_hits --;

					ad_converting = 0;

					return 0;
					/* No point in going on. */
				}
			}

			/* Conversion not yet finished and no hit. */

			return 1;
		}
	}

	@Override
	public int getResult() {
		int value;

		/* Return the current MSB of ad_value: */

		value = (ad_value >>> 8) & 0xff;

		if (Harness.TRACE) Harness.trace(String.format("Get_Result %d = 0x%x\n", value, value));

		/* Shift the LSB to the MSB, for next Get_Result: */

		ad_value <<= 8;

		return value;
	}

	@Override
	public void setDACOutput(int level) {
		if (Harness.TRACE) Harness.trace(String.format("Set_DAC_Output %d", level)); 
	}

	@Override
	public void startConversion() {
		int channel;

		channel = HealthMonitoringTask.ADCChannelRegister & 0x3f;

		if (Harness.TRACE) Harness.trace(String.format("Start_Conversion on channel %d\n", channel));

		if (ad_converting != 0) {
			if (Harness.TRACE) Harness.trace("- previous conversion did not end.\n");
		}

		/* Pick an A/D reading for this channel: */

		ad_rand_index = next(ad_rand_index);
		ad_value = (nextRand(ad_rand_index)) << 8;
		ad_rand_index = next(ad_rand_index);
		ad_value |= nextRand(ad_rand_index);

		if (ad_value < ad_limit[channel][MIN] /* ad_limit[channel].min */)
			ad_value = ad_limit[channel][MIN] /* ad_limit[channel].min */;
		else if (ad_value > ad_limit[channel][MAX] /* ad_limit[channel].max */)
			ad_value = ad_limit[channel][MAX] /* ad_limit[channel].max */;

		start_conversion_count ++;

		ad_conv_timer = 0;
		/* Normal case, may be changed below. */

		ad_conv_num ++;
		if (ad_conv_num >= AD_NUM_CONV) ad_conv_num = 0;

		if (ad_random_failures > 0) {
			ad_fail_index = next(ad_fail_index);
			if (nextRand (ad_fail_index) > 0x3f) {
				/* Pretend that this conversion will fail (not end). */

				if (Harness.TRACE) Harness.trace("Start_Conversion starts a failing A/D conversion.");
				ad_conv_timer = -5000;

				ad_random_failures --;
			}
		}

		ad_converting = 1;
	}

	@Override
	public void updateADCChannelReg(int channel) {
		if (Harness.TRACE) Harness.trace(String.format("UpdateADC_ChannelReg %x", channel));
	}
	
	public void setADNominal ()
	/* Sets A/D limits to ensure nominal (in-range) readings. */
	{
		if(Harness.TRACE) Harness.trace("[AdcSim] Set AD Nominal");

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
