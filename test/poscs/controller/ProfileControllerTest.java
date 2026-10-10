package poscs.controller;

import org.junit.Test;
import static org.junit.Assert.*;

public class ProfileControllerTest {
    @Test
    public void testInstantiate() {
        ProfileController controller = new ProfileController();
        assertNotNull(controller);
    }
}
