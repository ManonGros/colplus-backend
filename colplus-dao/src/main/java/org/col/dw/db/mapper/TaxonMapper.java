package org.col.dw.db.mapper;

import org.apache.ibatis.annotations.Param;
import org.col.dw.api.Page;
import org.col.dw.api.Taxon;

import java.util.List;

/**
 *
 */
public interface TaxonMapper {

  int count(@Param("datasetKey") Integer datasetKey, @Param("root") Boolean root,
      @Param("nameKey") Integer nameKey);

  List<Taxon> list(@Param("datasetKey") Integer datasetKey, @Param("root") Boolean root,
                   @Param("nameKey") Integer nameKey, @Param("page") Page page);

  Integer lookupKey(@Param("id") String id, @Param("datasetKey") int datasetKey);

  Taxon get(@Param("key") int key);

  List<Taxon> classification(@Param("key") int key);

  int countChildren(@Param("key") int key);

  List<Taxon> children(@Param("key") int key, @Param("page") Page page);

  void create(Taxon taxon);

}