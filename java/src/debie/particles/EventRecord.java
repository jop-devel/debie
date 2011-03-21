/*------------------------------------------------------------------------------
 *
 *    Copyright (C) 1998 : Space Systems Finland Ltd.
 *
 * Space Systems Finland Ltd (SSF) allows you to use this version of
 * the DEBIE-I DPU software for the specific purpose and under the
 * specific conditions set forth in the Terms Of Use document enclosed
 * with or attached to this software. In particular, the software
 * remains the property of SSF and you must not distribute the software
 * to third parties without written and signed authorization from SSF.
 *
 *   Ported to Java
 *   Based on the SSF DHI file msg_ctrl.h, rev 1.11, Mon May 17 22:50:44 1999.
 *   (C) 2011 : Wolfgang Puffitsch, Benedikt Huber, Martin Schoeberl 
 *
 *    System Name:   DEBIE DPU SW
 *    Module     :   Based on 'class.h', 'class.c', 'classtab.h', and 'classtab.c'
 *      
 *- * --------------------------------------------------------------------------
 */
 
package debie.particles;

import debie.particles.SensorUnit.SensorUnitState;
import debie.target.SensorUnitDev;
import debie.telecommand.TelecommandExecutionTask;

public class EventRecord {

	/** Maximum value for an amplitude term in the quality formula.
	 * Valid range: 1 - 255. */
	public static final int MAX_AMPLITUDE_TERM = 5;

	/** Default value for classification coefficient adjustable with
	 *  telecommands. Gives maximum allowed (5) amplitude term with
	 *  maximum amplitude with this formula. If amplitudes are going
	 *  to be smaller, the amplitude can be amplified by setting
	 *  greater value to the quality coefficient. Minimum
	 *  amplification is 1/5 and maximum 50.
	 *  Valid range 1 - 255. */
	public static final int DEFAULT_COEFF = 5;

	/** Divider for an amplitude term in the quality formula.  16 =
	 *  maxumum value for the rough 2 based logarithm of the signal
	 *  amplitude in the quality formula. */
	public static final float AMPLITUDE_DIVIDER = ((DEFAULT_COEFF * 16.0f) / MAX_AMPLITUDE_TERM);

	/* Classification index mask values for signal amplitudes above
	 * the classification levels. */
	public static final int PLASMA_1_PLUS_CLASS  = 0x80;
	public static final int PLASMA_1_MINUS_CLASS = 0x40;
	public static final int PLASMA_2_PLUS_CLASS  = 0x08;
	public static final int PIEZO_1_CLASS        = 0x20;
	public static final int PIEZO_2_CLASS        = 0x10;

	/* Classification index mask values for delays inside the time
	 * windows. */
	public static final int PLASMA_1_PLUS_TO_PIEZO_CLASS  = 0x02;
	public static final int PLASMA_1_MINUS_TO_PIEZO_CLASS = 0x01;
	public static final int PLASMA_1_PLUS_TO_MINUS_CLASS  = 0x04;

	private static final int MAX_QUALITY = 255;
	
	int       quality_number;    /* byte  0  XXX: was unsigned char
    										 XXX: extended to int to avoid loss of
    										  	  precision following offsets are wrong now */
	byte      classification;    /* byte  1  XXX: was unsigned char    */
	byte      SU_number;         /* byte  2  XXX: was unsigned char    */
	int       hit_time;          /* byte  3 -  6 XXX: was tm_dpu_time_t */
	byte      SU_temperature_1;  /* byte  7  XXX: was unsigned char        */
	byte      SU_temperature_2;  /* byte  8  XXX: was unsigned char        */
	char      plasma_1_plus;     /* byte  9 - 10 XXX: was tm_ushort_t*/
	char      plasma_1_minus;    /* byte 11 - 12 XXX: was tm_ushort_t*/
	char      piezo_1;           /* byte 13 - 14 XXX: was tm_ushort_t*/
	char      piezo_2;           /* byte 15 - 16 XXX: was tm_ushort_t*/
	char      plasma_2_plus;     /* byte 17 - 18 XXX: was tm_ushort_t*/
	byte      rise_time;         /* byte 19 XXX: was unsigned char         */
	byte      delay_1;            /* byte 20 XXX: was SIGNED char         */
	char      delay_2;           /* byte 21 - 22 XXX: was tm_ushort_t */
	char      delay_3;           /* byte 23 - 24 XXX: was tm_ushort_t */
	byte      checksum;          /* byte 25 XXX: was unsigned char */

	private static final int SIZE_IN_BYTES = 26;
	public static int sizeInBytes() {
		return SIZE_IN_BYTES;
	}

	/* getters/setters to provide access for TelecommandExecutionTask */
	public int getQualityNumber() {
		return quality_number;
	}
	public void setQualityNumber(int val) {
		quality_number = val;
	}
	public int getHitTime() {
		return hit_time;
	}
	public byte getClassification() {
		return classification;
	}
	public byte getSUNumber() {
		return SU_number;
	}
	
