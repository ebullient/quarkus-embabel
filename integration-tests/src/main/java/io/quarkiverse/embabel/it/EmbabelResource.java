package io.quarkiverse.embabel.it;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Simple REST endpoint to verify the Quarkus application starts successfully
 * with the Embabel Agent extension loaded.
 */
@Path("/embabel")
public class EmbabelResource {

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String hello() {
        return "Embabel Agent extension is loaded";
    }
}
