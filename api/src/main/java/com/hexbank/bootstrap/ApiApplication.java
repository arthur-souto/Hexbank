package com.hexbank.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Composition root. Fica em {@code bootstrap}, então o scan precisa apontar explicitamente para
 * {@code com.hexbank}: sem isso só beans/entidades/repositórios dentro de {@code bootstrap} seriam encontrados.
 * <ul>
 *   <li>{@code scanBasePackages}: @Component/@Configuration de todos os módulos.</li>
 *   <li>{@code @AutoConfigurationPackage}: @Entity e repositórios Spring Data de todos os módulos (Fase 1+).</li>
 * </ul>
 * Testes {@code @SpringBootTest} devem declarar {@code classes = ApiApplication.class}.
 */
@SpringBootApplication(scanBasePackages = "com.hexbank")
@AutoConfigurationPackage(basePackages = "com.hexbank")
public class ApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(ApiApplication.class, args);
	}

}
