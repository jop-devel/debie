package debie.harness;

/* JOP specific instrumentation */
/*
 * import com.jopdesign.sys.Const;
 * import com.jopdesign.sys.Native;
 */

import com.jopdesign.sys.Const;
import com.jopdesign.sys.Native;

import debie.health.HealthMonitoringTask;

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
	public static final int ProbFirst = 10;
	public static final int ProbLast  = 65;

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
		
		/* JOP specific instrumentation code */
		if(INSTRUMENTATION) initInstrumentation();

		HarnessSystem system = new HarnessSystem();
		TestLogger defaultLogger = new TestLogger();

		HealthMonitoringTask healthMonitor = new HealthMonitoringTask(system);
		healthMonitor.boot();

		healthMonitor.initHealthMonitoring();

		TelecommandISRTest tcISRTest = new TelecommandISRTest(system, defaultLogger);
		TelecommandTaskTest tcTaskTest = new TelecommandTaskTest(system, defaultLogger);
		MonitoringTaskTest monTaskTest = new MonitoringTaskTest(system, defaultLogger);
		TelemetryTest tmTest = new TelemetryTest(system, defaultLogger);
		HitISRTest hitTest = new HitISRTest(system, defaultLogger);
		AcquisitionTest acqTest = new AcquisitionTest(system, defaultLogger);
		SensorUnitSelfTest suSelfTest = new SensorUnitSelfTest(system, defaultLogger);

		for (int i = 0; i < 10; i++) {
			tcISRTest.runTests();
			tcTaskTest.runTests();
			monTaskTest.runTests();
			tmTest.runTests();
			hitTest.runTests();
			acqTest.runTests();
			suSelfTest.runTests();
		}
		
		/* dump results from instrumentation */
		if(INSTRUMENTATION) printInstrumentation();
	}
	
	/*   Tracing */
	public static final boolean TRACE = false;

	public static void trace(String msg) {
		System.out.println(msg);
	}
	
	/* Instrumentation */
	public final static boolean INSTRUMENTATION = false;	

	private static int ts, te, to;

	public static void startProblem(int probCode) {
		if(INSTRUMENTATION) ts = Native.rdMem(Const.IO_CNT);
	}

	public static void endProblem(int probCode) {
		if(INSTRUMENTATION) {
			/* JOP Specific instrumentation */
			te = Native.rdMem(Const.IO_CNT);
			
			problemStats[probCode-ProbFirst].recordRun(te-ts-to);
		}
	}

	private static class MeasurementStatistic {
		int minElapsed, maxElapsed, totalRuns;
		long totalElapsed;
		public MeasurementStatistic() {
			minElapsed = Integer.MAX_VALUE;
			maxElapsed = 0;
			totalElapsed = 0;
			totalRuns = 0;
		}
		public void recordRun(int elapsed) {
			totalRuns++;
			totalElapsed += elapsed;
			if(minElapsed > elapsed) minElapsed = elapsed;
			if(maxElapsed < elapsed) maxElapsed = elapsed;
		}
		public String toString() {
			return "min:\t"+minElapsed+"\tmax:\t"+maxElapsed
				+"\ttotal:\t"+totalElapsed+"/"+totalRuns;
		}
	}

	private static MeasurementStatistic[] problemStats;
	
	private static void initInstrumentation() {
		/* initialize statistics */
		problemStats = new MeasurementStatistic[ProbLast-ProbFirst+1];
		for(int i = 0; i < problemStats.length; i++) {
			problemStats[i] = new MeasurementStatistic();
		} 
		/* JOP Specific instrumentation */
		ts = Native.rdMem(Const.IO_CNT);
		te = Native.rdMem(Const.IO_CNT);
		to = te-ts;
	}
	
	private static void printInstrumentation() {
		for (int i = 0; i < problemStats.length; i++) {
			if (problemStats[i].totalRuns > 0) {
				System.out.println("Problem "+(ProbFirst+i)+":\t"+problemStats[i]);
			}
		}
	}

}
