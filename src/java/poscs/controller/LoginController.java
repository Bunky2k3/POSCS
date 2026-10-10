package poscs.controller;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import poscs.model.User;
import poscs.service.AuthService;
import poscs.service.ServiceResult;

@WebServlet(name = "LoginController", urlPatterns = {"/login"})
public class LoginController extends HttpServlet {

    private final AuthService authService = new AuthService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.sendRedirect(request.getContextPath() + "/login.jsp");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String identifier = request.getParameter("username");
        String password = request.getParameter("password");

        String clientIp = poscs.common.ClientIp.get(request);
        long now = System.currentTimeMillis();

        ServiceResult<User> result = authService.login(identifier, password, clientIp, now);

        if (!result.isSuccess()) {
            redirectToLoginWithError(request, response, result.getMessage(), identifier);
            return;
        }

        HttpSession oldSession = request.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }
        HttpSession session = request.getSession(true);
        session.setAttribute("currentUser", result.getData());
        response.sendRedirect(request.getContextPath() + "/dashboard");
    }

    private void redirectToLoginWithError(HttpServletRequest request, HttpServletResponse response,
            String error, String identifier) throws IOException {
        String url = request.getContextPath() + "/login.jsp?error=" + error;
        if (identifier != null && !identifier.trim().isEmpty()) {
            String safeId = identifier.trim();
            if (safeId.length() > 50) {
                safeId = safeId.substring(0, 50);
            }
            url += "&username=" + URLEncoder.encode(safeId, StandardCharsets.UTF_8);
        }
        response.sendRedirect(url);
    }
}
