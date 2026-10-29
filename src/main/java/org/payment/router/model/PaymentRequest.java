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

    public PaymentRequest setProvider(String provider) {
        this.provider = provider;
        return this;
    }

    // Fixo desde a primeira tentativa: num 422 o processor guardou exatamente este valor.
    public PaymentRequest markRequestedAtIfAbsent() {
        if (this.requestedAt == null) {
            this.requestedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        }
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
}
