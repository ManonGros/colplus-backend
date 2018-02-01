package org.col.dw.config;

import javax.validation.constraints.Min;

/**
 *
 */
@SuppressWarnings("PublicField")
public class ImporterConfig {

  @Min(1)
  public int batchSize = 10000;

  /**
   * Number of parallel imports to allow simultanously
   */
  @Min(1)
  public int threads = 1;

  /**
   * Max size of queued import jobs before rejecting
   */
  @Min(10)
  public int maxQueue = 1000;

  /**
   * Duration in minutes the continous import scheduler will fall to sleep if imports are running already.
   * Zero or negative values will turn off continuous importing.
   */
  @Min(0)
  public int continousImportPolling = 0;

}