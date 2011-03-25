package debie.harness;

import static debie.support.KernelObjects.*;
import debie.health.HealthMonitoringTask;
import debie.particles.AcquisitionTask;
import debie.particles.HitTriggerTask;
import debie.support.DebieSystem;
import debie.support.Dpu;
import debie.support.TaskControl;
import debie.support.Dpu.Time;
import debie.target.AdConverter;
import debie.target.HwIf;
import debie.target.SensorUnitDev;
import debie.target.TcTmDev;
import debie.telecommand.TelecommandExecutionTask;
import debie.telecommand.TelemetryData;

/** The simulated DEBIE System */
public class HarnessSystem implements DebieSystem {
	TaskControlSim taskControl;

	TelemetryData tmData;
	
	AcquisitionTask acqTask;
	HealthMonitoringTask hmTask;
	TelecommandExecutionTask tctmTask;
	HitTriggerTask htTask;

	HarnessMailbox acqMailbox;
	HarnessMailbox tctmMailbox;

	AdcSim adcSim;
	SensorUnitSim suSim;
	TcTmSim tctmSim;
	
	public HarnessSystem() {
		HwIf.setSystem(this);
		Dpu.setSystem(this);
		
		this.taskControl = new TaskControlSim(this);
		
		this.tmData = new TelemetryData(this);
		
		this.tctmSim = new TcTmSim();
		this.suSim = new SensorUnitSim(this);
		this.adcSim = new AdcSim();

		this.acqMailbox = new HarnessMailbox(ACQUISITION_MAILBOX, this);
		this.tctmMailbox = new HarnessMailbox(TCTM_MAILBOX, this);

		this.acqTask = new AcquisitionTask(this);
		this.tctmTask = new TelecommandExecutionTask(this);
		this.hmTask = new HealthMonitoringTask(this);
		this.htTask = new HitTriggerTask(this);
	}

	@Override
	public TaskControl getTaskControl() {
		return taskControl;
	}

	@Override
	public TelemetryData getTelemetryData() {
		return tmData;
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
	public HitTriggerTask getHitTriggerTask() {
		return htTask;
	}
	
	@Override
	public Time getInternalTime() {
		return hmTask.getInternalTime();
	}
	

}
