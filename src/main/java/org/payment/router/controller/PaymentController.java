package org.payment.router.controller;

import io.smallrye.common.annotation.NonBlocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.payment.router.model.PaymentRequest;
import org.payment.router.worker.PaymentProcessorWorker;

@Path("/")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PaymentController {
    @Inject
    PaymentProcessorWorker paymentProcessorWorker;

    @POST
    @NonBlocking
    @Path("/payments")
    public Response processPayment(PaymentRequest request) {
        paymentProcessorWorker.enqueuePaymentForProcess(request);
        return Response.accepted().build();
    }
}
