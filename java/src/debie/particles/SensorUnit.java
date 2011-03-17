package debie.particles;


public class SensorUnit {
	
	public enum SensorUnitState {
		   off_e,               /* SU off state - power is Off.                  */
		   start_switching_e,   /* Transition to On state is starting.           */
		   switching_e,         /* Transition to On state is started.            */
		   on_e,                /* SU on state - power is On.                    */
		   self_test_mon_e,     /* Selt Test, Voltage and Temperature monitoring */
		   self_test_e,         /* Selt Test, test pulse setup.                  */
		   self_test_trigger_e, /* Self test, test pulse handling                */
		   acquisition_e        /* Power is On and Hit Events are accepted.      */
	}

	public int /* sensor_number_t */ number;        /* Sensor Unit number                   */
	public SensorUnitState state;              /* Sensor unit states can be either On  */
	                                     /* or Off.                              */
	public SensorUnitState expected_source_state; /* Excpected source state of the SU     */
	                                     /* state transition.                    */
	public /* unsigned char */ int execution_result;   /* This variable is used to indicate    */
	                                     /* execution results.                   */
}

