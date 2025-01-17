package org.col.db.type;

import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.col.api.model.CslData;
import org.col.db.type2.JsonAbstractHandler;

@MappedTypes(CslData.class)
@MappedJdbcTypes(JdbcType.OTHER)
public class CslJsonHandler extends JsonAbstractHandler<CslData> {
  
  public CslJsonHandler() {
    super(CslData.class);
  }
  
}
