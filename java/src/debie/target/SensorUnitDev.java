package debie.target;

import debie.particles.SensorUnit;
import debie.particles.SensorUnitSettings;
import debie.support.Dpu;

public abstract class SensorUnitDev {

	public static enum SensorUnitTestLevel {
		high_e, 
		low_e
	};

	public static class Delays {
		public char FromPlasma1Plus; /* XXX: was unsigned short int  */
		public char FromPlasma1Minus; /* XXX: was unsigned short int  */
	}

	public static class TriggerSet {
		public int /* sensor_number_t */ sensor_unit;
		public int /* channel_t */       channel;
		public int /* unsigned char */   level;
		public int /* unsigned char */   execution_result;
		public int /* unsigned int */    base;		
	}

	public static class VoltageStatus {
		int /* unsigned char */ V_down_bit;
		int /* unsigned char */ HV_status;
	};

	/*--- definitions from su_ctrl.h ---*
	/* Sensor Channels */

	public static final int NUM_CH = 5;
	/* Number of recorded measurement channels per sensor unit. */
	/* The PZT_1_2 channel is not recorded, as such.            */

	public static final int PLASMA_1_PLUS =  0;
	public static final int PLASMA_1_MINUS = 1;
	public static final int PZT_1 =          2;
	public static final int PZT_2 =          3;
	public static final int PLASMA_2_PLUS =  4;
	public static final int PZT_1_2 =        5;


	public static final int SU_1 = 1 ;
	public static final int SU_2 = 2;
	public static final int SU_3 = 3;
	public static final int SU_4 = 4;

	public static final int SU_1_ON = 1 ;
	public static final int SU_2_ON = 2;
	public static final int SU_3_ON = 3;
	public static final int SU_4_ON = 4;

	public static final int SU_1_OFF = 1 ;
	public static final int SU_2_OFF = 2;
	public static final int SU_3_OFF = 3;
	public static final int SU_4_OFF = 4;

	public static final int LOW_PLASMA_SELF_TEST_THRESHOLD =         0x15;
	public static final int LOW_PIEZO_SELF_TEST_THRESHOLD =          0x0D;
	public static final int HIGH_PLASMA_1_PLUS_SELF_TEST_THRESHOLD = 0xAB;
	public static final int HIGH_PLASMA_SELF_TEST_THRESHOLD =        0x80;
	public static final int HIGH_PIEZO_SELF_TEST_THRESHOLD =         0x2B;
	public static final int MAX_PLASMA_SELF_TEST_THRESHOLD =         0xFF;
	public static final int MAX_PIEZO_SELF_TEST_THRESHOLD =          0xFF;
	/* Self test threshold levels. */

	public static final int PLASMA_1_PLUS_LOW =          0x13;
	public static final int PLASMA_1_MINUS_LOW =         0x08;
	public static final int PLASMA_2_PLUS_LOW =          0x10;
	public static final int PZT_1_LOW =                  0x36;
	public static final int PZT_2_LOW =                  0x36;
	/* Low level test pulses. */

	public static final int PLASMA_1_PLUS_HIGH =         0x5A;
	public static final int PLASMA_1_MINUS_HIGH =        0x2A;
	public static final int PLASMA_2_PLUS_HIGH =         0x50;
	public static final int PZT_1_HIGH =                 0xF6;
	public static final int PZT_2_HIGH =                 0xE8;
	/* High level test pulses. */

	public static final int SU_NOT_ACTIVATED = 0;
	public static final int SU_NOT_DEACTIVATED = 0;

	public static final int CHANNEL_NOT_SELECTED = 5;
	public static final int SU_NOT_SELECTED = 6;

	public static final int TRIGGER_SET_OK = 1 ;

	public static final int DEFAULT_THRESHOLD = 0x0D;
	/* Default Trigger threshold is mid-scale value. */

	public static final int DEFAULT_TEST_PULSE_LEVEL = 0x00;

	public static final int DEFAULT_CLASSIFICATION_LEVEL = 0;
	public static final int DEFAULT_MAX_TIME =             255;
	public static final int DEFAULT_MIN_TIME =             0;
	/* These default levels are only temporary */

	public static final int SU_ONOFF_MASK = 3;
	/* Bit mask for SU Status register manipulation when SU is */
	/* switched ON or OFF.                                     */

	public static final int SU_STATE_TRANSITION_OK =     1;
	public static final int SU_STATE_TRANSITION_FAILED = 0;

	public static final int NO_SU = 0;

