package io.gdcc.jdbc.conffile.adapters;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.List;
import java.util.Map;

public abstract class Adapter implements AutoCloseable {
    
    public static final class Factory {
        private Factory() {
            // Intentionally left blank - factory pattern here
        }
        
        public static Adapter create(String type, Path directory, String basename, String profile) throws IOException, SQLException {
            if (type.equals("toml")) {
                return new TomlAdapter(directory, basename, profile);
            }
            throw new SQLFeatureNotSupportedException("Unsupported adapter type: " + type);
        }
        
        public static List<String> suffixesForType(String type) {
            if (type.equals("toml")) {
                return new TomlAdapter().validSuffixes();
            }
            return List.of();
        }
        
        public static String composePathAndBaseName(String path, String basename, String profile) {
            return path + File.separator + basename + (profile != null ? "-" + profile : "");
        }
        
        public static String extractProfileFromFilename(String filename, String basename, String adapterType) {
            List<String> suffixes = suffixesForType(adapterType);
            String noSuffix = "";
            for (String suffix : suffixes) {
                if (filename.endsWith(suffix)) {
                    noSuffix = filename.substring(0, filename.length() - ("." + suffix).length());
                }
            }
            
            String profile = noSuffix.substring(basename.length());
            return profile.isEmpty() ? null : profile.substring(1); // chop of the "-"
        }
    }
    
    protected final String pathAndBasename;
    protected final Path file;
    protected final String profile;
    
    Adapter() {
        this.pathAndBasename = null;
        this.file = null;
        this.profile = null;
    }
    
    Adapter(Path directory, String basename, String profile) throws IOException {
        this.pathAndBasename = Factory.composePathAndBaseName(directory.toAbsolutePath().toString(), basename, profile);
        this.file = validate(this.pathAndBasename);
        this.profile = profile;
        load();
    }
    
    public Path validate(String pathAndBasename) throws IOException {
        for (String suffix : validSuffixes()) {
            Path candidate = Path.of(pathAndBasename + "." + suffix);
            if (Files.isRegularFile(candidate) && Files.isReadable(candidate)) {
                return candidate;
            }
        }
        // No file found? Error out!
        throw new FileNotFoundException("Cannot find or read a file " + pathAndBasename + "." +
            (validSuffixes().size() > 1 ? "{" + String.join(",", validSuffixes()) + "}" : validSuffixes().get(0))
        );
    }
    
    public abstract List<String> validSuffixes();
    public abstract void load() throws IOException;
    public abstract String readItem(String itemName);
    public abstract Map<String,String> readAllItems();
    
}
