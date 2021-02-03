package com.outbrain.BootCampOmlevi;

import com.outbrain.ob1k.server.AbstractOb1kServerTestBase;
import com.outbrain.ob1k.server.Server;
import org.junit.Test;

/**
 * Integration test for the BootCampOmleviServer
 */
public class BootCampOmleviTestIT extends AbstractOb1kServerTestBase {
  @Override
  protected Server buildServer() {
    return BootCampOmleviServer.buildServer(false);
  }

  @Test
  public void testSelfTest() throws Exception {
    assertSelfTestOk();
  }


  @Test
  public void testMyCoolUrl() throws Exception {
    assertUrlOk("/api/echo?name=ooo");
  }

}

