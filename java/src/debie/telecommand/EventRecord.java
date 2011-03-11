package debie.telecommand;

public class EventRecord {
	byte      quality_number;    /* byte  0  XXX: was unsigned char    */
	byte      classification;    /* byte  1  XXX: was unsigned char    */
	public byte      SU_number;         /* byte  2  XXX: was unsigned char    */
	public int       hit_time;          /* byte  3 -  6 XXX: was tm_dpu_time_t */
	public byte      SU_temperature_1;  /* byte  7  XXX: was unsigned char        */
	public byte      SU_temperature_2;  /* byte  8  XXX: was unsigned char        */
	public char      plasma_1_plus;     /* byte  9 - 10 XXX: was tm_ushort_t*/
	public char      plasma_1_minus;    /* byte 11 - 12 XXX: was tm_ushort_t*/
	public char      piezo_1;           /* byte 13 - 14 XXX: was tm_ushort_t*/
	public char      piezo_2;           /* byte 15 - 16 XXX: was tm_ushort_t*/
	public char      plasma_2_plus;     /* byte 17 - 18 XXX: was tm_ushort_t*/
	public byte      rise_time;         /* byte 19 XXX: was unsigned char         */
	public byte      delay_1;            /* byte 20 XXX: was SIGNED char         */
	public char      delay_2;           /* byte 21 - 22 XXX: was tm_ushort_t */
	public char      delay_3;           /* byte 23 - 24 XXX: was tm_ushort_t */
	byte      checksum;          /* byte 25 XXX: was unsigned char */

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

	public void classify() {
		// TODO Auto-generated method stub

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

}