	/*--- XXX: In contrast to C, we need to explicitly calculate the checksum,
	 * iterating over all 24 bytes */
	/* Maybe there should be some support in SCJ for treating data as byte arrays? */
	/* FIXME: This is really ugly; maybe we should use a checksum class */
	public void calculateCheckSum() {
		/* These variables are used when checksum is computed for a given event
		 * before storing it to Science data area.                                */
        int tmp;
        byte new_checksum = 0;

//      for (i = 1; i < sizeof(event_record_t); i++)
//      {
//         event_checksum ^= *checksum_pointer;
//         checksum_pointer++;
//      }
        /*--- i=1 ==> quality_number is not part of the checksum */
        new_checksum ^= classification;
        new_checksum ^= SU_number;
        // XXX: order does not matter, because ^ is commutative
        tmp=hit_time;
        for(int i = 0; i < 4; ++i) {
        	new_checksum ^= tmp & 0xFF; 
        	tmp >>>= 8;
        }
        new_checksum ^= SU_temperature_1;
        new_checksum ^= SU_temperature_2;
        new_checksum ^= plasma_1_plus & 0xFF;
        new_checksum ^= plasma_1_plus >>> 8;
        new_checksum ^= plasma_1_minus & 0xFF;
        new_checksum ^= plasma_1_minus >>> 8;
        new_checksum ^= piezo_1 & 0xFF;
        new_checksum ^= piezo_1 >>> 8;
        new_checksum ^= piezo_2 & 0xFF;
        new_checksum ^= piezo_2 >>> 8;
        new_checksum ^= plasma_2_plus & 0xFF;
        new_checksum ^= plasma_2_plus >>> 8;
        new_checksum ^= rise_time;
        new_checksum ^= delay_1;
        new_checksum ^= delay_2 & 0xFF;
        new_checksum ^= delay_2 >>> 8;
        new_checksum ^= delay_3 & 0xFF;
        new_checksum ^= delay_3 >>> 8;
        checksum = new_checksum;
	}

	/* XXX: replace STRUCT_ASSIGN
	 * FIXME: this is much worse than the corresponding C code :( */
	public void copyFrom(EventRecord eventRecord) {
		quality_number = eventRecord.quality_number;
		classification = eventRecord.classification;
		SU_number = eventRecord.SU_number;
		hit_time = eventRecord.hit_time;
		SU_temperature_1 = eventRecord.SU_temperature_1;
		SU_temperature_2 = eventRecord.SU_temperature_2;
		plasma_1_plus = eventRecord.plasma_1_plus;
		plasma_1_minus = eventRecord.plasma_1_minus;
		piezo_1 = eventRecord.piezo_1;
		piezo_2 = eventRecord.piezo_2;
		plasma_2_plus = eventRecord.plasma_2_plus;
		rise_time = eventRecord.rise_time;
		delay_1 = eventRecord.delay_1;
		delay_2 = eventRecord.delay_2;
		delay_3 = eventRecord.delay_3;
		checksum = eventRecord.checksum;
	}


	/** An integer approximation (0..16) of the base-2 log of x
	 * computed as the number of the most-significant non-zero bit.
	 * When x = 0, returns zero.
	 * When x > 0, returns floor(log2(x) + 1).
	 * For example, when x =     1, returns  1.
	 *              when x =     2, returns  2.
	 *              when x =     7, returns  3.
	 *              when x = x7FFF, returns 15.
	 *              when x = x8000, returns 16. */
	private int roughLogarithm (int x) {
		int greatest_non_zero_bit;
		int shifted;
	
		greatest_non_zero_bit = 0;
		shifted = x;
	
		while (shifted != 0) {
			greatest_non_zero_bit++;
			shifted >>>= 1;
		}
	
		return greatest_non_zero_bit;
	}

	/** Purpose        : Calculates the ampltude term of the quality formula.
	 *  Interface      : inputs      - parameter coeff defines number of the
	 *                                 quality coefficient.
	 *                               - parameter amplitude defines the signal 
	 *                                 amplitude.
	 *                   outputs     - quality term value is retruned.
	 *                   subroutines - RoughLogarithm.
	 *  Preconditions  : quality coefficients have valid values.
	 *  Postconditions : none.
	 *  Algorithm      : quality term is calculated according to following
	 *                   formula :
	 *                      coefficient[coeff]
	 *                      * RoughLogaritm(amplitude)
	 *                      / AMPLTUDE_DIVIDER
	 *                   where coefficient[coeff] is the amplitude coefficient of
	 *                   the quality term and AMPLTUDE_DIVIDER is a scaling
	 *                   factor whose purpose is scale the result below 5.0.
	 *                   However if the result would become larger than that,
	 *                   5.0 is returned. */
	private float getQualityTerm(int coeff, int amplitude) {
		float quality;
	
		quality = 
			(float)(TelecommandExecutionTask.getTelemetryData().getCoefficients()[coeff]
					* roughLogarithm(amplitude))
			/ AMPLITUDE_DIVIDER;
	
		if (quality > 5.0f) {
			quality = 5.0f;
		}
	
		return quality;
	}

