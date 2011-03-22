package debie.telecommand;

import static debie.telecommand.TelecommandExecutionTask.*;
import static debie.target.SensorUnitDev.NUM_SU;
import debie.particles.EventRecord;
import debie.particles.SensorUnitSettings;
import debie.support.TelemetryObject;
import debie.target.TcTmDev;

public class TelemetryData implements TelemetryObject {

	/* Modes */
	public static final int DPU_SELF_TEST =  0;
	public static final int STAND_BY =       1;
	public static final int ACQUISITION =    2;
	public static final int MODE_BITS_MASK = 3;

	/* unsigned char */ byte        error_status;                      /* reg   0       */
	/* unsigned char */ byte        mode_status;                       /* reg   1       */
	/* uint16_t */      char        TC_word;                           /* reg   2 -   3 */
	/* dpu_time_t */    int         TC_time_tag;                       /* reg   4 -   7 */
	/* unsigned char */ int         watchdog_failures;                 /* reg   8       */
	/* unsigned char */ int         checksum_failures;                 /* reg   9       */
	/* unsigned char */ byte        SW_version;                        /* reg  10       */
	/* unsigned char */ byte        isr_send_message_error;            /* reg  11       */
	/* unsigned char */ byte[]      SU_status = new byte[NUM_SU];      /* reg  12 -  15 */
	/* unsigned char */ int[]       SU_temperature = new int[NUM_SU * NUM_TEMP];  /* reg  16 -  23 */
	public /* unsigned char */ byte        DPU_plus_5_digital;                /* reg  24       */
	/* unsigned char */ byte        os_send_message_error;             /* reg  25       */
	/* unsigned char */ byte        os_create_task_error;              /* reg  26       */
	/* unsigned char */ public byte        SU_plus_50;                        /* reg  27       */
	/* unsigned char */ public byte        SU_minus_50;                       /* reg  28       */
	/* unsigned char */ byte        os_disable_isr_error;              /* reg  29       */
	/* unsigned char */ byte        not_used_1;                        /* reg  30       */
	SensorUnitSettings              sensor_unit_1 = new SensorUnitSettings();
	                                                                   /* reg  31 -  45 */
	/* unsigned char */ byte        os_wait_error;                     /* reg  46       */
	SensorUnitSettings              sensor_unit_2 = new SensorUnitSettings();
                                                                       /* reg  47 -  61 */
	/* unsigned char */ byte        os_attach_interrupt_error;         /* reg  62       */
	SensorUnitSettings              sensor_unit_3 = new SensorUnitSettings();
                                                                       /* reg  63 -  77 */
	/* unsigned char */ byte        os_enable_isr_error;               /* reg  78       */
	SensorUnitSettings              sensor_unit_4 = new SensorUnitSettings();
                                                                       /* reg  79 -  93 */
	/* code_address_t */ char       failed_code_address;               /* reg  94 -  95 */
	/* data_address_t */ char       failed_data_address;               /* reg  96 -  97 */
	/* uint16_t */ char  []         SU_hits = new char[NUM_SU];        /* reg  98 - 105 */
	/* tm_dpu_time_t */ int         time;                              /* reg 106 - 109 */

	/* unsigned char */ byte        software_error;                    /* reg 110       */
	/* unsigned char */ public int  hit_budget_exceedings;             /* reg 111       */
	/* unsigned char */ byte[]      coefficient = new byte[NUM_QCOEFF];/* reg 112 - 116 */
//	/* unsigned char */ byte        not_used;                          /* reg 117       */

	/* The last register of telemetry data should be 'not_used'.   */
	/* This is necessary for correct operation of telemetry        */
	/* retrieving TCs i.e. number of bytes should be even.         */

	public static final int TIME_INDEX = 106;

	private static final int SIZE_IN_BYTES = 120;
	public static int sizeInBytes() {
		return SIZE_IN_BYTES;
	}
	
