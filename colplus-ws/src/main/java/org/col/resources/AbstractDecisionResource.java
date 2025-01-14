package org.col.resources;

import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import io.dropwizard.auth.Auth;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.ColUser;
import org.col.api.model.DataEntity;
import org.col.api.model.DatasetScoped;
import org.col.api.vocab.Datasets;
import org.col.dao.EntityDao;
import org.col.dw.auth.Roles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public abstract class AbstractDecisionResource<T extends DataEntity<Integer> & DatasetScoped> extends AbstractGlobalResource<T> {
  
  private static final Logger LOG = LoggerFactory.getLogger(AbstractDecisionResource.class);

  public AbstractDecisionResource(Class<T> objClass, EntityDao<Integer, T, ?> dao, SqlSessionFactory factory) {
    super(objClass, dao, factory);
  }
  
  private void setDraftIfMissing(T obj) {
    if (obj.getDatasetKey() == null) {
      obj.setDatasetKey(Datasets.DRAFT_COL);
    }
  }
  
  @POST
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public Integer create(@Valid T obj, @Auth ColUser user) {
    setDraftIfMissing(obj);
    return super.create(obj, user);
  }
  
  @PUT
  @Path("{key}")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void update(@PathParam("key") Integer key, T obj, @Auth ColUser user) {
    setDraftIfMissing(obj);
    super.update(key, obj, user);
  }
  
}
