package org.payment.router.worker;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.payment.router.model.PaymentRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Singleton
public class PaymentSaverWorker {
    @ConfigProperty(name = "batch.size")
    int MAX_BATCH_SIZE;

    private static final int SAVER_THREADS = 2;

    static final Logger log = LoggerFactory.getLogger(PaymentSaverWorker.class);
    static final LinkedBlockingQueue<PaymentRequest> paymentsToSave = new LinkedBlockingQueue<>();

    private volatile boolean running = true;

    @Inject
    Pool client;

    void start(@Observes StartupEvent e) {
        running = true;
        IntStream.range(0, SAVER_THREADS).forEach(i ->
                Executors.newVirtualThreadPerTaskExecutor().submit(() -> {
                    while (running) {
                        try {
                            this.saveBatchLoop();
                        } catch (Exception ex) {
                            log.error("Erro no loop de save", ex);
                        }
                    }
                })
        );
    }

    void onStop(@Observes ShutdownEvent ev) {
        running = false;
        log.info("Encerrando... Drenando {} pagamentos restantes.", paymentsToSave.size());

        while (!paymentsToSave.isEmpty()) {
            saveRemaining();
        }
    }

    public static void enqueuePaymentForSave(PaymentRequest request) {
        paymentsToSave.offer(request);
    }

    void saveBatchLoop() {
        List<PaymentRequest> batch = new ArrayList<>(MAX_BATCH_SIZE);
        try {
            PaymentRequest first = paymentsToSave.poll(1, TimeUnit.SECONDS);
            if (first == null) return;

            batch.add(first);
            paymentsToSave.drainTo(batch, MAX_BATCH_SIZE - 1);

            persistBatch(batch);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    void saveRemaining() {
        List<PaymentRequest> batch = new ArrayList<>(MAX_BATCH_SIZE);
        paymentsToSave.drainTo(batch, MAX_BATCH_SIZE);
        if (!batch.isEmpty()) {
            persistBatch(batch);
        }
    }

    private void persistBatch(List<PaymentRequest> batch) {
        if (batch.isEmpty()) return;

        List<Tuple> tuples = batch.stream()
                .map(p -> Tuple.of(
                        p.correlationId,
                        p.amount,
                        p.requestedAt.atZone(ZoneOffset.UTC).toLocalDateTime(),
                        p.provider
                ))
                .collect(Collectors.toList());

        try {
            // Esperar o banco segura o próximo lote; sem isso os lotes se acumulam na memória (limite de 100MB).
            client.preparedQuery("INSERT INTO payments (correlation_id, amount, requested_at, provider) VALUES ($1, $2, $3, $4) ON CONFLICT DO NOTHING")
                    .executeBatch(tuples)
                    .await().indefinitely();
        } catch (Exception e) {
            log.error("Erro ao salvar batch", e);
        }
    }
}
