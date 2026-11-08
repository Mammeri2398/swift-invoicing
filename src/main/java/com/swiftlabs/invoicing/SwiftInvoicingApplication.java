package com.swiftlabs.invoicing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import java.time.ZoneId;
import java.util.TimeZone;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SwiftInvoicingApplication {

	/** Algeria's time, whatever the server's own (a Docker image runs in UTC): see app.time-zone. */
	static final String DEFAULT_TIME_ZONE = "Africa/Algiers";

	public static void main(String[] args) {
		// Before anything reads the time: dates and invoice years follow the business's day, not the server's
		String zone = System.getenv().getOrDefault("APP_TIME_ZONE", DEFAULT_TIME_ZONE);
		TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of(zone)));
		SpringApplication.run(SwiftInvoicingApplication.class, args);
	}

}