	/** FIXME: this is just a stub, but is there a good solution for serialization in Java? */
	public int getByte(int index) {
		switch (index) {
		case 0: return error_status & 0xff;
		case 1: return mode_status & 0xff;
		case 2: return TC_word & 0xff;
		case 3: return (TC_word >> 8) & 0xff;
		case 4: return TC_time_tag & 0xff;
		case 5: return (TC_time_tag >> 8) & 0xff;
		case 6: return (TC_time_tag >> 16) & 0xff;
		case 7: return (TC_time_tag >> 24) & 0xff;
		case 8: return watchdog_failures & 0xff;
		case 9: return checksum_failures & 0xff;
		case 10: return SW_version & 0xff;
		case 11: return isr_send_message_error & 0xff;
		case 12: case 13: case 14: case 15:
			return SU_status[index-12] & 0xff;
		case 16: case 17: case 18: case 19:
		case 20: case 21: case 22: case 23:
			return SU_temperature[index-16] & 0xff;
		case 24: return DPU_plus_5_digital & 0xff;
		case 25: return os_send_message_error & 0xff;
		case 26: return os_create_task_error & 0xff;
		case 27: return SU_plus_50 & 0xff;
		case 28: return SU_minus_50 & 0xff;
		case 29: return os_disable_isr_error & 0xff;
		case 30: return not_used_1 & 0xff;
		case 31: case 32: case 33: case 34:
		case 35: case 36: case 37: case 38:
		case 39: case 40: case 41: case 42:
		case 43: case 44: case 45:
			return sensor_unit_1.getByte(index-31);
		case 46: return os_wait_error & 0xff;
		case 47: case 48: case 49: case 50:
		case 51: case 52: case 53: case 54:
		case 55: case 56: case 57: case 58:
		case 59: case 60: case 61:
			return sensor_unit_2.getByte(index-47);
		case 62: return os_attach_interrupt_error & 0xff;
		case 63: case 64: case 65: case 66:
		case 67: case 68: case 69: case 70:
		case 71: case 72: case 73: case 74:
		case 75: case 76: case 77:
			return sensor_unit_3.getByte(index-63);
		case 78: return os_enable_isr_error & 0xff;
		case 79: case 80: case 81: case 82:
		case 83: case 84: case 85: case 86:
		case 87: case 88: case 89: case 90:
		case 91: case 92: case 93:
			return sensor_unit_4.getByte(index-79);
		case 94: return failed_code_address & 0xff;
		case 95: return (failed_code_address >> 8) & 0xff;
		case 96: return failed_data_address & 0xff;
		case 97: return (failed_data_address >> 8) & 0xff;
		case 98: case 100: case 102: case 104:
			return SU_hits[(index-98) >> 1] & 0xff;
		case 99: case 101: case 103: case 105:
			return (SU_hits[(index-98) >> 1] >> 8) & 0xff;
		// case 106: case 107: padding in original code
		case 108: return time & 0xff;
		case 109: return (time >> 8) & 0xff;
		case 110: return (time >> 16) & 0xff;
		case 111: return (time >> 24) & 0xff;
		case 112: return software_error & 0xff;
		case 113: return hit_budget_exceedings & 0xff;
		case 114: case 115: case 116: case 117: case 118:
			return coefficient[index-114];
		}
		return 0; // not_used;		
	}
	
	public TelemetryData() {
	}
	
	public int getSensorUnitTemperature(int sensorUnit, int tempIx) {
		return SU_temperature[(sensorUnit << 1) + (tempIx & 0x01)];
	}

	public void setSensorUnitTemperature(int sensorUnit, int tempIx, int value) {
		SU_temperature[(sensorUnit << 1) + (tempIx & 0x01)] = value;
	}

	public byte getErrorStatus() {
		return error_status;
	}

	public void setErrorStatus(byte val) {
		error_status |= val & ~TcTmDev.TC_ERROR;
	}	

	public void setErrorStatusRaw(byte val) {
		error_status = val;
	}	

	public void setISRSendMessageError(byte val) {
		isr_send_message_error = val;
	}

	public void setFailedCodeAddress(char failed_code_address) {
		this.failed_data_address = failed_code_address;
	}
	public void setFailedDataAddress(char failed_data_address) {
		this.failed_data_address = failed_data_address;
	}

	/** {@code telemetry_data.mode_status & MODE_BITS_MASK) } */
	public int getMode() {
		return mode_status & MODE_BITS_MASK;
	}
	
	public int getModeBits() {
		return mode_status;
	}

	public void clearModeBits(int mask) {
		mode_status &= ~mask;
	}
	
	public void setModeBits(int mask) {
		mode_status |= mask;
	}
	