	/* Trigger level register base addresses */

	public static final int SU_1_TRIGGER_BASE =    0x7FB0;
	public static final int SU_2_TRIGGER_BASE =    0x7FB3;
	public static final int SU_3_TRIGGER_BASE =    0x7FC0;
	public static final int SU_4_TRIGGER_BASE =    0x7FC3;

	public static final int SU_CONTROL = 0x7FD0;

	public static final int SU_1_MINUS_50 =   1;
	public static final int SU_1_PLUS_50 =    2;
	public static final int SU_2_MINUS_50 =   4;
	public static final int SU_2_PLUS_50 =    8;
	public static final int SU_3_MINUS_50 =   16;
	public static final int SU_3_PLUS_50 =    32;
	public static final int SU_4_MINUS_50 =   64;
	public static final int SU_4_PLUS_50 =    128;

	public static final int HV_STATUS =       0x7F70;

	public static final int NUM_SU = 4;

	/*--- ported from su_ctrl.h:171-219 */
	/* Function prototypes */
	public int SU_ctrl_register = 0;

	private int SU_self_test_channel;
	
	/** This array stores the value to be used when analog switch bit
	 * corresponding to a given SU is set.                           
	 */
	private static final int  analog_switch_bit [] = {0x10, 0x20, 0x40, 0x80};

	public void switchSensorUnitOn(int number, SensorUnit sensorUnit)
	//	void Switch_SU_On  (
	//			   sensor_number_t SU_Number, 
	//			   unsigned char EXTERNAL *execution_result) COMPACT_DATA REENTRANT_FUNC
	/* Purpose        :  Given Sensor Unit is switched on.                       */
	/* Interface      :  Execution result is stored in a variable.               */
	/* Preconditions  :  SU_Number should be 1,2,3 or 4                          */
	/* Postconditions :  Given Sensor Unit is switced on.                        */
	/* Algorithm      :  The respective bit is set high in the SU on/off control */
	/*                   register with XBYTE.                                    */
	{
		switch (number)
		{
		case SU_1:

			SU_ctrl_register |= 0x10;
			sensorUnit.execution_result = SU_1_ON;
			/* Set high bit 4 in the SU on/off control register,                   */
			/* preserves other bits.                                               */
			break;

		case SU_2:

			SU_ctrl_register |= 0x20;
			sensorUnit.execution_result = SU_2_ON;
			/* Set high bit 5 in the SU on/off control register,                   */
			/* preserves other bits.                                               */
			break;

		case SU_3:

			SU_ctrl_register |= 0x40;
			sensorUnit.execution_result = SU_3_ON;
			/* Set high bit 6 in the SU on/off control register,                   */
			/* preserves other bits.                                               */
			break;

		case SU_4:

			SU_ctrl_register |= 0x80;
			sensorUnit.execution_result = SU_4_ON;
			/* Set high bit 7 in the SU on/off control register,                   */
			/* preserves other bits.                                               */
			break;

		default:
			sensorUnit.execution_result = SU_NOT_ACTIVATED;
		/*Incorrect SU number has caused an error.                            */
		break;
		}

		Dpu.setDataByte(SU_CONTROL, (byte)SU_ctrl_register);    

		// NOTE: Moved telemetry_data status update to the only call site 'Start_SU_SwitchingOn' 
	}


	public void switchSensorUnitOff(int number, SensorUnit sensorUnit)
	//			void Switch_SU_Off (
	//			   sensor_number_t SU_Number, 
	//			   unsigned char EXTERNAL *execution_result) COMPACT_DATA REENTRANT_FUNC
	/* Purpose        :  Given Sensor Unit is switced off.                       */
	/* Interface      :  Execution result is stored in a variable.               */
	/* Preconditions  :  SU_Number should be 1,2,3 or 4.                         */
	/* Postconditions :  Given Sensor Unit is switced off.                       */
	/* Algorithm      :  The respective bit is set low with XBYTE.               */
	{
		switch (number)
		{
		case SU_1:

			SU_ctrl_register &= ~0x10;
			sensorUnit.execution_result = SU_1_OFF;
			/* Set low bit 4 in the SU on/off control register,                    */
			/* preserves other bits.                                               */
			break;

		case SU_2:

			SU_ctrl_register &= ~0x20;
			sensorUnit.execution_result = SU_2_OFF;
			/* Set low bit 5 in the SU on/off control register,                    */
			/* preserves other bits.                                               */
			break;

		case SU_3:

			SU_ctrl_register &= ~0x40;
			sensorUnit.execution_result = SU_3_OFF;
			/* Set low bit 6 in the SU on/off control register,                    */
			/* preserves other bits.                                               */
			break;

		case SU_4:

			SU_ctrl_register &= ~0x80;
			sensorUnit.execution_result = SU_4_OFF;
			/* Set low bit 7 in the SU on/off control register,                    */
			/* preserves other bits.                                               */
			break;

		default:
			sensorUnit.execution_result = SU_NOT_DEACTIVATED;
		/*Incorrect SU number has caused an error.                            */
		break;
		}

		Dpu.setDataByte(SU_CONTROL, (byte)SU_ctrl_register);       

		// NOTE: Moved telemetry_data status update to the only call site 'Start_SU_SwitchingOff' 
	}

