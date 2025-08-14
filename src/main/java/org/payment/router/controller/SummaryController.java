package org.payment.router.controller;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.payment.router.model.PaymentsSummary;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Path("/")
public class SummaryController {

    @Inject
    Pool client;

    @GET
    @Path("/payments-summary")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<PaymentsSummary> getSummary(@QueryParam("from") String from, @QueryParam("to") String to) {
        Instant fromInstant = parseDate(from);
        Instant toInstant = parseDate(to);

        StringBuilder sql = new StringBuilder("SELECT provider, count(*) as total_requests, sum(amount) as total_amount FROM payments");
        List<Object> params = new ArrayList<>();

        if (fromInstant != null || toInstant != null) {
            sql.append(" WHERE ");
            if (fromInstant != null) {
                sql.append("requested_at >= $1");
                params.add(fromInstant.atZone(ZoneOffset.UTC).toLocalDateTime());
            }
            if (toInstant != null) {
                if (fromInstant != null) {
                    sql.append(" AND ");
                }
                sql.append("requested_at <= $").append(params.size() + 1);
                params.add(toInstant.atZone(ZoneOffset.UTC).toLocalDateTime());
            }
        }

        sql.append(" GROUP BY provider");

        return client.preparedQuery(sql.toString())
                .execute(Tuple.from(params))
                .onItem().transform(rows -> {
                    Map<String, PaymentsSummary.SummaryData> results = new HashMap<>();
                    for (Row row : rows) {
                        results.put(row.getString("provider"),
                                new PaymentsSummary.SummaryData(
                                        row.getInteger("total_requests"),
                                        row.getBigDecimal("total_amount").setScale(2, RoundingMode.HALF_UP)
                                )
                        );
                    }

                    PaymentsSummary summary = new PaymentsSummary();
                    summary.defaultProcessor = results.getOrDefault("default", new PaymentsSummary.SummaryData(0, BigDecimal.ZERO));
                    summary.fallbackProcessor = results.getOrDefault("fallback", new PaymentsSummary.SummaryData(0, BigDecimal.ZERO));

                    return summary;
                });
    }

    private Instant parseDate(String dateStr) {
        if (dateStr == null) return null;
        try {
            return Instant.parse(dateStr);
        } catch (DateTimeParseException e) {
            throw new BadRequestException("Invalid date format, must be ISO-8601 in UTC");
        }
    }
}