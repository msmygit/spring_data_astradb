package com.madhavan.demo.spring_data_astradb;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ASTRA_DB_APPLICATION_TOKEN", matches = ".+")
class SpringDataAstradbApplicationTests {

	@Test
	void contextLoads() {
	}

}
