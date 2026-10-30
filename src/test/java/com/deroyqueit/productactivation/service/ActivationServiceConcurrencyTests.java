package com.deroyqueit.productactivation.service;

import com.deroyqueit.productactivation.IntegrationTest;
import com.deroyqueit.productactivation.domain.model.Activation;
import com.deroyqueit.productactivation.repository.ActivationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class ActivationServiceConcurrencyTests {

    private static final int PARALLEL_REQUESTS = 10;

    @Autowired
    private ActivationService service;

    @Autowired
    private ActivationRepository repository;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void parallelRequestsWithSameExternalIdCreateExactlyOneRecord() throws Exception {
        List<Activation> results = createInParallel("EXT-CONCURRENT", "CUST-1", "INTERNET-500");

        UUID createdId = results.getFirst().id();
        assertThat(repository.count()).isEqualTo(1);
        assertThat(results).allSatisfy(result -> assertThat(result.id()).isEqualTo(createdId));
    }

    private List<Activation> createInParallel(String externalId, String customerId, String productCode)
            throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(PARALLEL_REQUESTS)) {
            List<Future<Activation>> futures = IntStream.range(0, PARALLEL_REQUESTS)
                    .mapToObj(i -> executor.submit(() -> {
                        start.await();
                        return service.create("client-1", externalId, customerId, productCode);
                    }))
                    .toList();

            start.countDown();

            List<Activation> results = new ArrayList<>();
            for (Future<Activation> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        }
    }
}
