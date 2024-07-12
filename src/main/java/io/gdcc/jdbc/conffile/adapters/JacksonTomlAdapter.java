package io.gdcc.jdbc.conffile.adapters;

import com.fasterxml.jackson.dataformat.toml.TomlMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class JacksonTomlAdapter extends Adapter {
    
    JacksonTomlAdapter() {}
    
    JacksonTomlAdapter(Path directory, String topScope, String profile) throws IOException {
        super(directory, topScope, profile);
    }
    
    private Map data;
    
    @Override
    public List<String> validSuffixes() {
        return List.of("toml");
    }
    
    @Override
    public void load() throws IOException {
        // Inspired by https://stackoverflow.com/a/73725201
        final var tomlMapper = new TomlMapper();
        this.data = tomlMapper.readValue(this.file.toFile(), Map.class);
    }
    
    @Override
    public String readItem(String itemName) {
        return "";
    }
    
    @Override
    public Map<String, String> readAllItems() {
        
        System.out.println(data);
        
        return Map.of();
    }
    
    @Override
    public void close() throws Exception {
    
    }
}
