package com.example.booking_service.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

	public static final String BOOKING_EVENTS_TOPIC = "booking-events";

	@Bean
	public NewTopic bookingEventsTopic() {
		return TopicBuilder.name(BOOKING_EVENTS_TOPIC)
				.partitions(3)
				.replicas(1)
				.build();
	}
}
