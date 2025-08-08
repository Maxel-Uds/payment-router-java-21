package org.payment.router.client;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.payment.router.model.PaymentRequest;

@RegisterRestClient(configKey="storage-async-api", baseUri = "${quarkus.rest-client.storage-async-api.url}")
public interface PaymentStorageAsyncClient {
    @POST
    @Path("/save")
    Response save(PaymentRequest request);
}
