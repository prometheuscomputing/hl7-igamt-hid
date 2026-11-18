package gov.nist.hit.hl7.igamt.serialization.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.util.Locale;

/**
 * Fetches a picture an author linked to in rich text, so that the export carries it.
 *
 * The address comes from a user and the request is made by the server, so it is held to what a
 * picture on the public web needs: http or https, a host that is not this machine or its private
 * network (which is where the metadata service of a cloud host answers), an image as the answer, a
 * bounded size and time. Anything else returns null and the picture keeps its address.
 */
final class RemoteImageLoader {

  static final int MAX_BYTES = 10 * 1024 * 1024;
  private static final int TIMEOUT_MILLIS = 5000;
  private static final int TOTAL_MILLIS = 20000;
  private static final int MAX_REDIRECTS = 3;

  /** The bytes of a picture and its media type, such as {@code image/png}. */
  static final class Image {
    final byte[] bytes;
    final String mediaType;

    Image(byte[] bytes, String mediaType) {
      this.bytes = bytes;
      this.mediaType = mediaType;
    }
  }

  private RemoteImageLoader() {
  }

  static Image load(String address) {
    if (!addressesAreRemembered()) {
      return null;
    }
    try {
      URL url = new URL(address.trim());
      for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
        if (!isPublicWebAddress(url)) {
          return null;
        }
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(TIMEOUT_MILLIS);
        connection.setReadTimeout(TIMEOUT_MILLIS);
        connection.setRequestProperty("Accept", "image/*");
        try {
          int status = connection.getResponseCode();
          if (status >= 300 && status < 400) {
            String location = connection.getHeaderField("Location");
            if (location == null) {
              return null;
            }
            url = new URL(url, location);
            continue;
          }
          String type = connection.getContentType();
          type = type == null ? "" : type.split(";")[0].trim().toLowerCase(Locale.ENGLISH);
          if (status != 200 || !type.startsWith("image/")) {
            return null;
          }
          try (InputStream in = connection.getInputStream()) {
            byte[] bytes = readBounded(in);
            return bytes == null || bytes.length == 0 ? null : new Image(bytes, type);
          }
        } finally {
          connection.disconnect();
        }
      }
      return null;
    } catch (IOException | RuntimeException e) {
      return null;
    }
  }

  /**
   * The address of a host is checked, then the connection looks it up again. The two answers are
   * the same because Java remembers a lookup for a while (30 seconds unless configured). Where that
   * memory is switched off a host could answer "public" first and "local" next: no picture is
   * fetched there.
   */
  static boolean addressesAreRemembered() {
    return !"0".equals(java.security.Security.getProperty("networkaddress.cache.ttl")) && !"0".equals(System.getProperty("sun.net.inetaddr.ttl"));
  }

  private static byte[] readBounded(InputStream in) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    // the time limit of the connection is per read: a server sending a byte now and then is cut off here
    long deadline = System.currentTimeMillis() + TOTAL_MILLIS;
    int read;
    while ((read = in.read(buffer)) != -1) {
      if (out.size() + read > MAX_BYTES || System.currentTimeMillis() > deadline) {
        return null;
      }
      out.write(buffer, 0, read);
    }
    return out.toByteArray();
  }

  /** http or https, to a host none of whose addresses is local to this machine or its network. */
  static boolean isPublicWebAddress(URL url) {
    String protocol = url.getProtocol() == null ? "" : url.getProtocol().toLowerCase(Locale.ENGLISH);
    if (!("http".equals(protocol) || "https".equals(protocol)) || url.getHost() == null || url.getHost().isEmpty()) {
      return false;
    }
    try {
      InetAddress[] addresses = InetAddress.getAllByName(url.getHost());
      if (addresses.length == 0) {
        return false;
      }
      for (InetAddress address : addresses) {
        if (!isPublic(address)) {
          return false;
        }
      }
      return true;
    } catch (IOException | RuntimeException e) {
      return false;
    }
  }

  static boolean isPublic(InetAddress address) {
    if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()
        || address.isMulticastAddress()) {
      return false;
    }
    byte[] raw = address.getAddress();
    if (raw.length == 16) {
      // unique local fc00::/7, and an IPv4 address written as IPv6 (::ffff:a.b.c.d)
      if ((raw[0] & 0xfe) == 0xfc) {
        return false;
      }
      boolean mapped = raw[10] == (byte) 0xff && raw[11] == (byte) 0xff;
      for (int i = 0; i < 10 && mapped; i++) {
        mapped = raw[i] == 0;
      }
      if (mapped) {
        try {
          return isPublic(InetAddress.getByAddress(new byte[] {raw[12], raw[13], raw[14], raw[15]}));
        } catch (IOException e) {
          return false;
        }
      }
      return true;
    }
    int first = raw[0] & 0xff;
    int second = raw[1] & 0xff;
    // "this network", carrier-grade NAT 100.64/10, benchmarking 198.18/15, reserved 240/4
    return !(first == 0 || (first == 100 && second >= 64 && second <= 127) || (first == 198 && (second == 18 || second == 19)) || first >= 240);
  }
}
