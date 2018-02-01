package org.col.dw.resources;

import org.apache.ibatis.session.SqlSessionFactory;
import org.col.dw.api.DatasetImport;
import org.col.dw.api.Page;
import org.col.dw.api.ResultPage;
import org.col.dw.dao.DatasetImportDao;
import org.col.dw.task.importer.ImportManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Path("/importer")
@Produces(MediaType.APPLICATION_JSON)
public class ImporterResource {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(ImporterResource.class);
  private final ImportManager importManager;
  private final DatasetImportDao dao;

  public ImporterResource(ImportManager importManager, SqlSessionFactory factory) {
    this.importManager = importManager;
    dao = new DatasetImportDao(factory);
  }

  @GET
  public ResultPage<DatasetImport> list(@Valid @BeanParam Page page) {
    return dao.list(page);
  }

  @POST
  public ImportManager.ImportRequest schedule(@QueryParam("key") Integer datasetKey, @QueryParam("force") Boolean force) {
    return importManager.submit(datasetKey, force);
  }

  @GET
  @Path("/queue")
  public List<ImportManager.ImportRequest> queue() {
    return importManager.list();
  }

  @DELETE
  @Path("{key}")
  public void cancel(@PathParam("key") Integer datasetKey) {
    importManager.cancel(datasetKey);
  }

}