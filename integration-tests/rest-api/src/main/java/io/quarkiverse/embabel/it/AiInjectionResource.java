package io.quarkiverse.embabel.it;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * REST endpoint to verify that Ai-related beans can be injected via CDI.
 * <p>
 * This resource demonstrates that the AiBeansProducer correctly provides
 * Ai, ExecutingOperationContext, and AiBuilder as injectable CDI beans.
 */
@Path("/ai-injection")
public class AiInjectionResource {

    @Inject
    AiInjectionDemoComponent demoComponent;

    /**
     * Verify that all Ai-related beans were successfully injected.
     */
    @GET
    @Path("/status")
    @Produces(MediaType.TEXT_PLAIN)
    public String checkInjectionStatus() {
        if (demoComponent.areBeansInjected()) {
            return "SUCCESS: All Ai beans injected";
        } else {
            return "FAILURE: Some Ai beans not injected";
        }
    }

    /**
     * Get detailed information about the injected beans.
     */
    @GET
    @Path("/info")
    @Produces(MediaType.TEXT_PLAIN)
    public String getBeanInfo() {
        return demoComponent.getBeanInfo();
    }
}