	/**
	 * Purpose        :  Given trigger level is set.
	 * Interface      :  Execution result is stored in a variable.
	 * Preconditions  :  SU_Number should be 1-4 and channel number 1-5 levels
	 * Postconditions :  Given level is set for specific unit and channel.
	 * Algorithm      :  The respective memory address is written into.
	 *
	 * This function is used by TelecomandExecutionTask and
	 * HealthMonitoringTask. despite the fact that it is of type re-enrant 
	 * the two tasks should not use it simultaniously. When
	 * HealthMonitoringTask is conducting self test and uses
	 * SetTriggerLevel, TelecomandExecutionTask is able to interrupt and
	 * possibly set another trigger levels which would foul up the self
	 * test. On the other hand when TelecomandExecutionTask is setting
	 * trigger levels HealthMonitoringTask is disabled due to its lower
	 * priority.
	 */
	public void setTriggerLevel(TriggerSet setting) {

		setting.execution_result = TRIGGER_SET_OK;

		switch (setting.sensor_unit)
		/*sensor unit is selected*/
		{
		case SU_1:
		{
			setting.base = SU_1_TRIGGER_BASE;
			break;
		}
		case SU_2:
		{
			setting.base = SU_2_TRIGGER_BASE;
			break;
		}
		case SU_3:
		{
			setting.base = SU_3_TRIGGER_BASE;
			break;
		}
		case SU_4:
		{
			setting.base = SU_4_TRIGGER_BASE;
			break;
		}
		default:
		{
			setting.execution_result = SU_NOT_SELECTED;
			/*Sensor Unit number is invalid.                                    */
			break;
		}
		}

		if (setting.execution_result != SU_NOT_SELECTED)
		{
			// XXX: avoid lookupswitch
			int channel = setting.channel;
			/*channel is selected*/
			if (channel == PLASMA_1_PLUS) {
				Dpu.setDataByte(setting.base + 0, (byte)setting.level);

			} else if (channel == PLASMA_1_MINUS) {
				Dpu.setDataByte(setting.base + 1, (byte)setting.level);

			} else if (channel == PZT_1_2) {
				Dpu.setDataByte(setting.base + 2, (byte)setting.level);

			} else {
				setting.execution_result = CHANNEL_NOT_SELECTED;
				/*Given channel parameter is invalid.                            */
			}
//			switch (setting.channel)
//			/*channel is selected*/
//			{
//			case PLASMA_1_PLUS:
//			{
//				Dpu.setDataByte(setting.base + 0, (byte)setting.level);
//				break;
//			}
//			case PLASMA_1_MINUS:
//			{
//				Dpu.setDataByte(setting.base + 1, (byte)setting.level);
//				break;
//			}
//			case PZT_1_2:
//			{
//				Dpu.setDataByte(setting.base + 2, (byte)setting.level);
//				break;
//			}
//			default:
//			{
//				setting.execution_result = CHANNEL_NOT_SELECTED;
//				/*Given channel parameter is invalid.                            */
//				break;
//			}
//			} 
		}
	}
	
	/**
	 * Purpose        : Set all trigger-levels of one SU.
	 * Interface      : inputs      - SU number in 'sensor_unit'.
	 *                              - Triggering levels in 'settings'.
	 *                  outputs     - Hardware trigger levels.
	 *                  subroutines - SetTriggerLevel
	 * Preconditions  : none
	 * Postconditions : none 
	 * Algorithm      : - set trigger level for Plasma 1+
	 *                  - set trigger level for Plasma 1-
	 *                  - set trigger level for Piezos.
	 */
	public void setSUTriggerLevels (int sensor_unit, SensorUnitSettings settings) {   
		TriggerSet trigger = new TriggerSet();
		/* Holds parameters for SetTriggerLevel. */

		trigger.sensor_unit = sensor_unit;
			 
		trigger.level   = settings.plasma_1_plus_threshold;
		trigger.channel = PLASMA_1_PLUS;
		setTriggerLevel(trigger);
			 
		trigger.level   = settings.plasma_1_minus_threshold;
		trigger.channel = PLASMA_1_MINUS;
		setTriggerLevel(trigger);
			 
		trigger.level   = settings.piezo_threshold;
		trigger.channel = PZT_1_2;
		setTriggerLevel(trigger);
	}
	
