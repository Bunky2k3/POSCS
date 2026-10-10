package poscs.common;

import jakarta.servlet.http.HttpServletRequest;

public class ClientIp {
    private ClientIp() {
        // Utility class
    }

    public static String get(HttpServletRequest request) {
        // We do not trust X-Forwarded-For unconditionally.
        // If there's a trusted reverse proxy, it should be configured via Tomcat RemoteIpValve.
        return request.getRemoteAddr();
    }
}
