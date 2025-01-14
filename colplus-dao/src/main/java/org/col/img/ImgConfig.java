package org.col.img;

import java.nio.file.Path;
import javax.validation.constraints.NotNull;

import org.col.db.PgDbConfig;

/**
 * A configuration for the postgres database connection pool as used by the mybatis layer.
 */
@SuppressWarnings("PublicField")
public class ImgConfig extends PgDbConfig {
  
  public enum Scale {
    ORIGINAL,
    LARGE,
    SMALL
  }
  
  @NotNull
  public Path repo;
  
  @NotNull
  public Size small = new Size(90, 60);
  
  @NotNull
  public Size large = new Size(300, 200);
  
  public Size size(Scale scale) {
    switch (scale) {
      case LARGE:
        return large;
      case SMALL:
        return small;
    }
    throw new IllegalArgumentException("No raw size supported");
  }
  
  public Path datasetLogo(int datasetKey, Scale scale) {
    return repo.resolve("dataset").resolve(filename(datasetKey + "-logo", scale));
  }
  
  private String filename(String prefix, Scale scale) {
    return String.format("%s-%s.%s", prefix, scale.name().toLowerCase(), ImageServiceFS.IMAGE_FORMAT);
  }
  
}
