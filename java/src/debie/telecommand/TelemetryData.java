package debie.telecommand;

import static debie.telecommand.TelecommandExecutionTask.*;
import static debie.target.SensorUnitDev.NUM_SU;
import debie.particles.EventRecord;
import debie.particles.SensorUnitSettings;
import debie.support.TelemetryObject;

public class TelemetryData implements TelemetryObject {

	/* Modes */
	public static final int DPU_SELF_TEST =  0;
	public static final int STAND_BY =       1;
	public static final int ACQUISITION =    2;
	public static final int MODE_BITS_MASK = 3;

	/* unsigned char */ byte        error_status;                      /* reg   0       */
	/* unsigned char */ byte        mode_status;                       /* reg   1       */
	/* uint16_t */ char             TC_word;                           /* reg   2 -   3 */
	/* dpu_time_t */ int            TC_time_tag;                       /* reg   4 -   7 */
	/* unsigned char */ int         watchdog_failures;                 /* reg   8       */
	/* unsigned char */ int         checksum_failures;                 /* reg   9       */
	/* unsigned char */ byte        SW_version;                        /* reg  10       */
	/* unsigned char */ byte        isr_send_message_error;            /* reg  11       */
	/* unsigned char */ byte[]      SU_status = new byte[NUM_SU];      /* reg  12 -  15 */
	/* unsigned char */ byte[]      SU_temperature = new byte[NUM_SU * NUM_TEMP];  /* reg  16 -  23 */
	public /* unsigned char */ byte        DPU_plus_5_digital;                /* reg  24       */
	/* unsigned char */ byte        os_send_message_error;             /* reg  25       */
	/* unsigned char */ byte        os_create_task_error;              /* reg  26       */
	/* unsigned char */ byte        SU_plus_50;                        /* reg  27       */
	/* unsigned char */ byte        SU_minus_50;                       /* reg  28       */
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
	/* unsigned char */ byte        hit_budget_exceedings;             /* reg 111       */
	/* unsigned char */ byte[]      coefficient = new byte[NUM_QCOEFF];/* reg 112 - 116 */
//	/* unsigned char */ byte        not_used;                          /* reg 117       */

	/* The last register of telemetry data should be 'not_used'.   */
	/* This is necessary for correct operation of telemetry        */
	/* retrieving TCs i.e. number of bytes should be even.         */

	public static final int TIME_INDEX = 106;

	/** FIXME: this is just a stub, but is there a good solution for serialization in Java? */
	public int getByte(int index) {
		if(index == 0) return error_status;
		/* and so on, can't we use some internal magic for this ?? */
		return 0; // not_used;		
	}
	
	public TelemetryData() {
	}
	
	public byte getSensorUnitTemperature(int sensorUnit, int tempIx) {
		return SU_temperature[(sensorUnit << 1) + (tempIx & 0x01)];
	}

	public byte getErrorStatus() {
		return error_status;
	}

	public void setErrorStatus(byte val) {
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
		setErrorStatus((byte)0);
	    /* Error bits in the error status register are      */
	    /* cleared.			                  */
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
		case 1: return this.sensor_unit_1;
		case 2: return this.sensor_unit_1;
		case 3: return this.sensor_unit_1;
		default: throw new RuntimeException("getSuConfig: invalid index");
		}
	}

	public void setSWVersion(byte version) {
		SW_version = version;
	}

	public void incrementWatchdogFailures() {
		if (watchdog_failures < 255) {
			watchdog_failures++;
		}
	}

	public void resetWatchdogFailures() {
		watchdog_failures = 0;
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
