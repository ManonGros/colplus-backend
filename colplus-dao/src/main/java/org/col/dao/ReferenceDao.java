package org.col.dao;

import org.apache.ibatis.session.SqlSession;
import org.col.api.Page;
import org.col.api.PagingResultSet;
import org.col.api.Reference;
import org.col.db.mapper.ReferenceMapper;

import java.util.List;

public class ReferenceDao {

	private final SqlSession session;

	public ReferenceDao(SqlSession sqlSession) {
		this.session = sqlSession;
	}

	public int count(int datasetKey) {
		ReferenceMapper mapper = session.getMapper(ReferenceMapper.class);
		return mapper.count(datasetKey);
	}

	public PagingResultSet<Reference> list(int datasetKey, Page page) {
		ReferenceMapper mapper = session.getMapper(ReferenceMapper.class);
		int total = mapper.count(datasetKey);
		List<Reference> result = mapper.list(datasetKey, page);
		return new PagingResultSet<>(page, total, result);
	}

	public Integer lookupKey(String id, int datasetKey) {
		ReferenceMapper mapper = session.getMapper(ReferenceMapper.class);
		return mapper.lookupKey(id, datasetKey);
	}

	public Reference get(int key) {
		ReferenceMapper mapper = session.getMapper(ReferenceMapper.class);
		return mapper.get(key);
	}

	public void create(Reference ref) {
		ReferenceMapper mapper = session.getMapper(ReferenceMapper.class);
		mapper.create(ref);
	}

}
