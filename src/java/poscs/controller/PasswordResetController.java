package poscs.controller;

import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import poscs.common.Logs;
import poscs.model.User;
import poscs.service.AttributeStore;
import poscs.service.AuthService;
import poscs.service.ServiceResult;

@WebServlet(name = "PasswordResetController", urlPatterns = {
    "/changePassword", "/ForgotPasswordServlet", "/VerifyOtpServlet", "/ResetPasswordServlet", "/ResendOtpServlet"
})
public class PasswordResetController extends HttpServlet {

    private static final Logger LOG = LoggerFactory.getLogger(PasswordResetController.class);
    private final AuthService authService = new AuthService();

    @Override
    public void destroy() {
        authService.shutdown();
        super.destroy();
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String path = request.getServletPath();
        switch (path) {
            case "/changePassword":
                handleChangePassword(request, response);
                break;
            case "/ForgotPasswordServlet":
                handleForgotPassword(request, response);
                break;
            case "/VerifyOtpServlet":
                handleVerifyOtp(request, response);
                break;
            case "/ResetPasswordServlet":
                handleResetPassword(request, response);
                break;
            case "/ResendOtpServlet":
                handleResendOtp(request, response);
                break;
            default:
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                break;
        }
    }

    private void handleChangePassword(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        User currentUser = session != null ? (User) session.getAttribute("currentUser") : null;

        if (currentUser == null) {
            response.sendRedirect(request.getContextPath() + "/login.jsp");
            return;
        }

        String oldPassword = request.getParameter("oldPassword");
        String newPassword = request.getParameter("newPassword");
        String confirmPassword = request.getParameter("confirmPassword");
        String clientIp = poscs.common.ClientIp.get(request);
        long now = System.currentTimeMillis();

        ServiceResult<Void> result = authService.changePassword(currentUser, oldPassword, newPassword, confirmPassword, clientIp, now);

        if (!result.isSuccess()) {
            if (AuthService.ERR_SESSION_INVALID.equals(result.getMessage())) {
                if (session != null) {
                    session.invalidate();
                }
                response.sendRedirect(request.getContextPath() + "/login.jsp");
                return;
            }
            if (AuthService.ERR_UPDATE_FAILED.equals(result.getMessage())) {
                LOG.warn("Doi mat khau that bai o buoc ghi CSDL (actor={})", Logs.actor(request));
            }
            response.sendRedirect(request.getContextPath() + "/changePassword.jsp?error=" + result.getMessage());
            return;
        }

        if (session != null) {
            session.invalidate();
        }
        response.sendRedirect(request.getContextPath() + "/login.jsp?reset=success");
    }

    private void handleForgotPassword(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String username = request.getParameter("username");
        String clientIp = poscs.common.ClientIp.get(request);
        long now = System.currentTimeMillis();

        HttpSession session = request.getSession(true);
        ServiceResult<Void> result = authService.requestOtp(username, clientIp, now, new SessionAttributeStore(session));

        if (!result.isSuccess()) {
            response.sendRedirect(request.getContextPath() + "/forgotPassword.jsp?error=" + result.getMessage());
            return;
        }
        response.sendRedirect(request.getContextPath() + "/verifyOtp.jsp");
    }

    private void handleResendOtp(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        String clientIp = poscs.common.ClientIp.get(request);
        long now = System.currentTimeMillis();

        if (session == null) {
            response.sendRedirect(request.getContextPath() + "/forgotPassword.jsp?error=" + AuthService.ERR_SESSION_EXPIRED);
            return;
        }

        ServiceResult<Void> result = authService.resendOtp(clientIp, now, new SessionAttributeStore(session));

        if (!result.isSuccess()) {
            if (AuthService.ERR_SESSION_EXPIRED.equals(result.getMessage())) {
                response.sendRedirect(request.getContextPath() + "/forgotPassword.jsp?error=" + result.getMessage());
            } else {
                response.sendRedirect(request.getContextPath() + "/verifyOtp.jsp?error=" + result.getMessage());
            }
            return;
        }
        response.sendRedirect(request.getContextPath() + "/verifyOtp.jsp");
    }

    private void handleVerifyOtp(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String otpInput = request.getParameter("otp");
        long now = System.currentTimeMillis();
        HttpSession session = request.getSession(false);

        if (session == null) {
            response.sendRedirect(request.getContextPath() + "/forgotPassword.jsp?error=" + AuthService.ERR_SESSION_EXPIRED);
            return;
        }

        ServiceResult<Void> result = authService.verifyOtp(otpInput, now, new SessionAttributeStore(session));

        if (!result.isSuccess()) {
            if (AuthService.ERR_SESSION_EXPIRED.equals(result.getMessage())
                    || AuthService.ERR_EXPIRED.equals(result.getMessage())
                    || AuthService.ERR_TOO_MANY_ATTEMPTS.equals(result.getMessage())) {
                response.sendRedirect(request.getContextPath() + "/forgotPassword.jsp?error=" + result.getMessage());
            } else {
                response.sendRedirect(request.getContextPath() + "/verifyOtp.jsp?error=" + result.getMessage());
            }
            return;
        }
        response.sendRedirect(request.getContextPath() + "/resetPassword.jsp");
    }

    private void handleResetPassword(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String newPassword = request.getParameter("newPassword");
        String confirmPassword = request.getParameter("confirmPassword");
        HttpSession session = request.getSession(false);

        long now = System.currentTimeMillis();

        if (session == null) {
            response.sendRedirect(request.getContextPath() + "/forgotPassword.jsp?error=" + AuthService.ERR_SESSION_EXPIRED);
            return;
        }

        ServiceResult<Void> result = authService.resetPassword(newPassword, confirmPassword, now, new SessionAttributeStore(session));

        if (!result.isSuccess()) {
            if (AuthService.ERR_UNAUTHORIZED.equals(result.getMessage())
                    || AuthService.ERR_UPDATE_FAILED.equals(result.getMessage())) {
                response.sendRedirect(request.getContextPath() + "/forgotPassword.jsp?error=" + result.getMessage());
            } else {
                response.sendRedirect(request.getContextPath() + "/resetPassword.jsp?error=" + result.getMessage());
            }
            return;
        }
        response.sendRedirect(request.getContextPath() + "/login.jsp?reset=success");
    }

    private static class SessionAttributeStore implements AttributeStore {
        private final HttpSession session;

        public SessionAttributeStore(HttpSession session) {
            this.session = session;
        }

        @Override
        public Object get(String key) {
            return session.getAttribute(key);
        }

        @Override
        public void set(String key, Object value) {
            session.setAttribute(key, value);
        }

        @Override
        public void remove(String key) {
            session.removeAttribute(key);
        }
    }
}
