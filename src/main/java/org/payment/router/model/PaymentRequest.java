package org.payment.router.model;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class PaymentRequest {
    public String correlationId;
    public Double amount;
    public Instant requestedAt;
    public String provider;

    public PaymentRequest() {
    }

    public PaymentRequest(String correlationId, Double amount) {
        this.correlationId = correlationId;
        this.amount = amount;
    }

    public PaymentRequest setProvider(String provider) {
        this.provider = provider;
        return this;
    }

    public PaymentRequest setRequestedAt(Instant now) {
        this.requestedAt = now;
        return this;
    }

    @Override
    public String toString() {
        return "PaymentRequest{" +
                "correlationId='" + correlationId + '\'' +
                ", amount=" + amount +
                ", requestedAt=" + requestedAt +
                ", provider='" + provider + '\'' +
                '}';
    }

    public PaymentRequest toProcessOnDefault() {
        return new PaymentRequest(this.correlationId, this.amount)
                .setProvider("default")
                .setRequestedAt(Instant.now().truncatedTo(ChronoUnit.SECONDS));
    }

    public PaymentRequest toProcessOnFallback() {
        return new PaymentRequest(this.correlationId, this.amount)
                .setProvider("fallback")
                .setRequestedAt(Instant.now().truncatedTo(ChronoUnit.SECONDS));
    }
}
