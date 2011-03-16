package debie.harness;

import static debie.harness.Harness.*;
import static debie.support.KernelObjects.*;
import debie.health.HealthMonitoringTask;
import debie.particles.AcquisitionTask;
import debie.target.TcTmDev;
import debie.telecommand.TelecommandExecutionTask;

/** The simulated DEBIE System */
public class HarnessSystem {
	
	AcquisitionTask acqTask;
	HealthMonitoringTask hmTask;
	TelecommandExecutionTask tctmTask;

	HarnessMailbox acqMailbox;
	HarnessMailbox tctmMailbox;
	
	AdcSim adcSim;
	SensorUnitSim suSim;
	TcTmSim tctmSim;
	
	public HarnessSystem() {
		this.tctmSim = new TcTmSim();
		this.suSim = new SensorUnitSim(this);
		this.adcSim = new AdcSim();

		this.acqMailbox = new HarnessMailbox(ACQUISITION_MAILBOX);
		this.tctmMailbox = new HarnessMailbox(TCTM_MAILBOX);		

		this.hmTask = new HealthMonitoringTask();
		this.acqTask = new AcquisitionTask(hmTask);
		this.tctmTask = new TelecommandExecutionTask(tctmMailbox, tctmSim, hmTask.getInternalTime());
	}
	

}