	/**
	 * Purpose        :  Testpulse level is set.
	 * Interface      :  input:  - Desired test pulse level.
	 * Preconditions  :  none.
	 * Postconditions :  Level is set.
	 * Algorithm      :  Level is written into memory-mapped port address.
	 */
	public abstract void setTestPulseLevel(int level);
	
	public abstract void enableHitTrigger  ();

	public abstract void disableHitTrigger ();
	
	public abstract int getHitTriggerFlag ();
	
	public abstract int getEventFlag();

	public abstract int getMSBCounter();
	public abstract int getLSB1Counter();
	public abstract int getLSB2Counter();	
	
	public abstract int getRiseTimeCounter();
	
	public abstract int triggerSource0();
	public abstract int triggerSource1();

	public abstract int VDown();

	public abstract void signalPeakDetectorReset(
			int low_reset_value,
			int high_reset_value);
	
	/**
	 * Purpose        : The analog switch output is enabled in the
	 *                  self test channel register.
	 * Interface      : inputs      - self_test_SU_index
	 *                  outputs     - SU_self_test_channel, HW register
	 *                  subroutines -  none
	 * Preconditions  : none
	 * Postconditions : The analog switch output is enabled for a given
	 *                  self test SU in the SU_self_test_channel register.
	 * Algorithm      : - The respective bit is set in the SU_self_test_channel
	 *                    variable and written to HW.
	 */
	public void enableAnalogSwitch(int self_test_SU_index) {
	   SU_self_test_channel |= analog_switch_bit[self_test_SU_index];
	   /* The respective bit is set in the variable, preserve other bits.  */

	   setSUSelfTestCh(SU_self_test_channel);
	}

	/**
	 * Purpose        : The analog switch output is disabled in the
	 *                  self test channel register.
	 * Interface      : inputs      - self_test_SU_index
	 *                  outputs     - SU_self_test_channel, HW register
	 *                  subroutines -  none
	 * Preconditions  : none
	 * Postconditions : The analog switch output is disabled for a given
	 *                  self test SU in the SU_self_test_channel register.
	 * Algorithm      : - The respective bit is reset in the SU_self_test_channel
	 *                    variable and written to HW.
	 */
	public void disableAnalogSwitch(int self_test_SU_index) {
	   SU_self_test_channel &= ~analog_switch_bit[self_test_SU_index];
	   /* The respective bit is set in the variable, preserve other bits.  */

	   setSUSelfTestCh(SU_self_test_channel);
	}

	/** This array stores the selector bit states related to a given channel. */
	// XXX: pulled out of selectSelfTestChannel, which avoids memory allocation
	private static final int channel_selector_value [] = { 0x00, 0x01, 0x02, 0x03, 0x04 };
//	channel_selector_value[PLASMA_1_PLUS]    = 0x00;
//	channel_selector_value[PLASMA_1_MINUS]   = 0x01;
//	channel_selector_value[PZT_1]            = 0x02;
//	channel_selector_value[PZT_2]            = 0x03;
//	channel_selector_value[PLASMA_2_PLUS]    = 0x04;

	/**
	 * Purpose        : A self test channel is selected in the
	 *                  self test channel register.
	 * Interface      : inputs      - channel
	 *                  outputs     - SU_self_test_channel, HW register
	 *                  subroutines -  none
	 * Preconditions  : none
	 * Postconditions : The given self test channel is selected.
	 *                  self test SU in the SU_self_test_channel register.
	 * Algorithm      : - The respective bit is set in the self test channel
	 *                    register and written to HW.
	 */
	public void selectSelfTestChannel(int channel) {

		SU_self_test_channel = 
			(SU_self_test_channel & 0xF8) | channel_selector_value[channel]; 
		/* Set chosen bits preserve others. */

		setSUSelfTestCh(SU_self_test_channel);
	}
	
	public abstract void setSUSelfTestCh (int value);

}


