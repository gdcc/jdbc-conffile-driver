package io.gdcc.jdbc.conffile.demo;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.core.Response;

public class Info {
    @GET
    public Response info() {
        return Response.ok("Hello World!").build();
    }
}
