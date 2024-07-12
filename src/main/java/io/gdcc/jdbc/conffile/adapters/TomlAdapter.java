package io.gdcc.jdbc.conffile.adapters;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.concurrent.StampedConfig;
import com.electronwill.nightconfig.core.file.FileConfig;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class TomlAdapter extends Adapter {
    
    private FileConfig fileConfig;
    
    TomlAdapter() {}
    
    TomlAdapter(Path directory, String topScope, String profile) throws IOException {
        super(directory, topScope, profile);
    }
    
    @Override
    public List<String> validSuffixes() {
        return List.of("toml");
    }
    
    @Override
    public void load() {
        this.fileConfig = FileConfig.of(this.file);
        this.fileConfig.load();
    }
    
    @Override
    public String readItem(String itemPath) {
        Object value = fileConfig.get(itemPath);
        if (value == null) {
            return null;
        }
        
        if (value instanceof List) {
            List list = (List) value;
            if (!list.isEmpty()) {
                Object innerValue = list.get(0);
                // If this is a complex object, lets unwrap it into a flattened form
                if (innerValue instanceof StampedConfig) {
                    throw new IllegalArgumentException("Retrieving a complex config object as a string is not supported");
                } else {
                    // If this is just a bunch of whatever, just add them as a string representation to the map
                    // (MPC will convert this for us)
                    return ((List<Object>) list).stream().map(String::valueOf).collect(Collectors.joining(","));
                }
            }
        } else if (value instanceof StampedConfig) {
            throw new IllegalArgumentException("Retrieving a complex config object as a string is not supported");
        }
        // If this is just a bunch of whatever, just add them as a string representation to the map
        // (MPC will convert this for us)
        return String.valueOf(value);
    }
    
    @Override
    public Map<String, String> readAllItems() {
        HashMap values = new HashMap();
        String key = this.profile == null ? "" : "%" + this.profile + ".";
        
        for (Config.Entry entry : fileConfig.entrySet()) {
            deepSearch(key + entry.getKey(), entry.getValue(), values);
        }
        return Collections.unmodifiableMap(values);
    }
    
    private void deepSearch(final String key, final Object value, final Map<String,String> flattenedValues) {
        Objects.requireNonNull(value, "value must not be null at key " + key);
        if (value instanceof List) {
            List list = (List) value;
            if (!list.isEmpty()) {
                Object innerValue = list.get(0);
                // If this is a complex object, lets unwrap it into a flattened form
                if (innerValue instanceof StampedConfig) {
                    for (int i = 0; i < list.size(); i++) {
                        deepSearch(key + "." + i, list.get(i), flattenedValues);
                    }
                } else {
                    // If this is just a bunch of whatever, just add them as a string representation to the map
                    // (MPC will convert this for us)
                    flattenedValues.putIfAbsent(key, ((List<Object>) list).stream().map(String::valueOf).collect(Collectors.joining(",")));
                }
            }
        } else if (value instanceof StampedConfig) {
            for (Config.Entry subValue : ((StampedConfig) value).entrySet()) {
                String subKey = key + "." + subValue.getKey();
                deepSearch(subKey, subValue.getValue(), flattenedValues);
            }
        } else {
            // If this is just a bunch of whatever, just add them as a string representation to the map
            // (MPC will convert this for us)
            flattenedValues.putIfAbsent(key, String.valueOf(value));
        }
    }
    
    @Override
    public void close() throws Exception {
        fileConfig.close();
    }
}
