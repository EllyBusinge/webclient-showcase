package gr.kmandalas.service.otp;

import gr.kmandalas.service.otp.dto.CustomerDTO;
import gr.kmandalas.service.otp.dto.NotificationResultDTO;
import gr.kmandalas.service.otp.dto.SendForm;
import gr.kmandalas.service.otp.util.PostgresContainer;
import io.specto.hoverfly.junit.core.Hoverfly;
import io.specto.hoverfly.junit.core.HoverflyConfig;

import org.jetbrains.annotations.NotNull;
import org.junit.ClassRule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import reactor.core.publisher.Mono;

import static io.specto.hoverfly.junit.core.HoverflyMode.SIMULATE;
import static io.specto.hoverfly.junit.core.SimulationSource.dsl;
import static io.specto.hoverfly.junit.dsl.HoverflyDsl.service;
import static io.specto.hoverfly.junit.dsl.HttpBodyConverter.*;
import static io.specto.hoverfly.junit.dsl.ResponseCreators.success;

@ContextConfiguration(initializers = { OTPControllerIntegrationTests.PostgresContainerInitializer.class })
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class OTPControllerIntegrationTests {

	@Autowired
	private WebTestClient webTestClient;

	@ClassRule
	public static PostgreSQLContainer<?> postgresSQLContainer = PostgresContainer.getInstance();

	protected static class PostgresContainerInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
		public void initialize(@NotNull ConfigurableApplicationContext configurableApplicationContext) {
			postgresSQLContainer.start();
		}
	}

	private Hoverfly hoverfly;

	@BeforeEach
	void setUp() {
		var simulation = dsl(service("http://customer-service").get("customers?number=1234567891")
						.willReturn(success()
								.body(json(CustomerDTO.builder()
										.firstName("John")
										.lastName("Papadopoulos")
										.accountId(Long.MIN_VALUE)
										.email("john.papadopoulos@mail.com")
										.build()))),
				service("http://localhost:8006")
						.get("/number-information")
						.willReturn(success()
								.body("Valid")),
				service("http://localhost:8005")
						.get("/notifications")
						.willReturn(success()
								.body(json(NotificationResultDTO.builder()
										.status("OK")
										.message("A message")
										.build()))));

		var localConfig = HoverflyConfig.localConfigs().disableTlsVerification().proxyLocalHost().proxyPort(7999);
		hoverfly = new Hoverfly(localConfig, SIMULATE);
		hoverfly.start();
		hoverfly.simulate(simulation);
	}

	@AfterEach
	void tearDown() {
		hoverfly.close();
	}

	//@ClassRule
	//public static HoverflyRule hoverflyRule = HoverflyRule.inSimulationMode(
	//		dsl(service("customer-service").get("customers?number=1234567891")
	//				.willReturn(success()
	//						.body(json(CustomerDTO.builder()
	//								.firstName("John")
	//								.lastName("Papadopoulos")
	//								.accountId(Long.MIN_VALUE)
	//								.email("john.papadopoulos@mail.com")
	//								.build()))),
	//				service("http://localhost:8006")
	//						.get("/number-information")
	//						.willReturn(success()
	//								.body("Valid")),
	//				service("http://localhost:8005")
	//						.get("/notifications")
	//						.willReturn(success()
	//								.body(json(NotificationResultDTO.builder()
	//										.status("OK")
	//										.message("A message")
	//										.build())))),
	//		HoverflyConfig.localConfigs().proxyLocalHost().proxyPort(7999)).printSimulationData();

	@Test
	void contextLoads() {
	}

	@Test
	void testSend_success() throws Exception {

		SendForm requestForm = new SendForm();
		requestForm.setMsisdn("1234567891");
		webTestClient.post()
				.uri("/v1/otp")
				.body(Mono.just(requestForm), SendForm.class)
				.exchange()
				.expectStatus()
				.is2xxSuccessful();
	}

}