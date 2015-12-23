package main.java.com.epsm.electricPowerSystemModel.model.dispatch;

import java.time.LocalTime;
import java.util.Collection;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import main.java.com.epsm.electricPowerSystemModel.model.bothConsumptionAndGeneration.LoadCurve;
import main.java.com.epsm.electricPowerSystemModel.model.generalModel.ElectricPowerSystemSimulation;
import main.java.com.epsm.electricPowerSystemModel.model.generation.Generator;
import main.java.com.epsm.electricPowerSystemModel.model.generation.PowerStation;
import main.java.com.epsm.electricPowerSystemModel.model.generation.PowerStationException;

public class MainControlPanel implements ReportSource, ReportSenderSource{
	private ElectricPowerSystemSimulation simulation;
	private PowerStation station;
	private PowerStationGenerationSchedule curentSchedule;
	private PowerStationGenerationSchedule receivedSchedule;
	private Timer generatorControlTimer;
	private GeneratorsControlTask generatorControlTask = new GeneratorsControlTask();
	private GenerationScheduleValidator validator = new GenerationScheduleValidator();
	private ReportSender sender;
	private PowerStationParameters parameters;
	private PowerStationReport stationReport;
	private Set<GeneratorStateReport> generatorsReports = new TreeSet<GeneratorStateReport>();
	private Logger logger = (Logger) LoggerFactory.getLogger(MainControlPanel.class);
	
	public void performGenerationSchedule(PowerStationGenerationSchedule generationSchedule){
		receivedSchedule = generationSchedule;
		
		getStationParameters();
		
		if(isReceivedScheduleValid()){
			replaceCurrentSchedule();
			executeSchedule();
		}else if(isThereValidSchedule()){
			executeSchedule();
		}
	}
	
	private void getStationParameters(){
		if(parameters == null){
			parameters = station.getPowerStationParameters();
		}
	}
	
	private boolean isReceivedScheduleValid(){
		try{
			validator.validate(receivedSchedule, parameters);
			logger.info("Schedule was received.");
			return true;
		}catch (PowerStationException exception){
			//TODO send request to dispatcher
			logger.warn("Received ", exception);
			return false;
		}
	}
	
	private void replaceCurrentSchedule(){
		curentSchedule = receivedSchedule;
	}
	
	private void executeSchedule(){
		generatorControlTimer = new Timer();
		generatorControlTimer.schedule(generatorControlTask, 0, TimeUnit.SECONDS.toMillis(1));
	}
	
	private boolean isThereValidSchedule(){
		return curentSchedule != null;
	}
	
	@Override
	public void subscribeOnReports(){
		sender.sendReports();
	}
	
	@Override
	public Report getReport() {
		processStateOfEveryGenerator();
		prepareStationStateReport();
		return stationReport;
	}
		
	private void processStateOfEveryGenerator(){
		clearPreviousGeneratorsReports();
		prepareGeneratorsStatesReports();
	}
	
	private void clearPreviousGeneratorsReports(){
		generatorsReports.clear();
	}
	
	private void prepareGeneratorsStatesReports(){
		Collection<Integer> generatorNumbers = station.getGeneratorsNumbers();
		for(Integer generatorNumber: generatorNumbers){
			Generator generator = station.getGenerator(generatorNumber);
			GeneratorStateReport generatorReport = prepareGeneratorStateReport(generator);
			addGeneratorStateReportToGeneratorsStatesReport(generatorReport);
		}
	}
	
	private GeneratorStateReport prepareGeneratorStateReport(Generator generator){
		int generatorNumber = generator.getNumber();
		float generationInWM = generator.getGenerationInMW();
		
		return new GeneratorStateReport(generatorNumber, generationInWM);
	}
	
	private void addGeneratorStateReportToGeneratorsStatesReport(GeneratorStateReport report){
		generatorsReports.add(report);
	}
	
	private void prepareStationStateReport(){
		int stationNumber = station.getNumber();
		LocalTime currentTime = simulation.getTime();
		stationReport = new PowerStationReport(stationNumber, currentTime, generatorsReports);
	}

	public void setSimulation(ElectricPowerSystemSimulation simulation) {
		this.simulation = simulation;
	}

	public void setStation(PowerStation station) {
		this.station = station;
	}
	
	@Override
	public void setReportSender(ReportSender sender) {
		this.sender = sender;
	}
	
	private class GeneratorsControlTask extends TimerTask{
		private Generator generator;
		private LoadCurve generationCurve;
		private boolean shouldGeneratorBeTurnedOn;
		private boolean shouldAstaticRegulationBeTurnedOn;
		
		@Override
		public void run() {
			processEveryGenerationSchedule();
		}
		
		private void processEveryGenerationSchedule(){
			Collection<Integer> generatorNumbers = station.getGeneratorsNumbers();
			for(Integer generatorNumber: generatorNumbers){
				Generator generator = station.getGenerator(generatorNumber);
				rememberCurrentGenerator(generator);
				GeneratorGenerationSchedule generatorSchedule = getGeneratorGenerationSchedule(generator);
				adjustGenerationAccordingToSchedule(generatorSchedule);
			}
		}

		private void rememberCurrentGenerator(Generator generator){
			this.generator = generator;
		}
		
		private GeneratorGenerationSchedule getGeneratorGenerationSchedule(Generator generator){
			int generatorNumber = generator.getNumber();
			return curentSchedule.getGeneratorGenerationSchedule(generatorNumber);
		}
		
		private void adjustGenerationAccordingToSchedule(GeneratorGenerationSchedule generatorSchedule){
			getNewGenerationParameters(generatorSchedule);
			adjustNecessaryParameters();
		}

		private void getNewGenerationParameters(GeneratorGenerationSchedule generatorSchedule){
			shouldGeneratorBeTurnedOn = generatorSchedule.isGeneratorTurnedOn();
			shouldAstaticRegulationBeTurnedOn = generatorSchedule.isAstaticRegulatorTurnedOn();
			generationCurve = generatorSchedule.getCurve();
		}
		
		private void adjustNecessaryParameters() {
			if(shouldGeneratorBeTurnedOn){
				vefifyAndTurnOnGenerator();
				adjustGeneration();
			}else{
				vefifyAndTurnOffGenerator();
			}
		}
		
		private void vefifyAndTurnOnGenerator(){
			if(! generator.isTurnedOn()){
				generator.turnOnGenerator();
			}
		}
		
		private void vefifyAndTurnOffGenerator() {
			if(generator.isTurnedOn()){
				generator.turnOffGenerator();
			}
		}
		
		private void adjustGeneration(){
			if(shouldAstaticRegulationBeTurnedOn){
				vefifyAndTurnOnAstaticRegulation();
			}else{
				vefifyAndTurnOffAstaticRegulation();
				adjustGenerationPower();
			}
		}
		
		private void vefifyAndTurnOnAstaticRegulation(){
			if(! generator.isAstaticRegulationTurnedOn()){
				generator.turnOnAstaticRegulation();
			}
		}
		
		private void vefifyAndTurnOffAstaticRegulation(){
			if(generator.isAstaticRegulationTurnedOn()){
				generator.turnOffAstaticRegulation();
			}
		}
		
		private void adjustGenerationPower(){
			LocalTime currentTime = simulation.getTime();
			float newGenerationPower = generationCurve.getPowerOnTimeInMW(currentTime);
			generator.setPowerAtRequiredFrequency(newGenerationPower);
		}
	}
}