package com.hexbank;

import com.hexbank.bootstrap.ApiApplication;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = ApiApplication.class)
@ActiveProfiles("test")
@Import(TestContainersConfiguration.class)
class ApiApplicationTests {

	@Test
	void contextLoads() {
	}

}