	/**
	 * Purpose        : This function will be called always when all
	 *                  error bits in the mode status register are cleared.
	 * Interface      : inputs      - mode status register
	 *                  outputs     - mode status register
	 *                  subroutines - none
	 * Preconditions  : none
	 * Postconditions : none
	 * Algorithm      : - Disable interrupts
	 *                  - Write to Mode Status register
	 *                  - Enable interrupts
	 */
	void clearModeStatusError()	{
//	   DISABLE_INTERRUPT_MASTER;

		mode_status &= MODE_BITS_MASK;
		/* Error bits in the mode status register are cleared. */

//	   ENABLE_INTERRUPT_MASTER;
	}

	
	public void clearAll() {
		error_status = 0;
		mode_status = 0;
		TC_word = 0;
		TC_time_tag = 0;
		watchdog_failures = 0;
		checksum_failures = 0;
		SW_version = 0;
		isr_send_message_error = 0;
		for (int i = 0; i < NUM_SU; i++) {
			SU_status[i] = 0;
		}
		for (int i = 0; i < NUM_SU; i++) {
			SU_temperature[i] = 0;
		}
		DPU_plus_5_digital = 0;
		os_send_message_error = 0;
		os_create_task_error = 0;
		SU_plus_50 = 0;
		SU_minus_50 = 0;
		os_disable_isr_error = 0;
		not_used_1 = 0;
		sensor_unit_1.clearAll();
		os_wait_error = 0;
		sensor_unit_2.clearAll();
		os_attach_interrupt_error = 0;
		sensor_unit_3.clearAll();
		os_enable_isr_error = 0;
		sensor_unit_4.clearAll();
		failed_code_address = 0;
		failed_data_address = 0;		
		for (int i = 0; i < NUM_SU; i++) {
			SU_hits[i] = 0;
		}
		time = 0;
		software_error = 0;
		hit_budget_exceedings = 0;
		for (int i = 0; i < NUM_QCOEFF; i++) {
			coefficient[i] = 0;
		}
		// not_used = 0;
	}
	
	/**
	 * Purpose        : Clears RTX error registers in telemetry
	 * Interface      : input   -
	 *                  output  - telemetry_data, rtx error registers
	 * Preconditions  : none
	 * Postconditions : RTX error registers are cleared.
	 * Algorithm      : See below, self explanatory. 
	 */ 
	public void clearRTXErrors() {
	   isr_send_message_error    = (byte)0xFF;
	   os_send_message_error     = (byte)0xFF;
	   os_create_task_error      = (byte)0xFF;
	   os_wait_error             = (byte)0xFF;
	   os_attach_interrupt_error = (byte)0xFF;
	   os_enable_isr_error       = (byte)0xFF; 
	   os_disable_isr_error      = (byte)0xFF;
	}

	/**
	 * Purpose        : This function will be called always when all
	 *                  error bits in the SU# status register are cleared.
	 * Interface      : inputs      - SU# status register
	 *                  outputs     - SU# status register
	 *                  subroutines - none
	 * Preconditions  : none
	 * Postconditions : none
	 * Algorithm      : - Disable interrupts
	 *                  - Write to Mode Status register
	 *                  - Enable interrupts
	 */
	public void clearSUError() {
//	   DISABLE_INTERRUPT_MASTER;
	   for (int i = 0; i < NUM_SU; i++)
	   {
	      SU_status[i] &= SUPPLY_VOLTAGE_MASK;
	      /* Error bits in the SU# status register are cleared. */
	   }

//	   ENABLE_INTERRUPT_MASTER;
	}

