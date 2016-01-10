package com.epsm.electricPowerSystemModel.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.epsm.electricPowerSystemModel.model.bothConsumptionAndGeneration.LoadCurve;
import com.epsm.electricPowerSystemModel.model.generation.GeneratorGenerationSchedule;
import com.epsm.electricPowerSystemModel.model.generation.PowerStationGenerationSchedule;
import com.epsm.electricPowerSystemModel.service.IncomingMessageService;
import com.epsm.electricPowerSystemModel.util.UrlRequestSender;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Import(UrlRequestSender.class)
@RunWith(MockitoJUnitRunner.class)
public class PowerStationControllerTest {
	private MockMvc mockMvc;
	private ObjectMapper mapper;
	private String objectInJsonString;
	private Object objectToSerialize;
	private final float[] GENERATION_BY_HOURS = new float[]{
			55.15f,  50.61f,  47.36f,  44.11f, 	41.20f,  41.52f,
			40.87f,  48.66f,  64.89f,  77.86f,  85.00f,  84.34f,
			77.86f,  77.86f,  77.53f,  77.20f,  77.20f,  77.20f,
			77.20f,  77.20f,  77.20f,  77.20f,  77.20f,  77.20f 
	};
	
	@InjectMocks
	private PowerStationController controller;
	
	@Mock
	private IncomingMessageService service;
	
	@Before
	public void initialize(){
		mockMvc = standaloneSetup(controller).build();
		mapper = new ObjectMapper();
		mapper.findAndRegisterModules();
	}
	
	@Test
	public void acceptsPowerStationGenerationSchedule() throws Exception {
		prepareScheduleAsJSONString();
		
		mockMvc.perform(
				post("/api/powerstation/command")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectInJsonString))
				.andExpect(status().isOk());
	}
	
	private void prepareScheduleAsJSONString() throws JsonProcessingException{
		PowerStationGenerationSchedule generationSchedule = 
				new PowerStationGenerationSchedule(1, LocalDateTime.MIN, LocalTime.MIN, 2);
		LoadCurve generationCurve = new LoadCurve(GENERATION_BY_HOURS);
		GeneratorGenerationSchedule genrationSchedule_1 = new GeneratorGenerationSchedule(
				1, true, true, null);
		GeneratorGenerationSchedule genrationSchedule_2 = new GeneratorGenerationSchedule(
				2, true, false, generationCurve);
		generationSchedule.addGeneratorSchedule(genrationSchedule_1);
		generationSchedule.addGeneratorSchedule(genrationSchedule_2);
		
		objectToSerialize = generationSchedule;
		objectInJsonString = mapper.writeValueAsString(objectToSerialize);
	}
}
