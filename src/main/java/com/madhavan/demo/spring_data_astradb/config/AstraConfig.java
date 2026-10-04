package com.madhavan.demo.spring_data_astradb.config;

import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.cassandra.autoconfigure.CqlSessionBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AstraConfig {

	@Bean
	public CqlSessionBuilderCustomizer astraSecureBundleCustomizer(
			@Value("${astra.secure-connect-bundle}") String secureConnectBundle) {
		if (secureConnectBundle.isBlank()) {
			throw new IllegalStateException("ASTRA_DB_SECURE_BUNDLE_PATH must point to the Astra secure connect bundle zip");
		}
		Path bundle = Path.of(secureConnectBundle);
		return builder -> builder.withCloudSecureConnectBundle(bundle);
	}

}