	/** Purpose        : Calculates the quality number of a particle hit event
	 *                   and stores in the event record.
	 *  Interface      : inputs      - event record pointed by the parameter.
	 *                   outputs     - event record pointed by the parameter.
	 *                   subroutines - GetQualityTerm.
	 *  Preconditions  : All components of the event record pointed by the
	 *                   parameter which are used as input have valid values.
	 *  Postconditions : quality_number component of the event record pointed
	 *                   by the parameter has is calculated.
	 *  Algorithm      : quality_number is calculated according to the following
	 *                   formula :
	 *                      25 * Class + Ai*RoughLogarithm(Si) / Divider , where
	 *                   Class   is the class of the event,
	 *                   Ai      is the amplitude coefficient of the quality
	 *                              formula
	 *                   Si      is a signal amplitude from the Sensor Unit Peak
	 *                              detector
	 *                   Divider is scaling factor whose value is determined by
	 *                              the maximum value (5) of the latter terms
	 *                   and i goes from 1 to 5. */
	private void calculateQualityNumber() {
		float quality;
	
		quality = 25.0f * classification;
		/* First term of the quality formula. */
	
		quality += getQualityTerm(0, plasma_1_plus);
		/* Add amplitude term for i=1 (see function algorithm). */
	
		quality += getQualityTerm(1, plasma_1_minus);
		/* Add amplitude term for i=2 (see function algorithm). */
	
		quality += getQualityTerm(2, piezo_1);
		/* Add amplitude term for i=3 (see function algorithm). */
		
		quality += getQualityTerm(3, piezo_2);
		/* Add amplitude term for i=4 (see function algorithm). */
		
		quality += getQualityTerm(4, plasma_2_plus);
		/* Add amplitude term for i=5 (see function algorithm). */
		
		quality_number = (byte)(quality + 0.5f);
		/* Store quality number to the event record */
	}

	/** Purpose        : Classifies a particle hit event and stores result
	 *                   to the event record pointed by the parameter.
	 *  Interface      : inputs      - event record pointed by the parameter.
	 *                   outputs     - event record pointed by the parameter.
	 *                   subroutines - CalculateQualityNumber.
	 *  Preconditions  : All components of the event record pointed by the
	 *                   parameter which are used as input have valid values.
	 *  Postconditions : classification and quality_number components of the
	 *                   event record pointed by the parameter are computed.
	 *  Algorithm      : - class index is determined by comparing signal.
	 *                     amplitudes and time delays to classification
	 *                     thresholds.
	 *                   - class number is read from a look-up table using the
	 *                     class index and stored in the event record.
	 *                   - CalculateQualityNumber is called. */
	public void classify() {
	
		int class_index;
		/* Index for the class look-up table. */
	
		SensorUnitSettings limits = null;
		/* Pointer to the struct holding classification thresholds. */
	
		class_index = 0;
		/* Bits will be set below according to event attributes. */
		
		switch (SU_number) {
			/* Select proper classification thresholds. */
			
		case SensorUnitDev.SU_1:
			limits = TelecommandExecutionTask.getTelemetryData().getSensorUnit1();
			break;
	
		case SensorUnitDev.SU_2:
			limits = TelecommandExecutionTask.getTelemetryData().getSensorUnit2();
			break;
			
		case SensorUnitDev.SU_3:
			limits = TelecommandExecutionTask.getTelemetryData().getSensorUnit3();
			break;
			
		case SensorUnitDev.SU_4:
			limits = TelecommandExecutionTask.getTelemetryData().getSensorUnit4();
			break;
		}
	
	    if (plasma_1_plus >=
			(limits.plasma_1_plus_classification * 256)) {
			class_index |= PLASMA_1_PLUS_CLASS;
			/* Set classification index bit for Plasma1+ peak amplitude. */ 
		}
	
		if (plasma_1_minus >=
			(limits.plasma_1_minus_classification * 256)) {
			class_index |= PLASMA_1_MINUS_CLASS;
			/* Set classification index bit for Plasma1- peak amplitude. */ 
		}
	
		if (piezo_1 >=
			(limits.piezo_1_classification * 256)) {
			class_index |= PIEZO_1_CLASS;
			/* Set classification index bit for Piezo1 peak amplitude. */ 
		}
	
		if (piezo_2 >=
			(limits.piezo_2_classification * 256)) {
			class_index |= PIEZO_2_CLASS;
			/* Set classification index bit for Piezo2 peak amplitude. */ 
		}
	
		if (plasma_2_plus >=
			(limits.plasma_2_plus_classification * 256)) {
			class_index |= PLASMA_2_PLUS_CLASS;
			/* Set classification index bit for Plasma2+ peak amplitude. */ 
		}
	
		if (delay_2 >=
			(limits.plasma_1_plus_to_piezo_min_time * 16) &&
			delay_2 <=
			(limits.plasma_1_plus_to_piezo_max_time * 16)) {
			class_index |= PLASMA_1_PLUS_TO_PIEZO_CLASS;
			/* Set classification index bit for Plasma1+ to Piezo delay. */ 
		}
	
		if (delay_3 >=
			(limits.plasma_1_minus_to_piezo_min_time * 16) &&
			delay_3 <=
			(limits.plasma_1_minus_to_piezo_max_time * 16)) {
			class_index |= PLASMA_1_MINUS_TO_PIEZO_CLASS;
			/* Set classification index bit for Plasma1- to Piezo delay. */ 
		}
	
		if (delay_1 <=
			limits.plasma_1_plus_to_minus_max_time) {
			class_index |= PLASMA_1_PLUS_TO_MINUS_CLASS;
			/* Set classification index bit for Plasma1+ to Plasma1- delay. */ 
		}
	
		classification = EventClass[class_index];
		/* Store classification number to the event record */
	
		if (AcquisitionTask.sensorUnitState[SU_number - SensorUnitDev.SU_1] == 
			SensorUnitState.self_test_e) {
			quality_number = MAX_QUALITY;
		} else {
			calculateQualityNumber();
		}
	}

