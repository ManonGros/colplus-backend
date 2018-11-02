package org.col.dw.cors;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;

public class CorsFilter implements ContainerResponseFilter {
  private static final String ALLOW_ORIGIN_HEADER = "Access-Control-Allow-Origin";
  private static final String ALLOW_METHODS_HEADER = "Access-Control-Allow-Methods";
  private static final String ALLOW_METHODS = "OPTIONS, GET, POST, PUT, DELETE";
  private static final String VARY_HEADER = "Vary";
  private static final String VARY_VALUE = "Origin";
  
  private final CorsConfiguration cfg;
  
  public CorsFilter(CorsConfiguration cfg) {
    this.cfg = cfg;
  }
  
  @Override
  public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
    responseContext.getHeaders().add(ALLOW_ORIGIN_HEADER, cfg.allowedOrigins);
    responseContext.getHeaders().add(ALLOW_METHODS_HEADER, ALLOW_METHODS);
    if (!cfg.anyOrigin()) {
      //W3C CORS spec http://www.w3.org/TR/cors/#resource-implementation
      responseContext.getHeaders().add(VARY_HEADER, VARY_VALUE);
    }
  }
}