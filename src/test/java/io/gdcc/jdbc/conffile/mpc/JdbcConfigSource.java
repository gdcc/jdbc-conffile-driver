package io.gdcc.jdbc.conffile.mpc;

import org.eclipse.microprofile.config.spi.ConfigSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JdbcConfigSource implements ConfigSource {
    
    private static final Logger LOGGER = Logger.getLogger(JdbcConfigSource.class.getName());
    private static Connection connection = null;
    
    public JdbcConfigSource() throws SQLException {
        connection = DriverManager.getConnection("jdbc:conffile:toml://src/test/resources/configsource");
        connect();
    }
    
    @Override
    public Set<String> getPropertyNames() {
        connect();
        return getAllConfigValues().keySet();
    }
    
    @Override
    public String getValue(String key) {
        connect();
        return getConfigValue(key);
    }
    
    @Override
    public String getName() {
        return "jdbc";
    }
    
    private static final String table = "dataverse";
    private static final String keyColumn = "keys";
    private static final String valueColumn = "values";
    
    PreparedStatement selectOne = null;
    PreparedStatement selectAll = null;
    
    public void connect() {
        if (connection != null) {
            String queryOne = "select " + valueColumn + " from " + table + " where " + keyColumn + " = ?";
            String queryAll = "select " + keyColumn + ", " + valueColumn + " from " + table;
            try {
                this.selectOne = connection.prepareStatement(queryOne);
                this.selectAll = connection.prepareStatement(queryAll);
            } catch (SQLException ex) {
                LOGGER.log(Level.WARNING, ex.getLocalizedMessage(), ex);
            }
        }
    }
    
    // This code has been copied from https://github.com/payara/Payara/blob/master/nucleus/payara-modules/nucleus-microprofile/config-service/src/main/java/fish/payara/nucleus/microprofile/config/source/JDBCConfigSourceHelper.java
    public synchronized String getConfigValue(String propertyName) {
        if (selectOne != null) {
            try {
                selectOne.setString(1, propertyName);
                ResultSet resultSet = selectOne.executeQuery();
                if (resultSet.next()) {
                    return resultSet.getString(1);
                }
            } catch (SQLException ex) {
                LOGGER.log(Level.WARNING, "Error in config source SQL execution", ex);
            }
        }
        return null;
    }
    
    // This code has been copied from https://github.com/payara/Payara/blob/master/nucleus/payara-modules/nucleus-microprofile/config-service/src/main/java/fish/payara/nucleus/microprofile/config/source/JDBCConfigSourceHelper.java
    public synchronized Map<String, String> getAllConfigValues() {
        Map<String, String> result = new HashMap<>();
        if (selectAll != null) {
            try {
                ResultSet resultSet = selectAll.executeQuery();
                while (resultSet.next()) {
                    result.put(resultSet.getString(1), resultSet.getString(2));
                }
            } catch (SQLException ex) {
                LOGGER.log(Level.WARNING, "Error in config source SQL execution", ex);
            }
        }
        return result;
    }
    
}
