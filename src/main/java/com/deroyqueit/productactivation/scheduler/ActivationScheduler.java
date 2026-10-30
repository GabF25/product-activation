package com.deroyqueit.productactivation.scheduler;

import com.deroyqueit.productactivation.service.ActivationProcessingService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "activation.processing.enabled", havingValue = "true", matchIfMissing = true)
public class ActivationScheduler {

    private final ActivationProcessingService processingService;

    public ActivationScheduler(ActivationProcessingService processingService) {
        this.processingService = processingService;
    }

    @Scheduled(fixedDelayString = "${activation.processing.poll-interval}")
    public void processPendingActivations() {
        processingService.processPendingActivations();
    }

    @Scheduled(fixedDelayString = "${activation.processing.recovery-interval}")
    public void recoverStuckActivations() {
        processingService.recoverStuckActivations();
    }
}
