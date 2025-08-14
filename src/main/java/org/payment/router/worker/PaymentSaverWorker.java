package org.payment.router.worker;

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
import java.util.stream.Collectors;

@Singleton
public class PaymentSaverWorker {
    @ConfigProperty(name = "batch.size")
    int MAX_BATCH_SIZE;

    static final Logger log = LoggerFactory.getLogger(PaymentSaverWorker.class);
    static final LinkedBlockingQueue<PaymentRequest> paymentsToSave = new LinkedBlockingQueue<>();

    @Inject
    Pool client;

    void start(@Observes StartupEvent e) {
        Executors.newVirtualThreadPerTaskExecutor().submit(() -> {
            try {
                for(;;) {
                    this.saveBatch();
                }
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    public static void enqueuePaymentForSave(PaymentRequest request) {
        paymentsToSave.offer(request);
    }

    void saveBatch() throws InterruptedException {
        Thread.sleep(50);

        List<PaymentRequest> batch = new ArrayList<>();

        paymentsToSave.drainTo(batch, MAX_BATCH_SIZE);

        if (!batch.isEmpty()) {
            log.info("Salvando lote com {} pagamentos.", batch.size());

            List<Tuple> tuples = batch.stream()
                    .map(p -> Tuple.of(
                            p.correlationId,
                            p.amount,
                            p.requestedAt.atZone(ZoneOffset.UTC).toLocalDateTime(),
                            p.provider
                    ))
                    .collect(Collectors.toList());

            client.preparedQuery("INSERT INTO payments (correlation_id, amount, requested_at, provider) VALUES ($1, $2, $3, $4)")
                    .executeBatch(tuples)
                    .subscribe().with(
                            res -> log.info("Batch salvo com sucesso"),
                            err -> log.error("Erro ao salvar batch", err)
                    );

       }
    }
}