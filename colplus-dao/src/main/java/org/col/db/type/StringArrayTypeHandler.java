/*
 * Copyright 2013 Global Biodiversity Information Facility (GBIF)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copyTaxon of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.col.db.type;

import java.sql.*;
import java.util.Collections;
import java.util.List;

import com.google.common.collect.Lists;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/**
 * String array handler that avoids nulls and uses empty arrays instead.
 */
public class StringArrayTypeHandler extends BaseTypeHandler<List<String>> {
  
  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, List<String> parameter, JdbcType jdbcType) throws SQLException {
    Array array = ps.getConnection().createArrayOf("text", parameter.toArray());
    ps.setArray(i, array);
  }
  
  @Override
  public void setParameter(PreparedStatement ps, int i, List<String> parameter, JdbcType jdbcType) throws SQLException {
    setNonNullParameter(ps, i, parameter == null ? Collections.emptyList() : parameter, jdbcType);
  }
  
  @Override
  public List<String> getNullableResult(ResultSet rs, String columnName) throws SQLException {
    return toList(rs.getArray(columnName));
  }
  
  @Override
  public List<String> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    return toList(rs.getArray(columnIndex));
  }
  
  @Override
  public List<String> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    return toList(cs.getArray(columnIndex));
  }
  
  private List<String> toList(Array pgArray) throws SQLException {
    if (pgArray == null) return Lists.newArrayList();
    
    String[] strings = (String[]) pgArray.getArray();
    return Lists.newArrayList(strings);
  }
}
