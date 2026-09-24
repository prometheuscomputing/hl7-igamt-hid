package gov.nist.hit.hl7.igamt.auth.config;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

import gov.nist.hit.hl7.auth.util.crypto.CryptoUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureException;
import io.jsonwebtoken.UnsupportedJwtException;

@Component
public class TokenAuthenticationService {

  @Autowired
  private CryptoUtil crypto;

  @Autowired
  Environment env;


  @SuppressWarnings("unchecked")
  public UsernamePasswordAuthenticationToken getAuthentication(HttpServletRequest request)
      throws ExpiredJwtException, UnsupportedJwtException, MalformedJwtException,
      SignatureException, IllegalArgumentException, FileNotFoundException, NoSuchAlgorithmException,
      InvalidKeySpecException, IOException {

    Cookie token = WebUtils.getCookie(request, "authCookie");

    if (token != null && token.getValue() != null && !token.getValue().isEmpty()) {
      Claims claims = Jwts.parser().setSigningKey(crypto.pub(env.getProperty("key.public")))
          .parseClaimsJws(token.getValue()).getBody();
      String username = claims.getSubject();
      List<Map<String, String>> roles = (List<Map<String, String>>) claims.get("roles");

      // A token with no roles claim used to NPE here, which the filter turned
      // into "clear the cookie and refuse the request". Keep that outcome, but
      // reach it deliberately: IllegalArgumentException is already the filter's
      // bad-token branch, so behaviour is unchanged and nothing silently
      // authenticates with an empty authority set.
      if (roles == null || roles.isEmpty()) {
        throw new IllegalArgumentException("authCookie carries no roles claim");
      }

      Collection<GrantedAuthority> authorities = new ArrayList<>();
      for (Map<String, String> role : roles) {
        authorities.add(new SimpleGrantedAuthority(role.get("authority")));
      }
      return new UsernamePasswordAuthenticationToken(username, token.getValue(), authorities);
    } else {
      return null;
    }
  }

}
