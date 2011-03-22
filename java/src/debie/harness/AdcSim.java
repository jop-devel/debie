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
	
	static int ad_conv_delay [] = new int[AD_NUM_CONV];
	
	/** Counts the consecutive conversions for ad_conv_delay[]. */
	private int ad_conv_num = 0;
	
	void setADConvNum(int value) {
		ad_conv_num = value;
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
	private RandomSim ad_rand_index = new RandomSim(0);

	/** Maximum number of particle "hits" that may occur during
	 * A/D conversions (in the Monitoring task, we assume).
	 */
	int max_adc_hits;

	/** A roving index to the random A/D data, for randomizing
	 * the occurrence of "hits" during A/D conversions.
	 */
	private RandomSim ad_hit_rand_index = new RandomSim(0);

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
	private RandomSim ad_fail_index = new RandomSim(0);

	int end_of_adc_count;
	
	/** Counts the number of calls of Start_Conversion. */
	int start_conversion_count;
	
	/** Simulated ADC value, set at Start_Conversion and reported
	 * byte by byte.
	 */
	int ad_value = 0;
	
	int ad_conv_timer;
	
	public void clearADConverting() {
		ad_converting = 0;
	}
	
	/** Sets ad_conv_delay[all] = delay. */
	public void setADDelay(int delay) {
		for (int i = 0; i < AD_NUM_CONV; i++)
			ad_conv_delay[i] = delay;
	}

	private RandomSim ad_delay_rand = new RandomSim(0);
	/* A roving index to randomize the A/D delays. */

	/* Sets random ad_conv_delay[]. */
	public void randomADDelay()	{
	   for (int i = 0; i < AD_NUM_CONV; i++)
	      ad_conv_delay[i] = ad_delay_rand.nextRand() % (AcquisitionTask.ADC_MAX_TRIES + 10);
	}

	
	@Override
	/** Is the A/D conversion done, that is, ready for readout?
	 * 0 (CONVERSION_ACTIVE) means yes, any other value means no.
	 */
	public int endOfADC() {
		if (Harness.TRACE) Harness.trace("[AdcSim] End_Of_ADC");
		
		if (ad_converting == 0) {
			if (Harness.TRACE) Harness.trace("[AdcSim] - conversion not going on.");
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

				if (ad_hit_rand_index.nextRand() > 0x7f)
				{
					/* Hit me again, Sam. */

					if (Harness.TRACE) Harness.trace("[AdcSim] Hit during A/D"); 
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

		if (Harness.TRACE) Harness.trace(String.format("[AdcSim] Get_Result %d = 0x%x", value, value));

		/* Shift the LSB to the MSB, for next Get_Result: */

		ad_value <<= 8;

		return value;
	}

	@Override
	public void setDACOutput(int level) {
		if (Harness.TRACE) Harness.trace(String.format("[AdcSim] Set_DAC_Output %d", level)); 
	}

	@Override
	public void startConversion() {
		int channel;

		channel = HealthMonitoringTask.ADCChannelRegister & 0x3f;

		if (Harness.TRACE) Harness.trace(String.format("[AdcSim] Start_Conversion on channel %d", channel));

		if (ad_converting != 0) {
			if (Harness.TRACE) Harness.trace("[AdcSim] - previous conversion did not end.");
		}

		/* Pick an A/D reading for this channel: */

		ad_value = ad_rand_index.nextRand() << 8;
		ad_value |= ad_rand_index.nextRand();

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

			if (ad_fail_index.nextRand() > 0x3f) {
				/* Pretend that this conversion will fail (not end). */

				if (Harness.TRACE) Harness.trace("[AdcSim] Start_Conversion starts a failing A/D conversion.");
				ad_conv_timer = -5000;

				ad_random_failures --;
			}
		}

		ad_converting = 1;
	}

	@Override
	public void updateADCChannelReg(int channel) {
		if (Harness.TRACE) Harness.trace(String.format("[AdcSim] Update_ADC_Channel_Reg %x", channel));
	}
	
	/** Sets no A/D limits. */
	public void setADUnlimited ()	{
		if(Harness.TRACE) Harness.trace("[AdcSim] Set AD Unlimited");

		for (int c = 0; c < AD_CHANNELS; c++) {
			ad_limit[c][MIN] = 0;
			ad_limit[c][MAX] = 0xffff;
		}
	}

	/** Sets A/D limits to ensure nominal (in-range) readings. */
	public void setADNominal () {
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
