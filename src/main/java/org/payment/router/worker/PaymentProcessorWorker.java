package org.payment.router.worker;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.payment.router.client.PaymentProcessorDefaultAsyncClient;
import org.payment.router.client.PaymentProcessorFallbackAsyncClient;
import org.payment.router.client.PaymentStorageAsyncClient;
import org.payment.router.model.PaymentRequest;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.IntStream;

@Singleton
public class PaymentProcessorWorker {
    static final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @ConfigProperty(name = "max.workers.process")
    int maxWorkersToProcess;

    @Inject
    @RestClient
    PaymentProcessorDefaultAsyncClient paymentProcessorDefaultAsyncClient;

    @Inject
    @RestClient
    PaymentProcessorFallbackAsyncClient paymentProcessorFallbackAsyncClient;

    @Inject
    @RestClient
    PaymentStorageAsyncClient paymentStorageAsyncClient;

    private static final LinkedBlockingQueue<PaymentRequest> paymentsToProcess = new LinkedBlockingQueue<>();

    public void startProcessWorker(@Observes StartupEvent ev) {
        IntStream.range(0, maxWorkersToProcess).forEach(__-> executor.submit(() -> {
                    for(;;) {
                        try {
                            this.processPayment(this.getNextPaymentToProcess());
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                })
        );
    }

    private PaymentRequest getNextPaymentToProcess() throws InterruptedException {
        return paymentsToProcess.take();
    }

    public void processPayment(PaymentRequest request) {
        try {
            final PaymentRequest paymentReadyToProcess = request.toProcessOnDefault();
            this.paymentProcessorDefaultAsyncClient.process(paymentReadyToProcess);
            this.paymentStorageAsyncClient.save(paymentReadyToProcess);
        } catch (Exception eDefault) {
            try {
                final PaymentRequest paymentReadyToProcess = request.toProcessOnFallback();
                this.paymentProcessorFallbackAsyncClient.process(paymentReadyToProcess);
                this.paymentStorageAsyncClient.save(paymentReadyToProcess);
            } catch (Exception eFallback) {
                this.enqueuePaymentForProcess(request);
            }
        }
    }

    public void enqueuePaymentForProcess(PaymentRequest request) {
        paymentsToProcess.offer(request);
    }

    public void stopProcessWorker(@Observes ShutdownEvent ev) {
        executor.shutdownNow();
    }
}
