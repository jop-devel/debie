package debie.harness;

import static debie.harness.Harness.*;
import static debie.support.KernelObjects.*;
import debie.health.HealthMonitoringTask;
import debie.particles.AcquisitionTask;
import debie.support.DebieSystem;
import debie.support.Mailbox;
import debie.support.TaskControl;
import debie.support.Dpu.Time;
import debie.target.AdConverter;
import debie.target.SensorUnitDev;
import debie.target.TcTmDev;
import debie.telecommand.TelecommandExecutionTask;

/** The simulated DEBIE System */
public class HarnessSystem implements DebieSystem {
	
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
		this.acqTask = new AcquisitionTask(this);
		this.tctmTask = new TelecommandExecutionTask(this);
	}

	@Override
	public HarnessMailbox getAcqMailbox() {
		return acqMailbox;
	}

	@Override
	public AcquisitionTask getAcquisitionTask() {
		return acqTask;
	}

	@Override
	public AdConverter getAdcDevice() {
		return adcSim;
	}

	@Override
	public HealthMonitoringTask getHealthMonitoringTask() {
		return hmTask;
	}

	@Override
	public SensorUnitDev getSensorUnitDevice() {
		return suSim;
	}

	@Override
	public TcTmDev getTcTmDevice() {
		return tctmSim;
	}

	@Override
	public HarnessMailbox getTcTmMailbox() {
		return tctmMailbox;
	}

	@Override
	public TelecommandExecutionTask getTelecommandExecutionTask() {
		return tctmTask;
	}

	@Override
	public Time getInternalTime() {
		return hmTask.getInternalTime();
	}
	

}
