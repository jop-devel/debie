package debie.telecommand;

import static debie.telecommand.TelecommandExecutionTask.*;
import debie.target.SensorUnit;

public class TelemetryData {

	/* Sensor Unit low power and TC settings : */
	public static class SensorUnitSettings {
		/* unsigned char */ public int plus_5_voltage;                    /* word  1 */
		/* unsigned char */ public int minus_5_voltage;                   /* word  2 */
		/* unsigned char */ public int plasma_1_plus_threshold;           /* word  3 */
		/* unsigned char */ public int plasma_1_minus_threshold;          /* word  4 */
		/* unsigned char */ public int piezo_threshold;                   /* word  5 */
		/* unsigned char */ public int plasma_1_plus_classification;      /* word  6 */
		/* unsigned char */ public int plasma_1_minus_classification;     /* word  7 */
		/* unsigned char */ public int piezo_1_classification;            /* word  8 */
		/* unsigned char */ public int piezo_2_classification;            /* word  9 */
		/* unsigned char */ public int plasma_2_plus_classification;      /* word 10 */
		/* unsigned char */ public int plasma_1_plus_to_minus_max_time;   /* word 11 */
		/* unsigned char */ public int plasma_1_plus_to_piezo_min_time;   /* word 12 */
		/* unsigned char */ public int plasma_1_plus_to_piezo_max_time;   /* word 13 */
		/* unsigned char */ public int plasma_1_minus_to_piezo_min_time;  /* word 14 */
		/* unsigned char */ public int plasma_1_minus_to_piezo_max_time;  /* word 15 */

		/** Purpose        : Initializes classication parameter
		 *  Interface      : inputs      - none.
		 *                   outputs     - classification levels in telemetry_data.
		 *                               - threshold levels in telemetry_data.
		 *                               - min time window in telemetry_data
		 *                               - max time window in telemetry_data
		 *                   subroutines - none
		 *  Preconditions  : none.
		 *  Postconditions : outputs have their default values.
		 *  Algorithm      : Sets default values to telemetry_data. */
		public void init() {
			plasma_1_plus_threshold       = SensorUnit.DEFAULT_THRESHOLD;
			plasma_1_minus_threshold      = SensorUnit.DEFAULT_THRESHOLD;
			piezo_threshold               = SensorUnit.DEFAULT_THRESHOLD;
			plasma_1_plus_classification  = SensorUnit.DEFAULT_CLASSIFICATION_LEVEL;
			plasma_1_minus_classification = SensorUnit.DEFAULT_CLASSIFICATION_LEVEL;
			piezo_1_classification        = SensorUnit.DEFAULT_CLASSIFICATION_LEVEL;
			piezo_2_classification        = SensorUnit.DEFAULT_CLASSIFICATION_LEVEL;
			plasma_2_plus_classification  = SensorUnit.DEFAULT_CLASSIFICATION_LEVEL;
			plasma_1_plus_to_minus_max_time  = SensorUnit.DEFAULT_MAX_TIME;
			plasma_1_plus_to_piezo_min_time  = SensorUnit.DEFAULT_MIN_TIME;
			plasma_1_plus_to_piezo_max_time  = SensorUnit.DEFAULT_MAX_TIME;
			plasma_1_minus_to_piezo_min_time = SensorUnit.DEFAULT_MIN_TIME;
			plasma_1_minus_to_piezo_max_time = SensorUnit.DEFAULT_MAX_TIME;
		}
	};	

	/* unsigned char */ byte        error_status;                      /* reg   0       */
	/* unsigned char */ byte        mode_status;                       /* reg   1       */
	/* uint16_t */ char             TC_word;                           /* reg   2 -   3 */
	/* dpu_time_t */ int            TC_time_tag;                       /* reg   4 -   7 */
	/* unsigned char */ byte        watchdog_failures;                 /* reg   8       */
	/* unsigned char */ byte        checksum_failures;                 /* reg   9       */
	/* unsigned char */ byte        SW_version;                        /* reg  10       */
	/* unsigned char */ byte        isr_send_message_error;            /* reg  11       */
	/* unsigned char */ byte[]      SU_status = new byte[SensorUnit.NUM_SU];      /* reg  12 -  15 */
	/* unsigned char */ byte[]      SU_temperature = new byte[SensorUnit.NUM_SU * NUM_TEMP];  /* reg  16 -  23 */
	/* unsigned char */ byte        DPU_plus_5_digital;                /* reg  24       */
	/* unsigned char */ byte        os_send_message_error;             /* reg  25       */
	/* unsigned char */ byte        os_create_task_error;              /* reg  26       */
	/* unsigned char */ byte        SU_plus_50;                        /* reg  27       */
	/* unsigned char */ byte        SU_minus_50;                       /* reg  28       */
	/* unsigned char */ byte        os_disable_isr_error;              /* reg  29       */
	/* unsigned char */ byte        not_used_1;                        /* reg  30       */
	public SensorUnitSettings       sensor_unit_1; /* reg  31 -  45 */
	/* unsigned char */ byte        os_wait_error;                     /* reg  46       */
	public SensorUnitSettings       sensor_unit_2;                     /* reg  47 -  61 */
	/* unsigned char */ byte        os_attach_interrupt_error;         /* reg  62       */
	public SensorUnitSettings       sensor_unit_3;                     /* reg  63 -  77 */
	/* unsigned char */ byte        os_enable_isr_error;               /* reg  78       */
	public SensorUnitSettings       sensor_unit_4;                     /* reg  79 -  93 */
	/* code_address_t */ char       failed_code_address;               /* reg  94 -  95 */
	/* data_address_t */ char       failed_data_address;               /* reg  96 -  97 */
	/* uint16_t */ char  []         SU_hits = new char[SensorUnit.NUM_SU];        /* reg  98 - 105 */
	/* tm_dpu_time_t */ int         time;                              /* reg 106 - 109 */
	/* unsigned char */ byte        software_error;                    /* reg 110       */
	/* unsigned char */ byte        hit_budget_exceedings;             /* reg 111       */
	public /* unsigned char */ byte[] coefficient = new byte[NUM_QCOEFF];/* reg 112 - 116 */
	/* unsigned char */ byte        not_used;                          /* reg 117       */
	/* The last register of telemetry data should be 'not_used'.   */
	/* This is necessary for correct operation of telemetry        */
	/* retrieving TCs i.e. number of bytes should be even.         */

	public byte getSensorUnitTemperatur(int sensorUnit, int tempIx) {
		return SU_temperature[(sensorUnit << 1) + (tempIx & 0x01)];
	}
}
