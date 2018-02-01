package org.col.dw.task;

import org.col.dw.AdminServer;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Manual test class to run an entire command in your IDE for debugging purposes mostly.
 * This can obviously also achieved by just calling the CliApp main class with the appropriate arguments
 */
@Ignore
public class ExecuteCmd {

  @Test
  public void test() throws Exception {
    // to run a command that needs configs please point the second argument to a matching yaml file
    new AdminServer().run(new String[]{"hello", "/Users/markus/Desktop/config.yml", "-n", "John"});
    //new CliApp().run(new String[]{"gbifsync", "/Users/markus/Desktop/config.yml"});
    //new CliApp().run(new String[]{"hello"});
  }
}