package io.gdcc.jdbc.conffile;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

class ConfFileDriverTest {
    
    ConfFileDriver testDriver = new ConfFileDriver();
    
    @Test
    void acceptsUrl() throws SQLException {
        Assertions.assertTrue(testDriver.acceptsURL("jdbc:conffile:toml://./test.toml"));
        Assertions.assertTrue(testDriver.acceptsURL("jdbc:conffile:toml:///etc/dataverse/test.toml"));
    }
    
}
