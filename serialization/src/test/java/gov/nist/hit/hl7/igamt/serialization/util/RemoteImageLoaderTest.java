package gov.nist.hit.hl7.igamt.serialization.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.net.InetAddress;
import java.net.URL;

import org.junit.Test;

/** The picture of a rich text is fetched by the server from an address a user typed. */
public class RemoteImageLoaderTest {

  private static boolean isPublic(String literal) throws Exception {
    return RemoteImageLoader.isPublic(InetAddress.getByName(literal));
  }

  @Test
  public void addressesOfThisMachineAndItsNetworkAreRefused() throws Exception {
    for (String local : new String[] {"127.0.0.1", "127.8.9.1", "0.0.0.0", "10.1.2.3", "172.16.0.9", "172.31.255.1", "192.168.1.1",
        "169.254.169.254", "100.64.0.1", "198.18.0.1", "224.0.0.1", "255.255.255.255", "::1", "::", "fe80::1", "fc00::1", "fd12:3456::1",
        "::ffff:127.0.0.1", "::ffff:169.254.169.254", "::ffff:10.0.0.1"}) {
      assertFalse(local, isPublic(local));
    }
  }

  @Test
  public void addressesOfThePublicWebAreAccepted() throws Exception {
    for (String open : new String[] {"8.8.8.8", "93.184.216.34", "172.32.0.1", "100.63.0.1", "2606:4700:4700::1111", "::ffff:8.8.8.8"}) {
      assertTrue(open, isPublic(open));
    }
  }

  @Test
  public void onlyTheWebSchemesAreFetched() throws Exception {
    assertFalse(RemoteImageLoader.isPublicWebAddress(new URL("file:///etc/passwd")));
    assertFalse(RemoteImageLoader.isPublicWebAddress(new URL("ftp://8.8.8.8/a.png")));
    assertFalse(RemoteImageLoader.isPublicWebAddress(new URL("jar:file:/a.jar!/b.png")));
    assertFalse(RemoteImageLoader.isPublicWebAddress(new URL("http://127.0.0.1:8080/a.png")));
    assertFalse(RemoteImageLoader.isPublicWebAddress(new URL("http://localhost/a.png")));
    assertFalse(RemoteImageLoader.isPublicWebAddress(new URL("http://169.254.169.254/latest/meta-data/")));
    assertFalse(RemoteImageLoader.isPublicWebAddress(new URL("http://[::1]/a.png")));
    assertTrue(RemoteImageLoader.isPublicWebAddress(new URL("https://8.8.8.8/a.png")));
  }

  @Test
  public void whatCannotBeFetchedLeavesThePictureAsItIs() {
    assertNull(RemoteImageLoader.load("file:///etc/passwd"));
    assertNull(RemoteImageLoader.load("http://127.0.0.1:1/a.png"));
    assertNull(RemoteImageLoader.load("not an address"));
    assertNull(RemoteImageLoader.load("data:image/png;base64,AAAA"));
  }
}