	/**
	 * Purpose        : This function will be called always when 
	 *                  error bit(s) in the SU# status register are set.
	 * Interface      : inputs      - SU# status register
	 *        	                    - 'SU_index' (0-3)
	 *                              - 'SU_error' is one of the following:
	 *
	 *                                 LV_SUPPLY_ERROR
	 *                                 HV_SUPPLY_ERROR
	 *                                 TEMPERATURE_ERROR
	 *                                 SELF_TEST_ERROR
	 *
	 *                  outputs     - SU# status register
	 *                  subroutines - none
	 * Preconditions  : none
	 * Postconditions : none
	 * Algorithm      : - Disable interrupts
	 *                  - Write to SU# status register
	 *                  - Set corresponding SU# error bit in the
	 *                    error status register.
	 *                  - Enable interrupts
	 */
	public void setSUError(int SU_index, byte SU_error) {
	 
//	   DISABLE_INTERRUPT_MASTER;
	   
	      SU_status[SU_index] |= (SU_error &(~SUPPLY_VOLTAGE_MASK));
	      /* Error bits in the SU# status register are cleared. */
	      /* The voltage status bits in the SU# status register */
	      /* are secured against unintended modification by     */
	      /* clearing those bits in 'SU_error' before           */
	      /* "or":ing its value to                              */
	      /* 'telemetry_data.SU_status'.                        */
	 
	      setErrorStatus((byte)(ERROR_STATUS_OFFSET << SU_index));      
	      /* SU# error is set in the error status register, if      */
	      /* anyone of the error bits in the SU# status register    */
	      /* is set.                                                */
	      /* Because this subroutine enables itself the interrupts, */
	      /* the call of it must be the last operation in the       */ 
	      /* interrupt blocked area !                               */

//	   ENABLE_INTERRUPT_MASTER;
	}

	
	/**
	 * Purpose        : This function will be called always when
	 *                  error bits in the error status register are cleared.
	 * Interface      : inputs      - error status register
	 *                  outputs     - error status register
	 *                  subroutines - none
	 * Preconditions  : none
	 * Postconditions : none
	 * Algorithm      :
	 *                  - Write to error status register
	 */
	public void clearErrorStatus() {
		error_status = 0;
	    /* Error bits in the error status register are      */
	    /* cleared.			                  */
	}
	
	/**
	 * Purpose        : This function will be called always when
	 *                  bit(s) in the software error status
	 *                  register are set.
	 * Interface      : inputs      - software error status register
	 *                              - measurement_error, which specifies what
	 *                                bits are set in software error status.
	 *                                Value is as follows,
	 *
	 *                                MEASUREMENT_ERROR
	 *
	 *                  outputs     - software error status register
	 *                  subroutines - none
	 * Preconditions  : none
	 * Postconditions : none
	 * Algorithm      : - Disable interrupts
	 *                  - Write to software error status register
	 *                  - Enable interrupts
	 */
	public void setSoftwareError(int error) {
//	   DISABLE_INTERRUPT_MASTER;

	   software_error |= error;

//	   ENABLE_INTERRUPT_MASTER;
	}
	
	/**
	 * Purpose        : This function will be called always when all
	 *                  bits in the software error status
	 *                  register are cleared.
	 * Interface      : inputs      - software error status register
	 *                  outputs     - software error status register
	 *                  subroutines - none
	 * Preconditions  : none
	 * Postconditions : none
	 * Algorithm      :
	 *                  - Write to SoftwareErrorStatuRegister
	 */
	public void clearSoftwareError() {
		software_error = 0;
	}

	
	/* getter/setter for coefficient array */
	public void initCoefficients() {
		for (int i=0; i<NUM_QCOEFF; i++) {
			coefficient[i] = EventRecord.DEFAULT_COEFF;
		}
	}	
	public byte[] getCoefficients() {
		return coefficient;
	}

	/* getters for sensor unit settings */
	public SensorUnitSettings getSensorUnit1() {
		return sensor_unit_1;
	}
	public SensorUnitSettings getSensorUnit2() {
		return sensor_unit_2;
	}
	public SensorUnitSettings getSensorUnit3() {
		return sensor_unit_3;
	}
	public SensorUnitSettings getSensorUnit4() {
		return sensor_unit_4;
	}

	public SensorUnitSettings getSuConfig(int sensorUnitIndex) {
		switch(sensorUnitIndex) {
		case 0: return this.sensor_unit_1;
		case 1: return this.sensor_unit_2;
		case 2: return this.sensor_unit_3;
		case 3: return this.sensor_unit_4;
		default: throw new RuntimeException("getSuConfig: invalid index");
		}
	}

	public void setSWVersion(byte version) {
		SW_version = version;
	}

	public int getWatchdogFailures() {
		return watchdog_failures;
	}	

	public void setWatchdogFailures(int value) {
		watchdog_failures = value;
	}	

	public void incrementWatchdogFailures() {
		if (watchdog_failures < 255) {
			watchdog_failures++;
		}
	}

	public void resetWatchdogFailures() {
		watchdog_failures = 0;
	}

	public int getChecksumFailures() {
		return checksum_failures;
	}	

	public void setChecksumFailures(int value) {
		checksum_failures = value;
	}	

	public void incrementChecksumFailures() {
		if (checksum_failures < 255) {
			checksum_failures++;
		}
	}

	public void resetChecksumFailures() {
		checksum_failures = 0;		
	}

	public void resetTCWord() {
		TC_word = 0;
	}
}
