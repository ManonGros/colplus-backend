package org.col.dw;

import java.util.Collection;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;

import org.col.api.model.Page;
import org.col.api.search.DatasetSearchRequest;
import org.col.api.search.NameSearchRequest;

import static org.glassfish.jersey.client.authentication.HttpAuthenticationFeature.HTTP_AUTHENTICATION_BASIC_PASSWORD;
import static org.glassfish.jersey.client.authentication.HttpAuthenticationFeature.HTTP_AUTHENTICATION_BASIC_USERNAME;

public class ApiUtils {
  
  private ApiUtils(){};
  
  public static WebTarget applyPage(WebTarget wt, Page page) {
    return page == null ? wt : wt.queryParam("offset", page.getOffset())
        .queryParam("limit", page.getLimit());
  }
  
  public static Invocation.Builder applyCreds(WebTarget wt, String username, String password) {
    return wt.request()
        .property(HTTP_AUTHENTICATION_BASIC_USERNAME, username)
        .property(HTTP_AUTHENTICATION_BASIC_PASSWORD, password);
  }
  
  /**
   * Make sure the user is registered with your the AuthenticationProvider
   */
  public static Invocation.Builder userCreds(WebTarget wt) {
    return wt.request()
        .property(HTTP_AUTHENTICATION_BASIC_USERNAME, "user")
        .property(HTTP_AUTHENTICATION_BASIC_PASSWORD, "1234");
  }
  
  public static Invocation.Builder editorCreds(WebTarget wt) {
    return wt.request()
        .property(HTTP_AUTHENTICATION_BASIC_USERNAME, "editor")
        .property(HTTP_AUTHENTICATION_BASIC_PASSWORD, "123456");
  }

  public static WebTarget applySearch(WebTarget wt, DatasetSearchRequest search, Page page) {
    if (search == null) return wt;
  
    return applyPage(wt, page)
        .queryParam("q", search.getQ())
        .queryParam("code", search.getCode())
        .queryParam("contributesTo", search.getContributesTo())
        .queryParam("format", search.getFormat())
        .queryParam("type", search.getType())
        .queryParam("modified", search.getModified())
        .queryParam("created", search.getCreated())
        .queryParam("released", search.getReleased())
        .queryParam("sortBy", search.getSortBy())
        .queryParam("reverse", search.isReverse());
  }
  
  @SuppressWarnings("unchecked")
  private static <T> T[] array(Collection<T> values) {
    if (values == null || values.isEmpty()) {
      return (T[]) new Object[0];
    }
    return (T[]) values.toArray();
  }

  public static WebTarget applySearch(WebTarget wt, NameSearchRequest search) {
    return wt;
  }
  
  public static Entity json(Object obj) {
    return Entity.entity(obj, MediaType.APPLICATION_JSON);
  }
  
}
