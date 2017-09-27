package org.col.resources;

import com.codahale.metrics.annotation.Timed;
import org.apache.ibatis.session.SqlSession;
import org.col.api.Reference;
import org.col.db.mapper.ReferenceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;


@Path("{datasetKey}/reference")
@Produces(MediaType.APPLICATION_JSON)
public class ReferenceResource {
  private static final Logger LOG = LoggerFactory.getLogger(ReferenceResource.class);

  @GET
  @Timed
  @Path("{key}")
  public Reference get(@PathParam("datasetKey") Integer datasetKey, @PathParam("key") String key, @Context SqlSession session) {
    ReferenceMapper mapper = session.getMapper(ReferenceMapper.class);
    return mapper.get(datasetKey, key);
  }

}