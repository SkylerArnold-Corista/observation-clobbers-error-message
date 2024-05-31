package com.example.observationclobberserrormessage;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskExecutor;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.config.EnableIntegrationManagement;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.MessageChannelSpec;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.messaging.support.ErrorMessage;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.UUID;

@SpringBootApplication
@EnableIntegrationManagement(observationPatterns = {
//        "!errorChannel", // The best (only) workaround I've found so far
        "*"
})
@Slf4j
public class ObservationClobbersErrorMessageApplication {

    public static void main(String[] args) {
        SpringApplication.run(ObservationClobbersErrorMessageApplication.class, args);
    }

    @Bean
    ObservedAspect observedAspect(ObservationRegistry observationRegistry) {
        return new ObservedAspect(observationRegistry);
    }

    @Bean(name = "errorChannel")
    public MessageChannelSpec<?, ?> errorChannel() {
        return MessageChannels.direct();
    }

    /**
     * When observation is enabled, this never gets called
     */
    @ServiceActivator(inputChannel = "errorChannel")
    public void handleError(ErrorMessage errorMessage) {
        log.error("Error from: {}", errorMessage.getOriginalMessage().getPayload());
    }

    @Bean
    public TaskExecutor exampleTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.initialize();
        return executor;
    }

    @Bean
    public IntegrationFlow integrationFlow() {
        return IntegrationFlow.fromSupplier(() -> new GenericMessage<>(UUID.randomUUID().toString()), c -> c.poller(p -> p
                        .fixedDelay(3000)
                        .errorChannel("errorChannel")
                        .taskExecutor(exampleTaskExecutor())
                ))
                .handle(m -> {
                    log.info("Throwing exception for {}", m.getPayload());
                    throw new RuntimeException(m.getPayload().toString());
                })
                .get();
    }
}
