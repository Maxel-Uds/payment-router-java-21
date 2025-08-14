package org.payment.router.worker;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.payment.router.client.PaymentProcessorDefaultAsyncClient;
import org.payment.router.client.PaymentProcessorFallbackAsyncClient;
import org.payment.router.model.PaymentRequest;

import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.IntStream;

@Singleton
public class PaymentProcessorWorker {
    @ConfigProperty(name = "max.workers.process")
    int MAX_WORKERS_TO_PROCESS;

    @Inject
    @RestClient
    PaymentProcessorDefaultAsyncClient paymentProcessorDefaultAsyncClient;

    @Inject
    @RestClient
    PaymentProcessorFallbackAsyncClient paymentProcessorFallbackAsyncClient;

    static final LinkedBlockingQueue<PaymentRequest> paymentsToProcess = new LinkedBlockingQueue<>();

    public void startProcessWorker(@Observes StartupEvent ev) {
        IntStream.range(0, MAX_WORKERS_TO_PROCESS).forEach(__-> Executors.newVirtualThreadPerTaskExecutor().submit(() -> {
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
            PaymentSaverWorker.enqueuePaymentForSave(paymentReadyToProcess);
        } catch (Exception eDefault) {
            try {
                final PaymentRequest paymentReadyToProcess = request.toProcessOnFallback();
                this.paymentProcessorFallbackAsyncClient.process(paymentReadyToProcess);
                PaymentSaverWorker.enqueuePaymentForSave(paymentReadyToProcess);
            } catch (Exception eFallback) {
                this.enqueuePaymentForProcess(request);
            }
        }
    }

    public void enqueuePaymentForProcess(PaymentRequest request) {
        paymentsToProcess.offer(request);
    }
}