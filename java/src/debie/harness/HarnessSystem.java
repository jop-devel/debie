package debie.harness;

import static debie.harness.Harness.*;
import static debie.support.KernelObjects.*;
import debie.health.HealthMonitoringTask;
import debie.particles.AcquisitionTask;
import debie.support.Mailbox;
import debie.support.TaskControl;
import debie.target.TcTmDev;
import debie.telecommand.TelecommandExecutionTask;

/** The simulated DEBIE System */
public class HarnessSystem {
	
	AcquisitionTask acqTask;
	HealthMonitoringTask hmTask;
	TelecommandExecutionTask tctmTask;

	HarnessMailbox acqMailbox;
	HarnessMailbox tctmMailbox;

	public AdcSim adcSim;
	public SensorUnitSim suSim;
	TcTmSim tctmSim;
	
	public HarnessSystem() {
		this.tctmSim = new TcTmSim();
		this.suSim = new SensorUnitSim(this);
		this.adcSim = new AdcSim();

		this.acqMailbox = new HarnessMailbox(ACQUISITION_MAILBOX);
		TaskControl.setMailbox(ACQUISITION_MAILBOX, acqMailbox);
		this.tctmMailbox = new HarnessMailbox(TCTM_MAILBOX);
		TaskControl.setMailbox(TCTM_MAILBOX, tctmMailbox);

		this.hmTask = new HealthMonitoringTask(this);
		this.acqTask = new AcquisitionTask(hmTask);
		this.tctmTask = new TelecommandExecutionTask(TaskControl.getMailbox(TCTM_MAILBOX), tctmSim, hmTask.getInternalTime());
	}
	

}
