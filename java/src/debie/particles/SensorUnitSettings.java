/**
 * 
 */
package debie.particles;

import static debie.target.SensorUnitDev.*;

public class SensorUnitSettings {
	/* unsigned char */ int plus_5_voltage;                    /* word  1 */
	/* unsigned char */ int minus_5_voltage;                   /* word  2 */
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

	/** Purpose        : Initializes classification parameter
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
		plasma_1_plus_threshold       = DEFAULT_THRESHOLD;
		plasma_1_minus_threshold      = DEFAULT_THRESHOLD;
		piezo_threshold               = DEFAULT_THRESHOLD;
		plasma_1_plus_classification  = DEFAULT_CLASSIFICATION_LEVEL;
		plasma_1_minus_classification = DEFAULT_CLASSIFICATION_LEVEL;
		piezo_1_classification        = DEFAULT_CLASSIFICATION_LEVEL;
		piezo_2_classification        = DEFAULT_CLASSIFICATION_LEVEL;
		plasma_2_plus_classification  = DEFAULT_CLASSIFICATION_LEVEL;
		plasma_1_plus_to_minus_max_time  = DEFAULT_MAX_TIME;
		plasma_1_plus_to_piezo_min_time  = DEFAULT_MIN_TIME;
		plasma_1_plus_to_piezo_max_time  = DEFAULT_MAX_TIME;
		plasma_1_minus_to_piezo_min_time = DEFAULT_MIN_TIME;
		plasma_1_minus_to_piezo_max_time = DEFAULT_MAX_TIME;
	}
}
