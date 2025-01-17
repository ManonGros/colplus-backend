package org.col.db.mapper;

import java.util.List;
import javax.annotation.Nullable;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.ResultHandler;
import org.col.api.model.DSID;
import org.col.api.model.Name;
import org.col.db.CRUD;
import org.col.db.DatasetPageable;

/**
 * When creating a new name if the homotypic group key is not yet set the newly created name key will be
 * used to point to the name itself
 */
public interface NameMapper extends CRUD<DSID<String>, Name>, ProcessableDataset<Name>, DatasetPageable<Name> {
  
  Name getByUsage(@Param("datasetKey") int datasetKey, @Param("usageId") String usageId);
  
  /**
   * Lists all distinct name index ids from the names table.
   */
  void processIndexIds(@Param("datasetKey") int datasetKey,
                       @Nullable @Param("sectorKey") Integer sectorKey,
                       ResultHandler<String> handler);

  /**
   * Lists all homotypic names based on the same homotypic name key
   *
   * @param nameId name id of the homotypic group
   */
  List<Name> homotypicGroup(@Param("datasetKey") int datasetKey, @Param("id") String nameId);
  
  /**
   * Returns the list of names published in the same reference.
   */
  List<Name> listByReference(@Param("datasetKey") int datasetKey, @Param("refId") String publishedInId);
  
  /**
   * Lists all names with the same names index key across all datasets.
   *
   * @param nameId from the names index!
   */
  List<Name> indexGroup(@Param("id") String nameId);
  
  /**
   *
   * @param datasetKey
   * @param id
   * @param nameIndexID
   */
  void updateMatch(@Param("datasetKey") int datasetKey, @Param("id") String id, @Param("nameIndexID") String nameIndexID);
  
  /**
   * Delete all bare names of a dataset
   */
  int deleteOrphans(@Param("datasetKey") int datasetKey);
  
  int deleteBySector(@Param("datasetKey") int datasetKey, @Param("sectorKey") int sectorKey);
  
  /**
   * @return true if at least one record for the given dataset exists
   */
  boolean hasData(@Param("datasetKey") int datasetKey);
  
}
