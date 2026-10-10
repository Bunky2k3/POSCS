package poscs.controller;

import java.io.IOException;
import java.sql.Date;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import poscs.common.FileStorage;
import poscs.dao.AddressDAO;
import poscs.dao.EmployeeDAO;
import poscs.model.User;
import poscs.service.ProfileService;
import poscs.service.ServiceResult;

@WebServlet(name = "ProfileController", urlPatterns = {
    "/viewProfile", "/updateProfile", "/UpdateProfileServlet"
})
@MultipartConfig
public class ProfileController extends HttpServlet {

    private static final Logger LOG = LoggerFactory.getLogger(ProfileController.class);

    private final EmployeeDAO employeeDAO = new EmployeeDAO();
    private final AddressDAO addressDAO = new AddressDAO();
    private final ProfileService profileService = new ProfileService();

    private static final String AVATAR_SUBFOLDER = "avatars";
    private static final DateTimeFormatter DATE_DISPLAY_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String path = request.getServletPath();
        if ("/viewProfile".equals(path)) {
            handleViewProfile(request, response);
        } else if ("/updateProfile".equals(path)) {
            handleEditProfileForm(request, response);
        } else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if ("/UpdateProfileServlet".equals(request.getServletPath())) {
            handleUpdateProfile(request, response);
        } else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    private void handleViewProfile(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        User currentUser = session != null ? (User) session.getAttribute("currentUser") : null;

        if (currentUser == null) {
            response.sendRedirect(request.getContextPath() + "/login.jsp");
            return;
        }

        User profile = employeeDAO.findProfileById(currentUser.getUserId());
        if (profile == null) {
            response.sendRedirect(request.getContextPath() + "/login.jsp");
            return;
        }

        request.setAttribute("profile", profile);
        request.setAttribute("hireDateText", formatDate(profile.getHireDate()));
        request.setAttribute("dateOfBirthText", formatDate(profile.getDateOfBirth()));
        request.getRequestDispatcher("/viewProfile.jsp").forward(request, response);
    }

    private String formatDate(Date date) {
        return date != null ? date.toLocalDate().format(DATE_DISPLAY_FORMAT) : null;
    }

    private void handleEditProfileForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        User currentUser = session != null ? (User) session.getAttribute("currentUser") : null;
        if (currentUser == null) {
            response.sendRedirect(request.getContextPath() + "/login.jsp");
            return;
        }

        User profile = employeeDAO.findProfileById(currentUser.getUserId());
        if (profile == null) {
            response.sendRedirect(request.getContextPath() + "/login.jsp");
            return;
        }

        request.setAttribute("profile", profile);
        request.setAttribute("hireDateText", formatDate(profile.getHireDate()));
        request.setAttribute("missingFields", String.join(", ", missingRequiredProfileFields(profile)));
        request.setAttribute("provinceList", addressDAO.findAllProvinces());
        request.getRequestDispatcher("/updateProfile.jsp").forward(request, response);
    }

    static List<String> missingRequiredProfileFields(User profile) {
        List<String> missing = new ArrayList<>();
        if (isBlank(profile.getLastName()) || isBlank(profile.getFirstName())) {
            missing.add("họ tên");
        }
        if (isBlank(profile.getGender())) {
            missing.add("giới tính");
        }
        if (profile.getDateOfBirth() == null) {
            missing.add("ngày sinh");
        }
        if (isBlank(profile.getCitizenId())) {
            missing.add("số CCCD/CMND");
        }
        if (isBlank(profile.getPhone())) {
            missing.add("số điện thoại");
        }
        if (isBlank(profile.getPersonalEmail())) {
            missing.add("email cá nhân");
        }
        if (profile.getAddress() == null || profile.getAddress().getDistrictId() <= 0) {
            missing.add("địa chỉ (tỉnh / thành phố và xã / phường)");
        }
        return missing;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void handleUpdateProfile(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        User currentUser = session != null ? (User) session.getAttribute("currentUser") : null;
        if (currentUser == null) {
            response.sendRedirect(request.getContextPath() + "/login.jsp");
            return;
        }

        String lastName = trimToNull(request.getParameter("lastName"));
        String firstName = trimToNull(request.getParameter("firstName"));
        String citizenId = trimToNull(request.getParameter("citizenId"));
        String phone = trimToNull(request.getParameter("phone"));
        String personalEmail = trimToNull(request.getParameter("personalEmail"));
        String middleName = trimToNull(request.getParameter("middleName"));
        String addressDetail = trimToNull(request.getParameter("addressDetail"));
        String gender = request.getParameter("gender");
        String dobStr = request.getParameter("dob");
        Integer districtId = parseIntOrNull(request.getParameter("districtId"));

        jakarta.servlet.http.Part avatarPart;
        try {
            avatarPart = request.getPart("avatar");
        } catch (Exception e) {
            response.sendRedirect(request.getContextPath() + "/updateProfile?error=invalid_image_type");
            return;
        }

        if (!FileStorage.isAcceptable(avatarPart, FileStorage.IMAGE_EXTENSIONS)) {
            response.sendRedirect(request.getContextPath() + "/updateProfile?error=invalid_image_type");
            return;
        }

        User currentProfile = employeeDAO.findProfileById(currentUser.getUserId());
        String newAvatarUrl = FileStorage.save(avatarPart, AVATAR_SUBFOLDER, FileStorage.IMAGE_EXTENSIONS);
        if (newAvatarUrl == null && currentProfile != null) {
            newAvatarUrl = currentProfile.getAvatarUrl();
        }

        ServiceResult<User> result = profileService.updateProfile(currentUser, lastName, middleName, firstName,
                citizenId, phone, personalEmail, gender, dobStr, districtId, addressDetail, newAvatarUrl);

        if (!result.isSuccess()) {
            response.sendRedirect(request.getContextPath() + "/updateProfile?error=" + result.getMessage());
            return;
        }
        
        currentUser.setAvatarUrl(result.getData().getAvatarUrl());
        response.sendRedirect(request.getContextPath() + "/viewProfile");
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Integer parseIntOrNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
