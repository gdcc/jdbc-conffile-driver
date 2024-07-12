package io.gdcc.jdbc.conffile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

public class ConfFileDriver implements Driver {
    
    private static Driver registeredDriver;
    
    // TODO: the file type (here: toml) can be made more flexible if we want to add more file types later on
    private static final String urlPrefix = "jdbc:conffile:toml://";
    
    static {
        try {
            register();
        } catch (SQLException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
    
    public static synchronized void register() throws SQLException {
        if (isRegistered()) {
            throw new IllegalStateException("Driver is already registered. It can only be registered once.");
        }
        Driver registeredDriver = new ConfFileDriver();
        DriverManager.registerDriver(registeredDriver);
        ConfFileDriver.registeredDriver = registeredDriver;
    }
    
    public static boolean isRegistered() {
        return registeredDriver != null;
    }
    
    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        String[] urlParts = url.split(":");
        if (urlParts.length < 4 ||
            !urlParts[0].equalsIgnoreCase("jdbc") ||
            !urlParts[1].equalsIgnoreCase("conffile") ||
            // TODO: if we want more adapters, this needs to be changed
            !urlParts[2].equalsIgnoreCase("toml") ||
            !urlParts[3].startsWith("//")) {
            throw new SQLException("Invalid url: " + url);
        }
        
        String adapter = urlParts[2].toLowerCase();
        
        // Chop of the // from the start of the file path
        String dirPath = urlParts[3].substring(2);
        Path directory = Path.of(dirPath);
        
        if (!Files.isDirectory(directory) || !Files.isReadable(directory)) {
            throw new SQLException("Invalid or non-accessible directory: " + dirPath);
        }
        
        return new ConfFileConnection(directory, adapter);
    }
    
    @Override
    public boolean acceptsURL(String url) throws SQLException {
        return url != null && url.startsWith(urlPrefix);
    }
    
    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        return new DriverPropertyInfo[0];
    }
    
    @Override
    public int getMajorVersion() {
        return 1;
    }
    
    @Override
    public int getMinorVersion() {
        return 0;
    }
    
    @Override
    public boolean jdbcCompliant() {
        return true;
    }
    
    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return null;
    }
}
