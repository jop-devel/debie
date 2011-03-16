package debie.target;

import debie.particles.SensorUnit;
import debie.telecommand.TelecommandExecutionTask;

public class SensorUnitDev {

	public static enum SensorUnitTestLevel {high_e, low_e};

	public static class Delays {
		public char FromPlasma1Plus; /* XXX: was unsigned short int  */
		public char FromPlasma1Minus; /* XXX: was unsigned short int  */
	}

	public static class TriggerSet {
		public int /* sensor_number_t */ sensor_unit;
		public int /* channel_t */    channel;
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

	public static final int SU_1_TRIGGER_BASE =    0xFFB0;
	public static final int SU_2_TRIGGER_BASE =    0xFFB3;
	public static final int SU_3_TRIGGER_BASE =    0xFFC0;
	public static final int SU_4_TRIGGER_BASE =    0xFFC3;

	public static final int SU_CONTROL = 0xFFD0;

	public static final int SU_1_MINUS_50 =   1;
	public static final int SU_1_PLUS_50 =    2;
	public static final int SU_2_MINUS_50 =   4;
	public static final int SU_2_PLUS_50 =    8;
	public static final int SU_3_MINUS_50 =   16;
	public static final int SU_3_PLUS_50 =    32;
	public static final int SU_4_MINUS_50 =   64;
	public static final int SU_4_PLUS_50 =    128;

	public static final int HV_STATUS =       0xFF70;

	public static final int NUM_SU = 4;

	/*--- ported from su_ctrl.h:171-219 */
	/* Function prototypes */
	public static int SU_ctrl_register = 0;

	/* Sensor Unit status */

	/* read delay counters --> HwIf.java */

	//	void readDelayCounters (delays_t delay);
	//	extern unsigned char ReadRiseTimeCounter(void) COMPACT REENTRANT_FUNC;
	//	extern void ResetDelayCounters(void) COMPACT REENTRANT_FUNC;
	//	extern void ResetPeakDetector(sensor_number_t unit);
	//	extern void SignalPeakDetectorReset(
	//	   unsigned char low_reset_value,
	//	   unsigned char high_reset_value);
	//
	//
	//	/* Trigger levels */
	//	extern void SetTriggerLevel(trigger_set_t EXTERNAL *setting)
	//	   COMPACT REENTRANT_FUNC;
	//
	//	/* Test pulse level */
	//	extern void SetTestPulseLevel(unsigned char level)
	//	   COMPACT REENTRANT_FUNC;
	//
	//	extern void GetVoltageStatus(voltage_status_t EXTERNAL *v_status) 
	//	   COMPACT REENTRANT_FUNC;
	//
	//
	//	/* Sensor Unit power control */

	public static void switchSensorUnitOn(int number, SensorUnit sensorUnit)
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
		// TODO: set data byte
		//SET_DATA_BYTE(SU_CONTROL,SU_ctrl_register);    

		// NOTE: Moved telemetry_data status update to the only call site 'Start_SU_SwitchingOn' 
	}


	public static void switchSensorUnitOff(int number, SensorUnit sensorUnit)
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

		// SET_DATA_BYTE(SU_CONTROL,SU_ctrl_register);       

		// NOTE: Moved telemetry_data status update to the only call site 'Start_SU_SwitchingOff' 
	}

	//
	//	/* Sensor Unit calibration */
	//
	//	extern void EnableAnalogSwitch(sensor_index_t self_test_SU_index);
	//	extern void DisableAnalogSwitch(sensor_index_t self_test_SU_index);
	//	extern void SelectSelfTestChannel(int /* unsigned char */ channel);
	//	extern void SelectTriggerSwitchLevel(
	//	           int /* unsigned char */  test_channel,
	//	           sensor_index_t self_test_SU_index);
	//	extern void SelectStartSwitchLevel(
	//	           int /* unsigned char */  test_channel,
	//	           sensor_index_t self_test_SU_index);	
}


