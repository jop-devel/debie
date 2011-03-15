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
 *    Module     :   Based on 'class.c'
 *      
 *- * --------------------------------------------------------------------------
 */
package debie.particles;

import debie.telecommand.TelecommandExecutionTask;

/** Event-classification module. 
 * 
 *  @see code/class.h
 *  @see code/class.c
 */
public class EventClassifier {

	/** Purpose        : Initializes classication coefficients and levels.
	 *  Interface      : inputs      - none.
	 *                   outputs     - quality coefficients in telemetry_data.
	 *                               - classification levels in telemetry_data.
	 *                               - threshold levels in telemetry_data.
	 *                               - min time window in telemetry_data
	 *                               - max time window in telemetry_data
	 *                   subroutines - Init_SU_Settings
	 *  Preconditions  : none.
	 *  Postconditions : outputs have their default values.
	 *  Algorithm      : see below */
	public static void initClassification() {
		TelecommandExecutionTask.getTelemetryData().initCoefficients();

		TelecommandExecutionTask.getTelemetryData().getSensorUnit1().init();
		TelecommandExecutionTask.getTelemetryData().getSensorUnit2().init();
		TelecommandExecutionTask.getTelemetryData().getSensorUnit3().init();
		TelecommandExecutionTask.getTelemetryData().getSensorUnit4().init();
		/* Default values for thresholds, classification levels and min/max times */
		/* related to classification are set here.                                */
	}
}
