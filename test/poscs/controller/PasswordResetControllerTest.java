package poscs.controller;

import org.junit.Test;
import static org.junit.Assert.*;

public class PasswordResetControllerTest {
    @Test
    public void testInstantiate() {
        PasswordResetController controller = new PasswordResetController();
        assertNotNull(controller);
    }
}
