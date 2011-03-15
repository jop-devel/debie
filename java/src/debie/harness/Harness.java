package debie.harness;

import debie.health.HealthMonitoringTask;
import debie.particles.AcquisitionTask;
import debie.telecommand.TelecommandExecutionTask;

public class Harness {
	
	/*   Test scenarios    */

	/*
	These test cases are not intended as a full functional test.
	They are intended to provide sufficient coverage for measurement-
	based WCET analysis of the tested code. Built-in checks of test
	execution (program state) are used only to check that the DEBIE
	software has done the expected thing in each test case, but the
	checks are definitely not exhaustive.

	Each test cases contributes measurements towards one of the
	"analysis problems" defined for this benchmark. The macro
	FOR_PROBLEM(P) is defined in problems.h and marks the start of
	a test case to be included in problem P. Here P is an integer
	number that identifies the analysis problem as follows:

	P    Problem
	--   -------
	10   1 : TC interrupt handler
	21   2a: TM interrupt handler, most common case
	22   2b: TM interrupt handler, send internal time register
	23   2c: TM interrupt handler, end of TM
	31   3a: Hit Trigger interrupt handler, no ADC errors
	32   3b: Hit Trigger interrupt handler, at most one ADC error
	33   3c: Hit Trigger interrupt handler, any number of ADC errors
	41   4a: TC Execution task, general case
	42   4b: TC Execution task, start TC buffering, MSB
	43   4c: TC Execution task, start TC buffering, LSB
	44   4d: TC Execution task, buffer TC word
	51   5a: Acquisition task, science data not full
	52   5b: Acquisition task, science data full
	61   6a: Monitoring task, no hits or errors
	62   6b: Monitoring task, one hit during A/D conversion
	63   6c: Monitoring task, many hits during A/D conversion
	64   6d: Monitoring task, some SU error detected
	65   6e: Monitoring task, any number of hits and errors

	The macro END_PROBLEM is also defined in problems.h and marks
	the end of execution of a case belonging to the problem named
	by the most recently executed FOR_PROBLEM. Note that more cases
	for the same problem can be added later with another FOR_PROBLEM.
	*/

	public static final int Prob1 = 10;

	public static final int Prob2a = 21;
	public static final int Prob2b = 22;
	public static final int Prob2c = 23;

	public static final int Prob3a = 31;
	public static final int Prob3b = 32;
	public static final int Prob3c = 33;

	public static final int Prob4a = 41;
	public static final int Prob4b = 42;
	public static final int Prob4c = 43;
	public static final int Prob4d = 44;

	public static final int Prob5a = 51;
	public static final int Prob5b = 52;

	public static final int Prob6a = 61;
	public static final int Prob6b = 62;
	public static final int Prob6c = 63;
	public static final int Prob6d = 64;
	public static final int Prob6e = 65;


	/** Test harness */
	public static void main(String[] argv) {
		HarnessSystem system = new HarnessSystem();
		TestLogger defaultLogger = new TestLogger();
		
		AcquisitionTest acqTest = new AcquisitionTest(system, defaultLogger);
		acqTest.runTests();
	}
	
	/*   Tracing */
	public static final boolean TRACE = true;

	public static void trace(String msg) {
		System.out.println(msg);
	}
	
	/* Instrumentation */
	public final static boolean INSTRUMENTATION = false;	

	public static void START_PROBLEM(int probCode) {
		// TODO Auto-generated method stub		
	}

	public static void END_PROBLEM(int probCode) {
		// TODO Auto-generated method stub
		
	}

}
