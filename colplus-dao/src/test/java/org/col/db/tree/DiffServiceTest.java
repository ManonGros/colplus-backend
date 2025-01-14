package org.col.db.tree;

import java.io.BufferedReader;
import java.io.File;
import java.util.Collections;

import org.apache.commons.io.IOUtils;
import org.col.api.exception.NotFoundException;
import org.col.common.io.Resources;
import org.col.dao.DaoTestBase;
import org.col.dao.DatasetImportDao;
import org.col.db.mapper.TestDataRule;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class DiffServiceTest extends DaoTestBase {
  static int attemptCnt;
  DiffService diff;
  DatasetImportDao dao;
  
  public DiffServiceTest() {
    super(TestDataRule.tree());
    dao = new DatasetImportDao(factory(), treeRepoRule.getRepo());
    diff = new DiffService(factory(), dao.getTreeDao());
  }
  
  
  @Test
  public void attemptParsing() throws Exception {
    assertArrayEquals(new int[]{1,2}, diff.parseAttempts("1..2", ()-> Collections.EMPTY_LIST));
    assertArrayEquals(new int[]{10,120}, diff.parseAttempts("10..120", ()-> Collections.EMPTY_LIST));
  }
  
  @Test(expected = NotFoundException.class)
  public void attemptParsingFail() throws Exception {
    diff.parseAttempts("", ()-> Collections.EMPTY_LIST);
  }
  
  @Test(expected = IllegalArgumentException.class)
  public void attemptParsingFailBad() throws Exception {
    diff.parseAttempts("1234", ()-> Collections.EMPTY_LIST);
  }
  
  @Test(expected = IllegalArgumentException.class)
  public void attemptParsingFailBadSequence() throws Exception {
    diff.parseAttempts("5..3", ()-> Collections.EMPTY_LIST);
  }
  
  @Test
  public void udiff() throws Exception {
    final File f1 = Resources.toFile("trees/coldp.tree");
    final File f2 = Resources.toFile("trees/coldp2.tree");
    
    BufferedReader br = diff.udiff(new int[]{1,2}, i -> {
      switch (i) {
        case 1: return f1;
        case 2: return f2;
      }
      return null;
    });
  
  
    String version = IOUtils.toString(br);
    System.out.println(version);
    
    Assert.assertTrue(version.startsWith("---"));
  }
  
  @Test
  public void namesdiff() throws Exception {
    final File f1 = Resources.toFile("names1.txt");
    final File f2 = Resources.toFile("names2.txt");
    
    NamesDiff d = diff.namesDiff(99, new int[]{1,2}, i -> {
      switch (i) {
        case 1: return f1;
        case 2: return f2;
      }
      return null;
    });
    
    assertEquals(2, d.getDeleted().size());
    assertEquals(2, d.getInserted().size());
    assertEquals(99, d.getKey());
    assertEquals(1, d.getAttempt1());
    assertEquals(2, d.getAttempt2());
  }
}