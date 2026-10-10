package poscs.controller;

import org.junit.Test;
import static org.junit.Assert.*;
import org.mockito.Mockito;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class LoginControllerTest {
    @Test
    public void testInstantiate() {
        LoginController controller = new LoginController();
        assertNotNull(controller);
    }
}