	/** Table for classifying event according to multiple measurements.
	 *  @see code/classtab.h
	 *  @see code/classtab.c */
	private static final byte [] EventClass = {
		/* Que  Qui  P1  P2  Qle  dTei     dTep     dTip       */
		0,   /*  0    0    0   0   0   OUTSIDE  OUTSIDE  OUTSIDE    */
		0,   /*  0    0    0   0   0   OUTSIDE  OUTSIDE  INSIDE     */
		0,   /*  0    0    0   0   0   OUTSIDE  INSIDE   OUTSIDE    */
		0,   /*  0    0    0   0   0   OUTSIDE  INSIDE   INSIDE     */
		0,   /*  0    0    0   0   0   INSIDE   OUTSIDE  OUTSIDE    */
		0,   /*  0    0    0   0   0   INSIDE   OUTSIDE  INSIDE     */
		0,   /*  0    0    0   0   0   INSIDE   INSIDE   OUTSIDE    */
		0,   /*  0    0    0   0   0   INSIDE   INSIDE   INSIDE     */
		0,   /*  0    0    0   0   1   OUTSIDE  OUTSIDE  OUTSIDE    */
		0,   /*  0    0    0   0   1   OUTSIDE  OUTSIDE  INSIDE     */
		0,   /*  0    0    0   0   1   OUTSIDE  INSIDE   OUTSIDE    */
		0,   /*  0    0    0   0   1   OUTSIDE  INSIDE   INSIDE     */
		0,   /*  0    0    0   0   1   INSIDE   OUTSIDE  OUTSIDE    */
		0,   /*  0    0    0   0   1   INSIDE   OUTSIDE  INSIDE     */
		0,   /*  0    0    0   0   1   INSIDE   INSIDE   OUTSIDE    */
		0,   /*  0    0    0   0   1   INSIDE   INSIDE   INSIDE     */
		0,   /*  0    0    0   1   0   OUTSIDE  OUTSIDE  OUTSIDE    */
		0,   /*  0    0    0   1   0   OUTSIDE  OUTSIDE  INSIDE     */
		0,   /*  0    0    0   1   0   OUTSIDE  INSIDE   OUTSIDE    */
		0,   /*  0    0    0   1   0   OUTSIDE  INSIDE   INSIDE     */
		0,   /*  0    0    0   1   0   INSIDE   OUTSIDE  OUTSIDE    */
		0,   /*  0    0    0   1   0   INSIDE   OUTSIDE  INSIDE     */
		0,   /*  0    0    0   1   0   INSIDE   INSIDE   OUTSIDE    */
		0,   /*  0    0    0   1   0   INSIDE   INSIDE   INSIDE     */
		3,   /*  0    0    0   1   1   OUTSIDE  OUTSIDE  OUTSIDE    */
		3,   /*  0    0    0   1   1   OUTSIDE  OUTSIDE  INSIDE     */
		3,   /*  0    0    0   1   1   OUTSIDE  INSIDE   OUTSIDE    */
		3,   /*  0    0    0   1   1   OUTSIDE  INSIDE   INSIDE     */
		3,   /*  0    0    0   1   1   INSIDE   OUTSIDE  OUTSIDE    */
		3,   /*  0    0    0   1   1   INSIDE   OUTSIDE  INSIDE     */
		3,   /*  0    0    0   1   1   INSIDE   INSIDE   OUTSIDE    */
		3,   /*  0    0    0   1   1   INSIDE   INSIDE   INSIDE     */
		0,   /*  0    0    1   0   0   OUTSIDE  OUTSIDE  OUTSIDE    */
		0,   /*  0    0    1   0   0   OUTSIDE  OUTSIDE  INSIDE     */
		0,   /*  0    0    1   0   0   OUTSIDE  INSIDE   OUTSIDE    */
		0,   /*  0    0    1   0   0   OUTSIDE  INSIDE   INSIDE     */
		0,   /*  0    0    1   0   0   INSIDE   OUTSIDE  OUTSIDE    */
		0,   /*  0    0    1   0   0   INSIDE   OUTSIDE  INSIDE     */
		0,   /*  0    0    1   0   0   INSIDE   INSIDE   OUTSIDE    */
		0,   /*  0    0    1   0   0   INSIDE   INSIDE   INSIDE     */
		3,   /*  0    0    1   0   1   OUTSIDE  OUTSIDE  OUTSIDE    */
		3,   /*  0    0    1   0   1   OUTSIDE  OUTSIDE  INSIDE     */
		3,   /*  0    0    1   0   1   OUTSIDE  INSIDE   OUTSIDE    */
		3,   /*  0    0    1   0   1   OUTSIDE  INSIDE   INSIDE     */
		3,   /*  0    0    1   0   1   INSIDE   OUTSIDE  OUTSIDE    */
		3,   /*  0    0    1   0   1   INSIDE   OUTSIDE  INSIDE     */
		3,   /*  0    0    1   0   1   INSIDE   INSIDE   OUTSIDE    */
		3,   /*  0    0    1   0   1   INSIDE   INSIDE   INSIDE     */
		5,   /*  0    0    1   1   0   OUTSIDE  OUTSIDE  OUTSIDE    */
		5,   /*  0    0    1   1   0   OUTSIDE  OUTSIDE  INSIDE     */
		5,   /*  0    0    1   1   0   OUTSIDE  INSIDE   OUTSIDE    */
		5,   /*  0    0    1   1   0   OUTSIDE  INSIDE   INSIDE     */
		5,   /*  0    0    1   1   0   INSIDE   OUTSIDE  OUTSIDE    */
		5,   /*  0    0    1   1   0   INSIDE   OUTSIDE  INSIDE     */
		5,   /*  0    0    1   1   0   INSIDE   INSIDE   OUTSIDE    */
		5,   /*  0    0    1   1   0   INSIDE   INSIDE   INSIDE     */
		8,   /*  0    0    1   1   1   OUTSIDE  OUTSIDE  OUTSIDE    */
		8,   /*  0    0    1   1   1   OUTSIDE  OUTSIDE  INSIDE     */
		8,   /*  0    0    1   1   1   OUTSIDE  INSIDE   OUTSIDE    */
		8,   /*  0    0    1   1   1   OUTSIDE  INSIDE   INSIDE     */
		8,   /*  0    0    1   1   1   INSIDE   OUTSIDE  OUTSIDE    */
		8,   /*  0    0    1   1   1   INSIDE   OUTSIDE  INSIDE     */
		8,   /*  0    0    1   1   1   INSIDE   INSIDE   OUTSIDE    */
		8,   /*  0    0    1   1   1   INSIDE   INSIDE   INSIDE     */
		0,   /*  0    1    0   0   0   OUTSIDE  OUTSIDE  OUTSIDE    */
		0,   /*  0    1    0   0   0   OUTSIDE  OUTSIDE  INSIDE     */
		0,   /*  0    1    0   0   0   OUTSIDE  INSIDE   OUTSIDE    */
		0,   /*  0    1    0   0   0   OUTSIDE  INSIDE   INSIDE     */
		0,   /*  0    1    0   0   0   INSIDE   OUTSIDE  OUTSIDE    */
		0,   /*  0    1    0   0   0   INSIDE   OUTSIDE  INSIDE     */
		0,   /*  0    1    0   0   0   INSIDE   INSIDE   OUTSIDE    */
		0,   /*  0    1    0   0   0   INSIDE   INSIDE   INSIDE     */
		3,   /*  0    1    0   0   1   OUTSIDE  OUTSIDE  OUTSIDE    */
		3,   /*  0    1    0   0   1   OUTSIDE  OUTSIDE  INSIDE     */
		3,   /*  0    1    0   0   1   OUTSIDE  INSIDE   OUTSIDE    */
		3,   /*  0    1    0   0   1   OUTSIDE  INSIDE   INSIDE     */
		3,   /*  0    1    0   0   1   INSIDE   OUTSIDE  OUTSIDE    */
		3,   /*  0    1    0   0   1   INSIDE   OUTSIDE  INSIDE     */
		3,   /*  0    1    0   0   1   INSIDE   INSIDE   OUTSIDE    */
		3,   /*  0    1    0   0   1   INSIDE   INSIDE   INSIDE     */
		1,   /*  0    1    0   1   0   OUTSIDE  OUTSIDE  OUTSIDE    */
		3,   /*  0    1    0   1   0   OUTSIDE  OUTSIDE  INSIDE     */
		1,   /*  0    1    0   1   0   OUTSIDE  INSIDE   OUTSIDE    */
		3,   /*  0    1    0   1   0   OUTSIDE  INSIDE   INSIDE     */
		1,   /*  0    1    0   1   0   INSIDE   OUTSIDE  OUTSIDE    */
		3,   /*  0    1    0   1   0   INSIDE   OUTSIDE  INSIDE     */
		1,   /*  0    1    0   1   0   INSIDE   INSIDE   OUTSIDE    */
		3,   /*  0    1    0   1   0   INSIDE   INSIDE   INSIDE     */
		1,   /*  0    1    0   1   1   OUTSIDE  OUTSIDE  OUTSIDE    */
		4,   /*  0    1    0   1   1   OUTSIDE  OUTSIDE  INSIDE     */
		1,   /*  0    1    0   1   1   OUTSIDE  INSIDE   OUTSIDE    */
		4,   /*  0    1    0   1   1   OUTSIDE  INSIDE   INSIDE     */
		1,   /*  0    1    0   1   1   INSIDE   OUTSIDE  OUTSIDE    */
		4,   /*  0    1    0   1   1   INSIDE   OUTSIDE  INSIDE     */
		1,   /*  0    1    0   1   1   INSIDE   INSIDE   OUTSIDE    */
		4,   /*  0    1    0   1   1   INSIDE   INSIDE   INSIDE     */
		1,   /*  0    1    1   0   0   OUTSIDE  OUTSIDE  OUTSIDE    */
		3,   /*  0    1    1   0   0   OUTSIDE  OUTSIDE  INSIDE     */
		1,   /*  0    1    1   0   0   OUTSIDE  INSIDE   OUTSIDE    */
		3,   /*  0    1    1   0   0   OUTSIDE  INSIDE   INSIDE     */
		1,   /*  0    1    1   0   0   INSIDE   OUTSIDE  OUTSIDE    */
		3,   /*  0    1    1   0   0   INSIDE   OUTSIDE  INSIDE     */
		1,   /*  0    1    1   0   0   INSIDE   INSIDE   OUTSIDE    */
		3,   /*  0    1    1   0   0   INSIDE   INSIDE   INSIDE     */
		1,   /*  0    1    1   0   1   OUTSIDE  OUTSIDE  OUTSIDE    */
		4,   /*  0    1    1   0   1   OUTSIDE  OUTSIDE  INSIDE     */
		1,   /*  0    1    1   0   1   OUTSIDE  INSIDE   OUTSIDE    */
		4,   /*  0    1    1   0   1   OUTSIDE  INSIDE   INSIDE     */
		1,   /*  0    1    1   0   1   INSIDE   OUTSIDE  OUTSIDE    */
		4,   /*  0    1    1   0   1   INSIDE   OUTSIDE  INSIDE     */
		1,   /*  0    1    1   0   1   INSIDE   INSIDE   OUTSIDE    */
		4,   /*  0    1    1   0   1   INSIDE   INSIDE   INSIDE     */
		1,   /*  0    1    1   1   0   OUTSIDE  OUTSIDE  OUTSIDE    */
		5,   /*  0    1    1   1   0   OUTSIDE  OUTSIDE  INSIDE     */
		1,   /*  0    1    1   1   0   OUTSIDE  INSIDE   OUTSIDE    */
		5,   /*  0    1    1   1   0   OUTSIDE  INSIDE   INSIDE     */
		1,   /*  0    1    1   1   0   INSIDE   OUTSIDE  OUTSIDE    */
		5,   /*  0    1    1   1   0   INSIDE   OUTSIDE  INSIDE     */
		1,   /*  0    1    1   1   0   INSIDE   INSIDE   OUTSIDE    */
		5,   /*  0    1    1   1   0   INSIDE   INSIDE   INSIDE     */
		1,   /*  0    1    1   1   1   OUTSIDE  OUTSIDE  OUTSIDE    */
		8,   /*  0    1    1   1   1   OUTSIDE  OUTSIDE  INSIDE     */
		1,   /*  0    1    1   1   1   OUTSIDE  INSIDE   OUTSIDE    */
		8,   /*  0    1    1   1   1   OUTSIDE  INSIDE   INSIDE     */
		1,   /*  0    1    1   1   1   INSIDE   OUTSIDE  OUTSIDE    */
		8,   /*  0    1    1   1   1   INSIDE   OUTSIDE  INSIDE     */
		1,   /*  0    1    1   1   1   INSIDE   INSIDE   OUTSIDE    */
		8,   /*  0    1    1   1   1   INSIDE   INSIDE   INSIDE     */
		0,   /*  1    0    0   0   0   OUTSIDE  OUTSIDE  OUTSIDE    */
		0,   /*  1    0    0   0   0   OUTSIDE  OUTSIDE  INSIDE     */
		0,   /*  1    0    0   0   0   OUTSIDE  INSIDE   OUTSIDE    */
		0,   /*  1    0    0   0   0   OUTSIDE  INSIDE   INSIDE     */
		0,   /*  1    0    0   0   0   INSIDE   OUTSIDE  OUTSIDE    */
		0,   /*  1    0    0   0   0   INSIDE   OUTSIDE  INSIDE     */
		0,   /*  1    0    0   0   0   INSIDE   INSIDE   OUTSIDE    */
		0,   /*  1    0    0   0   0   INSIDE   INSIDE   INSIDE     */
		3,   /*  1    0    0   0   1   OUTSIDE  OUTSIDE  OUTSIDE    */
		3,   /*  1    0    0   0   1   OUTSIDE  OUTSIDE  INSIDE     */
		3,   /*  1    0    0   0   1   OUTSIDE  INSIDE   OUTSIDE    */
		3,   /*  1    0    0   0   1   OUTSIDE  INSIDE   INSIDE     */
		3,   /*  1    0    0   0   1   INSIDE   OUTSIDE  OUTSIDE    */
		3,   /*  1    0    0   0   1   INSIDE   OUTSIDE  INSIDE     */
		3,   /*  1    0    0   0   1   INSIDE   INSIDE   OUTSIDE    */
		3,   /*  1    0    0   0   1   INSIDE   INSIDE   INSIDE     */
		1,   /*  1    0    0   1   0   OUTSIDE  OUTSIDE  OUTSIDE    */
		3,   /*  1    0    0   1   0   OUTSIDE  OUTSIDE  INSIDE     */
		1,   /*  1    0    0   1   0   OUTSIDE  INSIDE   OUTSIDE    */
		3,   /*  1    0    0   1   0   OUTSIDE  INSIDE   INSIDE     */
		1,   /*  1    0    0   1   0   INSIDE   OUTSIDE  OUTSIDE    */
		3,   /*  1    0    0   1   0   INSIDE   OUTSIDE  INSIDE     */
		1,   /*  1    0    0   1   0   INSIDE   INSIDE   OUTSIDE    */
		3,   /*  1    0    0   1   0   INSIDE   INSIDE   INSIDE     */
		1,   /*  1    0    0   1   1   OUTSIDE  OUTSIDE  OUTSIDE    */
		1,   /*  1    0    0   1   1   OUTSIDE  OUTSIDE  INSIDE     */
		4,   /*  1    0    0   1   1   OUTSIDE  INSIDE   OUTSIDE    */
		4,   /*  1    0    0   1   1   OUTSIDE  INSIDE   INSIDE     */
		1,   /*  1    0    0   1   1   INSIDE   OUTSIDE  OUTSIDE    */
		1,   /*  1    0    0   1   1   INSIDE   OUTSIDE  INSIDE     */
		4,   /*  1    0    0   1   1   INSIDE   INSIDE   OUTSIDE    */
		4,   /*  1    0    0   1   1   INSIDE   INSIDE   INSIDE     */
		1,   /*  1    0    1   0   0   OUTSIDE  OUTSIDE  OUTSIDE    */
		1,   /*  1    0    1   0   0   OUTSIDE  OUTSIDE  INSIDE     */
		3,   /*  1    0    1   0   0   OUTSIDE  INSIDE   OUTSIDE    */
		3,   /*  1    0    1   0   0   OUTSIDE  INSIDE   INSIDE     */
		1,   /*  1    0    1   0   0   INSIDE   OUTSIDE  OUTSIDE    */
		1,   /*  1    0    1   0   0   INSIDE   OUTSIDE  INSIDE     */
		3,   /*  1    0    1   0   0   INSIDE   INSIDE   OUTSIDE    */
		3,   /*  1    0    1   0   0   INSIDE   INSIDE   INSIDE     */
		1,   /*  1    0    1   0   1   OUTSIDE  OUTSIDE  OUTSIDE    */
		1,   /*  1    0    1   0   1   OUTSIDE  OUTSIDE  INSIDE     */
		4,   /*  1    0    1   0   1   OUTSIDE  INSIDE   OUTSIDE    */
		4,   /*  1    0    1   0   1   OUTSIDE  INSIDE   INSIDE     */
		1,   /*  1    0    1   0   1   INSIDE   OUTSIDE  OUTSIDE    */
		1,   /*  1    0    1   0   1   INSIDE   OUTSIDE  INSIDE     */
		4,   /*  1    0    1   0   1   INSIDE   INSIDE   OUTSIDE    */
		4,   /*  1    0    1   0   1   INSIDE   INSIDE   INSIDE     */
		1,   /*  1    0    1   1   0   OUTSIDE  OUTSIDE  OUTSIDE    */
		1,   /*  1    0    1   1   0   OUTSIDE  OUTSIDE  INSIDE     */
		5,   /*  1    0    1   1   0   OUTSIDE  INSIDE   OUTSIDE    */
		5,   /*  1    0    1   1   0   OUTSIDE  INSIDE   INSIDE     */
		1,   /*  1    0    1   1   0   INSIDE   OUTSIDE  OUTSIDE    */
		1,   /*  1    0    1   1   0   INSIDE   OUTSIDE  INSIDE     */
		5,   /*  1    0    1   1   0   INSIDE   INSIDE   OUTSIDE    */
		5,   /*  1    0    1   1   0   INSIDE   INSIDE   INSIDE     */
		1,   /*  1    0    1   1   1   OUTSIDE  OUTSIDE  OUTSIDE    */
		1,   /*  1    0    1   1   1   OUTSIDE  OUTSIDE  INSIDE     */
		8,   /*  1    0    1   1   1   OUTSIDE  INSIDE   OUTSIDE    */
		8,   /*  1    0    1   1   1   OUTSIDE  INSIDE   INSIDE     */
		1,   /*  1    0    1   1   1   INSIDE   OUTSIDE  OUTSIDE    */
		1,   /*  1    0    1   1   1   INSIDE   OUTSIDE  INSIDE     */
		8,   /*  1    0    1   1   1   INSIDE   INSIDE   OUTSIDE    */
		8,   /*  1    0    1   1   1   INSIDE   INSIDE   INSIDE     */
		1,   /*  1    1    0   0   0   OUTSIDE  OUTSIDE  OUTSIDE    */
		1,   /*  1    1    0   0   0   OUTSIDE  OUTSIDE  INSIDE     */
		1,   /*  1    1    0   0   0   OUTSIDE  INSIDE   OUTSIDE    */
		1,   /*  1    1    0   0   0   OUTSIDE  INSIDE   INSIDE     */
		6,   /*  1    1    0   0   0   INSIDE   OUTSIDE  OUTSIDE    */
		6,   /*  1    1    0   0   0   INSIDE   OUTSIDE  INSIDE     */
		6,   /*  1    1    0   0   0   INSIDE   INSIDE   OUTSIDE    */
		6,   /*  1    1    0   0   0   INSIDE   INSIDE   INSIDE     */
		1,   /*  1    1    0   0   1   OUTSIDE  OUTSIDE  OUTSIDE    */
		1,   /*  1    1    0   0   1   OUTSIDE  OUTSIDE  INSIDE     */
		1,   /*  1    1    0   0   1   OUTSIDE  INSIDE   OUTSIDE    */
		1,   /*  1    1    0   0   1   OUTSIDE  INSIDE   INSIDE     */
		7,   /*  1    1    0   0   1   INSIDE   OUTSIDE  OUTSIDE    */
		7,   /*  1    1    0   0   1   INSIDE   OUTSIDE  INSIDE     */
		7,   /*  1    1    0   0   1   INSIDE   INSIDE   OUTSIDE    */
		7,   /*  1    1    0   0   1   INSIDE   INSIDE   INSIDE     */
		2,   /*  1    1    0   1   0   OUTSIDE  OUTSIDE  OUTSIDE    */
		2,   /*  1    1    0   1   0   OUTSIDE  OUTSIDE  INSIDE     */
		2,   /*  1    1    0   1   0   OUTSIDE  INSIDE   OUTSIDE    */
		2,   /*  1    1    0   1   0   OUTSIDE  INSIDE   INSIDE     */
		2,   /*  1    1    0   1   0   INSIDE   OUTSIDE  OUTSIDE    */
		2,   /*  1    1    0   1   0   INSIDE   OUTSIDE  INSIDE     */
		2,   /*  1    1    0   1   0   INSIDE   INSIDE   OUTSIDE    */
		7,   /*  1    1    0   1   0   INSIDE   INSIDE   INSIDE     */
		2,   /*  1    1    0   1   1   OUTSIDE  OUTSIDE  OUTSIDE    */
		2,   /*  1    1    0   1   1   OUTSIDE  OUTSIDE  INSIDE     */
		2,   /*  1    1    0   1   1   OUTSIDE  INSIDE   OUTSIDE    */
		2,   /*  1    1    0   1   1   OUTSIDE  INSIDE   INSIDE     */
		2,   /*  1    1    0   1   1   INSIDE   OUTSIDE  OUTSIDE    */
		2,   /*  1    1    0   1   1   INSIDE   OUTSIDE  INSIDE     */
		2,   /*  1    1    0   1   1   INSIDE   INSIDE   OUTSIDE    */
		9,   /*  1    1    0   1   1   INSIDE   INSIDE   INSIDE     */
		2,   /*  1    1    1   0   0   OUTSIDE  OUTSIDE  OUTSIDE    */
		2,   /*  1    1    1   0   0   OUTSIDE  OUTSIDE  INSIDE     */
		2,   /*  1    1    1   0   0   OUTSIDE  INSIDE   OUTSIDE    */
		2,   /*  1    1    1   0   0   OUTSIDE  INSIDE   INSIDE     */
		2,   /*  1    1    1   0   0   INSIDE   OUTSIDE  OUTSIDE    */
		2,   /*  1    1    1   0   0   INSIDE   OUTSIDE  INSIDE     */
		2,   /*  1    1    1   0   0   INSIDE   INSIDE   OUTSIDE    */
		7,   /*  1    1    1   0   0   INSIDE   INSIDE   INSIDE     */
		2,   /*  1    1    1   0   1   OUTSIDE  OUTSIDE  OUTSIDE    */
		2,   /*  1    1    1   0   1   OUTSIDE  OUTSIDE  INSIDE     */
		2,   /*  1    1    1   0   1   OUTSIDE  INSIDE   OUTSIDE    */
		2,   /*  1    1    1   0   1   OUTSIDE  INSIDE   INSIDE     */
		2,   /*  1    1    1   0   1   INSIDE   OUTSIDE  OUTSIDE    */
		2,   /*  1    1    1   0   1   INSIDE   OUTSIDE  INSIDE     */
		2,   /*  1    1    1   0   1   INSIDE   INSIDE   OUTSIDE    */
		9,   /*  1    1    1   0   1   INSIDE   INSIDE   INSIDE     */
		2,   /*  1    1    1   1   0   OUTSIDE  OUTSIDE  OUTSIDE    */
		2,   /*  1    1    1   1   0   OUTSIDE  OUTSIDE  INSIDE     */
		2,   /*  1    1    1   1   0   OUTSIDE  INSIDE   OUTSIDE    */
		2,   /*  1    1    1   1   0   OUTSIDE  INSIDE   INSIDE     */
		2,   /*  1    1    1   1   0   INSIDE   OUTSIDE  OUTSIDE    */
		2,   /*  1    1    1   1   0   INSIDE   OUTSIDE  INSIDE     */
		2,   /*  1    1    1   1   0   INSIDE   INSIDE   OUTSIDE    */
		7,   /*  1    1    1   1   0   INSIDE   INSIDE   INSIDE     */
		2,   /*  1    1    1   1   1   OUTSIDE  OUTSIDE  OUTSIDE    */
		2,   /*  1    1    1   1   1   OUTSIDE  OUTSIDE  INSIDE     */
		2,   /*  1    1    1   1   1   OUTSIDE  INSIDE   OUTSIDE    */
		2,   /*  1    1    1   1   1   OUTSIDE  INSIDE   INSIDE     */
		2,   /*  1    1    1   1   1   INSIDE   OUTSIDE  OUTSIDE    */
		2,   /*  1    1    1   1   1   INSIDE   OUTSIDE  INSIDE     */
		2,   /*  1    1    1   1   1   INSIDE   INSIDE   OUTSIDE    */
		9    /*  1    1    1   1   1   INSIDE   INSIDE   INSIDE     */
	};


}
