package org.payment.router.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public class PaymentsSummary {
    @JsonProperty("default")
    public SummaryData defaultProcessor;
    @JsonProperty("fallback")
    public SummaryData fallbackProcessor;

    public static class SummaryData {
        public int totalRequests;
        public BigDecimal totalAmount;

        public SummaryData(int totalRequests, BigDecimal totalAmount) {
            this.totalRequests = totalRequests;
            this.totalAmount = totalAmount;
        }
    }
}