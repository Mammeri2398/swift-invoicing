package com.swiftlabs.invoicing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SwiftInvoicingApplication {

	public static void main(String[] args) {
		SpringApplication.run(SwiftInvoicingApplication.class, args);
	}

}
