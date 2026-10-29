package com.deroyqueit.productactivation.domain.model;

import com.deroyqueit.productactivation.domain.exception.InvalidStatusTransitionException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActivationTest {

    @Test
    void newActivationIsPendingAndDueImmediately() {
        Instant before = Instant.now();

        Activation activation = newActivation();

        assertThat(activation.status()).isEqualTo(ActivationStatus.PENDING);
        assertThat(activation.nextAttemptAt()).isBetween(before, Instant.now());
        assertThat(activation.version()).isNull();
    }

    @Test
    void startProcessingIsNoLongerScheduled() {
        Activation processing = newActivation().startProcessing();

        assertThat(processing.status()).isEqualTo(ActivationStatus.PROCESSING);
        assertThat(processing.nextAttemptAt()).isNull();
    }

    @Test
    void completesAfterProcessing() {
        Activation completed = newActivation().startProcessing().complete();

        assertThat(completed.status()).isEqualTo(ActivationStatus.COMPLETED);
    }

    @Test
    void retryLaterReturnsToPendingWithNextAttempt() {
        Duration delay = Duration.ofSeconds(30);
        Instant before = Instant.now();

        Activation retrying = newActivation().startProcessing().retryLater(delay);

        assertThat(retrying.status()).isEqualTo(ActivationStatus.PENDING);
        assertThat(retrying.nextAttemptAt()).isBetween(before.plus(delay), Instant.now().plus(delay));
    }

    @Test
    void cannotCompleteWithoutProcessing() {
        Activation pending = newActivation();

        assertThatThrownBy(() -> pending.complete())
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void completedActivationCannotBeProcessedAgain() {
        Activation completed = newActivation().startProcessing().complete();

        assertThatThrownBy(() -> completed.startProcessing())
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void failedActivationIsFinal() {
        Activation failed = newActivation().startProcessing().fail();

        assertThatThrownBy(() -> failed.retryLater(Duration.ofSeconds(10)))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void comparesContentByCustomerAndProduct() {
        Activation activation = newActivation();

        assertThat(activation.hasSameContent("CUST-1", "INTERNET-500")).isTrue();
        assertThat(activation.hasSameContent("CUST-1", "TV-100")).isFalse();
    }

    private static Activation newActivation() {
        return Activation.create("client-1", "EXT-1", "CUST-1", "INTERNET-500");
    }
}
