package io.gdcc.jdbc.conffile.lookup;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

class LookupTest {

    static Config config = ConfigProvider.getConfig();

    @BeforeAll
    static void checkSourceAvail() {
        List<String> actualList = StreamSupport
            .stream(config.getConfigSources().spliterator(), false)
            .map(source -> source.getName())
            .collect(Collectors.toList());
        Assumptions.assumeTrue(actualList.contains("jdbc"));
    }
    
    @Test
    void testSingleValue() {
        String sut = config.getValue("title", String.class);
        Assertions.assertEquals("Test", sut);
    }
    
    @Test
    void testTableValue() {
        String sut = config.getValue("hello.attribute", String.class);
        Assertions.assertEquals("arbitrary", sut);
    }
    
    @Test
    void testGetAllProperties() {
        List<String> propertyNames = StreamSupport.stream(config.getPropertyNames().spliterator(), false).collect(Collectors.toList());
        Assertions.assertTrue(propertyNames.contains("products.0.sku"));
    }
    
    @Test
    void testProfiledSetting() {
        System.setProperty("mp.config.profile", "ct");
        String sut = config.getValue("withprofile", String.class);
        Assertions.assertEquals("testvalue", sut);
        System.clearProperty("mp.config.profile");
    }
    
}
