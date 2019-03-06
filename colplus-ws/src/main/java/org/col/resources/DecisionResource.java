package org.col.resources;

import java.util.List;
import javax.annotation.security.RolesAllowed;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import io.dropwizard.auth.Auth;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.ColUser;
import org.col.api.model.EditorialDecision;
import org.col.db.dao.DecisionRematcher;
import org.col.db.mapper.DecisionMapper;
import org.col.dw.auth.Roles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/decision")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class DecisionResource extends CRUDIntResource<EditorialDecision> {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(DecisionResource.class);
  private final SqlSessionFactory factory;
  
  public DecisionResource(SqlSessionFactory factory) {
    super(EditorialDecision.class, DecisionMapper.class);
    this.factory = factory;
  }
  
  @GET
  public List<EditorialDecision> list(@Context SqlSession session, @QueryParam("datasetKey") Integer datasetKey, @QueryParam("id") String id) {
    return session.getMapper(DecisionMapper.class).list(datasetKey, id);
  }
  
  @GET
  @Path("/broken")
  public List<EditorialDecision> broken(@Context SqlSession session, @QueryParam("datasetKey") Integer datasetKey) {
    DecisionMapper mapper = session.getMapper(DecisionMapper.class);
    return mapper.subjectBroken(datasetKey);
  }
  
  @POST
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  @Path("/{key}/rematch")
  public EditorialDecision rematch(@PathParam("key") Integer key, @Context SqlSession session, @Auth ColUser user) {
    EditorialDecision s = getNonNull(key, session);
    DecisionRematcher rem = new DecisionRematcher(factory);
    rem.matchDecision(s);
    return s;
  }
}
