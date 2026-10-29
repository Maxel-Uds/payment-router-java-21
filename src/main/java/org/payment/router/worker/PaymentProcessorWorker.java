package org.payment.router.worker;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.payment.router.client.PaymentProcessorDefaultAsyncClient;
import org.payment.router.client.PaymentProcessorFallbackAsyncClient;
import org.payment.router.model.PaymentRequest;
import org.payment.router.worker.ProcessorRouter.Route;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.IntStream;

@Singleton
public class PaymentProcessorWorker {
    private static final long WAIT_MS = 50;

    @ConfigProperty(name = "max.workers.process")
    int MAX_WORKERS_TO_PROCESS;

    @ConfigProperty(name = "max.process.rate")
    int MAX_PROCESS_RATE;

    @Inject
    @RestClient
    PaymentProcessorDefaultAsyncClient paymentProcessorDefaultAsyncClient;

    @Inject
    @RestClient
    PaymentProcessorFallbackAsyncClient paymentProcessorFallbackAsyncClient;

    @Inject
    ProcessorRouter router;

    static final LinkedBlockingQueue<PaymentRequest> paymentsToProcess = new LinkedBlockingQueue<>();

    private final AtomicLong nextSendAt = new AtomicLong(Long.MIN_VALUE);

    public void startProcessWorker(@Observes StartupEvent ev) {
        IntStream.range(0, MAX_WORKERS_TO_PROCESS).forEach(__-> Executors.newVirtualThreadPerTaskExecutor().submit(() -> {
                    try {
                        for(;;) {
                            this.processPayment(this.getNextPaymentToProcess());
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                })
        );
    }

    private PaymentRequest getNextPaymentToProcess() throws InterruptedException {
        return paymentsToProcess.take();
    }

    public void processPayment(PaymentRequest request) throws InterruptedException {
        request.markRequestedAtIfAbsent();
        for(;;) {
            Route route = router.next();
            if (route != Route.WAIT) {
                waitForSendSlot();
            }
            switch (route) {
                case DEFAULT -> {
                    if (processOnDefault(request)) return;
                }
                case DEFAULT_PROBE -> {
                    try {
                        if (processOnDefault(request)) return;
                    } finally {
                        router.probeFinished();
                    }
                }
                case FALLBACK -> {
                    if (processOnFallback(request)) return;
                }
                case WAIT -> Thread.sleep(WAIT_MS);
            }
        }
    }

    // Drenar o backlog sem limite estoura a cota de 0.5 CPU e atrasa quem está chegando no POST /payments.
    private void waitForSendSlot() throws InterruptedException {
        long interval = TimeUnit.SECONDS.toNanos(1) / MAX_PROCESS_RATE;
        long now = System.nanoTime();
        long slot = Math.max(nextSendAt.getAndAccumulate(now, (next, n) -> Math.max(next, n) + interval), now);
        if (slot > now) {
            Thread.sleep(Duration.ofNanos(slot - now));
        }
    }

    private boolean processOnDefault(PaymentRequest request) {
        long start = System.nanoTime();
        if (!send(paymentProcessorDefaultAsyncClient::process, request)) {
            router.defaultFailed();
            return false;
        }
        router.defaultProcessed(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
        PaymentSaverWorker.enqueuePaymentForSave(request.setProvider("default"));
        return true;
    }

    private boolean processOnFallback(PaymentRequest request) {
        if (!send(paymentProcessorFallbackAsyncClient::process, request)) {
            router.fallbackFailed();
            return false;
        }
        PaymentSaverWorker.enqueuePaymentForSave(request.setProvider("fallback"));
        return true;
    }

    private static boolean send(Function<PaymentRequest, Response> processor, PaymentRequest request) {
        try (Response response = processor.apply(request)) {
            // 422: o processor já tinha registrado este correlationId numa tentativa cuja resposta se perdeu.
            return response.getStatus() < 300 || response.getStatus() == 422;
        } catch (Exception e) {
            return false;
        }
    }

    public void enqueuePaymentForProcess(PaymentRequest request) {
        paymentsToProcess.offer(request);
    }
}
