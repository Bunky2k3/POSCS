package poscs.controller;

import org.junit.Test;
import static org.junit.Assert.*;

public class LogoutControllerTest {
    @Test
    public void testInstantiate() {
        LogoutController controller = new LogoutController();
        assertNotNull(controller);
    }
}
