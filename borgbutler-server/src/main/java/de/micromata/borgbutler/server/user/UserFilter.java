package de.micromata.borgbutler.server.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import java.io.IOException;

/**
 * Ensuring the user data inside request threads. For now, it's only a simple implementation (no login required).
 * Only the user's (client's) locale is used.
 * <br>
 * For requests from remote (not localhost) an exception is thrown due to security reasons.
 */
public class UserFilter implements Filter {
    private Logger log = LoggerFactory.getLogger(UserFilter.class);

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        String remoteAddr = request.getRemoteAddr();
        //if (remoteAddr == null || !remoteAddr.equals("127.0.0.1")) {
        //    log.warn("****************************************");
        //    log.warn("***********                   **********");
        //    log.warn("*********** SECURITY WARNING! **********");
        //    log.warn("***********                   **********");
        //    log.warn("*********** Externa access:   **********");
        //    log.warn("*********** " + remoteAddr + " **********");
        //    log.warn("***********                   **********");
        //    log.warn("****************************************");
        //    log.warn("Only access from local host yet supported due to security reasons.");
        //    throw new RuntimeException("Server is only available for localhost due to security reasons. A remote access is not yet available.");
        //}
        try {
            UserData userData = UserUtils.getUser();
            if (userData != null) {
                log.warn("****************************************");
                log.warn("***********                   **********");
                log.warn("*********** SECURITY WARNING! **********");
                log.warn("***********                   **********");
                log.warn("*********** Internal error:   **********");
                log.warn("*********** User already set! **********");
                log.warn("***********                   **********");
                log.warn("****************************************");
                log.warn("Don't deliver this app in dev mode due to security reasons!");
                String message = "User already given for this request. Rejecting request due to security reasons. Given user: " + userData;
                log.error(message);
                throw new IllegalArgumentException(message);
            }
            userData = UserManager.instance().getUser("dummy");
            UserUtils.setUser(userData, request.getLocale());
            if (log.isDebugEnabled()) log.debug("Request for user: " + userData);
            chain.doFilter(request, response);
        } finally {
            UserUtils.removeUser();
        }
    }

    @Override
    public void destroy() {
    }

}
