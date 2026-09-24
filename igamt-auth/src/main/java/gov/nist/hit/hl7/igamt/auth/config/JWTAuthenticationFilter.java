package gov.nist.hit.hl7.igamt.auth.config;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.JwtException;

public class JWTAuthenticationFilter extends OncePerRequestFilter {

  private static final Logger LOG = LoggerFactory.getLogger(JWTAuthenticationFilter.class);

  private final TokenAuthenticationService tokenService;
  private final RequestMatcher pathMatcher;

  public JWTAuthenticationFilter(String path, TokenAuthenticationService tokenService) {
    this.pathMatcher = new AntPathRequestMatcher(path);
    this.tokenService = tokenService;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
    try {
      UsernamePasswordAuthenticationToken authentication = tokenService.getAuthentication(request);
      // Install a context built from scratch instead of writing through whatever
      // instance getContext() returns. Under concurrent load that instance is not
      // always private to this request, and writing the no-cookie null through it
      // intermittently stripped the authentication out of another request that was
      // between its authorization check and its controller. That is the
      // "session expired"/"login required on refresh" report: page boots race their
      // own cookie-less asset and ig-ws requests against the authenticated API
      // calls. Replacing the thread's reference mutates nothing shared, and the
      // per-request outcome is unchanged: no cookie still means anonymous.
      SecurityContext freshContext = SecurityContextHolder.createEmptyContext();
      freshContext.setAuthentication(authentication);
      SecurityContextHolder.setContext(freshContext);
    } catch (JwtException | IllegalArgumentException badToken) {
      // The token itself is expired, malformed or not signed by us. Dropping the cookie
      // is the right answer: it will never verify, so keeping it only makes the user
      // hit a 403 on every request with no way back to the login screen.
      LOG.debug("Rejecting authCookie: {}", badToken.getMessage());
      clearAuthCookie(request, response);
      SecurityContextHolder.clearContext();
    } catch (Exception verificationFailed) {
      // The token was never actually judged: reading the verification key failed, or
      // something else broke underneath. That is our problem, not the user's, so leave
      // the cookie alone. This request is anonymous and will be refused, but the session
      // survives and the next request succeeds once the fault clears. Deleting the cookie
      // here is what turned a transient read failure into "my session keeps expiring".
      LOG.error("Could not verify authCookie, treating request as anonymous", verificationFailed);
      SecurityContextHolder.clearContext();
    }
    filterChain.doFilter(request, response);
  }

  private void clearAuthCookie(HttpServletRequest request, HttpServletResponse response) {
    Cookie authCookie = new Cookie("authCookie", "");
    // Include the context path so the path matches the cookie that was
    // issued. Empty at the root context, so this keeps the previous "/api".
    authCookie.setPath(request.getContextPath() + "/api");
    authCookie.setMaxAge(0);
    response.addCookie(authCookie);
  }
}
