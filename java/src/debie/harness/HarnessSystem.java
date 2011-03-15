package debie.harness;

import static debie.harness.Harness.*;
import static debie.support.KernelObjects.*;
import debie.health.HealthMonitoringTask;
import debie.particles.AcquisitionTask;
import debie.target.TcTm;
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
	
	public HarnessSystem(AcquisitionTask acqTask, 
						 HealthMonitoringTask hmTask,
						 TelecommandExecutionTask tctmTask) {
		this.acqTask = acqTask;
		this.hmTask = hmTask;
		this.tctmTask = tctmTask;

		this.acqMailbox = new HarnessMailbox(ACQUISITION_MAILBOX);
		this.tctmMailbox = new HarnessMailbox(TCTM_MAILBOX);
		
		this.tctmSim = new TcTmSim();
		this.suSim = new SensorUnitSim(this);
		this.adcSim = new AdcSim();
	}
	

}
