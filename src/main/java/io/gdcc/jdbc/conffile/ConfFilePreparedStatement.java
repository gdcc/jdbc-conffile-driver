package io.gdcc.jdbc.conffile;

import io.gdcc.jdbc.conffile.adapters.Adapter;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.Date;
import java.sql.NClob;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ConfFilePreparedStatement implements PreparedStatement {
    
    private final String adapterType;
    private final String query;
    private final boolean singleValueQuery;
    private final Path directory;
    private final String tableName;
    private final String keyColumnLabel;
    private final String valueColumnLabel;
    private String profile = null;
    private final Map<Integer, String> queryParts = new HashMap<>();
    
    ConfFilePreparedStatement(Path directory, String adapterType, String sql) throws SQLException {
        String tableName;
        this.adapterType = adapterType;
        this.directory = directory;
        this.query = sql.trim();
        
        /* NOTE: we only need to address two types of queries:
         * queryOne = "select " + valueColumn + " from " + table + " where " + keyColumn + " = ?"
         *    --> The "?" is a placeholder and might contain a profile name as %profile.key.to.look.up
         * queryAll = "select " + keyColumn + ", " + valueColumn + " from " + table
         */
         
        if (!this.query.toLowerCase().trim().startsWith("select")) {
            throw new SQLFeatureNotSupportedException("Only SELECT statements are supported");
        }
        
        this.singleValueQuery = this.query.toLowerCase().contains("where");
        
        // Get the table name to determine the file name:
        // Cut of the select part
        tableName = this.query.substring(sql.toLowerCase().indexOf("from") + "from".length()).trim();
        // Cut of the where part if present
        if (tableName.toLowerCase().contains("where")) {
            tableName = tableName.substring(0, tableName.toLowerCase().indexOf("where")).trim();
        }
        this.tableName = tableName;
        
        // Extract the column names from the select statement
        if (this.singleValueQuery) {
            this.valueColumnLabel = this.query.substring("select".length(), sql.toLowerCase().indexOf("from")).trim();
            this.keyColumnLabel = "";
        } else {
            String[] columnNames = this.query.substring("select".length(), sql.toLowerCase().indexOf("from")).trim().split(",");
            this.keyColumnLabel = columnNames[0].trim();
            this.valueColumnLabel = columnNames[1].trim();
        }
    }
    
    @Override
    public ResultSet executeQuery() throws SQLException {
        if (this.singleValueQuery) {
            if (queryParts.size() != 1) {
                throw new SQLException("Not exactly 1 query parameter (the key to look up) given");
            }
            
            try (Adapter adapter = Adapter.Factory.create(this.adapterType, this.directory, this.tableName, this.profile)) {
                String value = adapter.readItem(queryParts.get(1));
                if (value != null) {
                    return new ConfFileResultSet(
                        List.of(List.of("", value)),
                        Map.of(this.valueColumnLabel, 1)
                    );
                }
            } catch (Exception e) {
                throw new SQLException(e);
            }
        // Receive all properties
        } else {
            // We need to scan for profile files ourselves, as we cannot determine the active profile at this point
            List<String> profiles;
            try {
                profiles = scanForProfiles(this.directory, this.tableName, this.adapterType);
            } catch (IOException e) {
                throw new SQLException(e);
            }
            
            // Now let's try to read those files and gather the properties
            List<List<String>> rows = new ArrayList<>();
            for (String profileName : profiles) {
                try (Adapter adapter = Adapter.Factory.create(this.adapterType, this.directory, this.tableName, profileName)) {
                    // Read data and transform map into query result
                    rows.addAll(adapter
                        .readAllItems()
                        .entrySet().stream()
                        .map(entry -> List.of("", entry.getKey(), entry.getValue()))
                        .collect(Collectors.toList()));
                    
                } catch (Exception e) {
                    throw new SQLException(e);
                }
            }
            return new ConfFileResultSet(
                rows,
                Map.of(this.keyColumnLabel, 1, this.valueColumnLabel, 2)
            );
        }
        // Nothing found - return empty result.
        return new ConfFileResultSet(List.of(), Map.of());
    }
    
    List<String> scanForProfiles(Path directory, String tableName, String adapterType) throws IOException {
        
        Predicate<Path> allowedSuffix = filename -> Adapter.Factory.suffixesForType(adapterType).stream()
            .anyMatch(suffix -> filename.getFileName().toString().toLowerCase().endsWith(suffix));
        
        try (Stream<Path> pathStream = Files.list(directory)) {
            return pathStream
                .filter(allowedSuffix)
                .filter(file -> file.getFileName().toString().startsWith(tableName))
                .map(path -> Adapter.Factory.extractProfileFromFilename(path.getFileName().toString(), tableName, adapterType))
                .collect(Collectors.toList());
        }
    }
    
    @Override
    public void setString(int parameterIndex, String parameter) throws SQLException {
        if (parameterIndex != 1) {
            throw new SQLException("The only valid parameter index is 1");
        }
        
        String sanitizedParameter = parameter.trim();
        
        // Extract profile, remove profile from lookup key (the profile file will not contain it!)
        if (parameter.startsWith("%")) {
            if (parameter.contains(".")) {
                this.profile = parameter.substring(1, parameter.indexOf("."));
                sanitizedParameter = parameter.substring(parameter.indexOf(".") + 1);
            } else {
                throw new SQLException("Invalid lookup key: contains a profile, but no separating dot");
            }
        }
        
        queryParts.put(parameterIndex, sanitizedParameter);
    }
    
    @Override
    public ResultSet executeQuery(String sql) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public void setNull(int parameterIndex, int sqlType) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public void setBoolean(int parameterIndex, boolean x) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public void setByte(int parameterIndex, byte x) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public void setShort(int parameterIndex, short x) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public void setInt(int parameterIndex, int x) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public void setLong(int parameterIndex, long x) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public void setFloat(int parameterIndex, float x) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public void setDouble(int parameterIndex, double x) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public void setBigDecimal(int parameterIndex, BigDecimal x) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public void setBytes(int parameterIndex, byte[] x) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public void setDate(int parameterIndex, Date x) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public void setTime(int parameterIndex, Time x) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public void setTimestamp(int parameterIndex, Timestamp x) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public void setAsciiStream(int parameterIndex, InputStream x, int length) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public void setUnicodeStream(int parameterIndex, InputStream x, int length) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public void setBinaryStream(int parameterIndex, InputStream x, int length) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public void clearParameters() throws SQLException {
    
    }
    
    @Override
    public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public void setObject(int parameterIndex, Object x) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public boolean execute() throws SQLException {
        return false;
    }
    
    @Override
    public void addBatch() throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public void setCharacterStream(int parameterIndex, Reader reader, int length) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public void setRef(int parameterIndex, Ref x) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public void setBlob(int parameterIndex, Blob x) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public void setClob(int parameterIndex, Clob x) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public void setArray(int parameterIndex, Array x) throws SQLException {
    
    }
    
    @Override
    public ResultSetMetaData getMetaData() throws SQLException {
        return null;
    }
    
    @Override
    public void setDate(int parameterIndex, Date x, Calendar cal) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public void setTime(int parameterIndex, Time x, Calendar cal) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public void setTimestamp(int parameterIndex, Timestamp x, Calendar cal) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public void setNull(int parameterIndex, int sqlType, String typeName) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public void setURL(int parameterIndex, URL x) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public ParameterMetaData getParameterMetaData() throws SQLException {
        return null;
    }
    
    @Override
    public void setRowId(int parameterIndex, RowId x) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public void setNString(int parameterIndex, String value) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public void setNCharacterStream(int parameterIndex, Reader value, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public void setNClob(int parameterIndex, NClob value) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public void setClob(int parameterIndex, Reader reader, long length) throws SQLException {
    
    }
    
    @Override
    public void setBlob(int parameterIndex, InputStream inputStream, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public void setNClob(int parameterIndex, Reader reader, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public void setSQLXML(int parameterIndex, SQLXML xmlObject) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public void setObject(int parameterIndex, Object x, int targetSqlType, int scaleOrLength) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public void setAsciiStream(int parameterIndex, InputStream x, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public void setBinaryStream(int parameterIndex, InputStream x, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public void setCharacterStream(int parameterIndex, Reader reader, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public void setAsciiStream(int parameterIndex, InputStream x) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public void setBinaryStream(int parameterIndex, InputStream x) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public void setCharacterStream(int parameterIndex, Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public void setNCharacterStream(int parameterIndex, Reader value) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public void setClob(int parameterIndex, Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public void setBlob(int parameterIndex, InputStream inputStream) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public void setNClob(int parameterIndex, Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public int executeUpdate() throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public int executeUpdate(String sql) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public void close() throws SQLException {
    
    }
    
    @Override
    public int getMaxFieldSize() throws SQLException {
        return 0;
    }
    
    @Override
    public void setMaxFieldSize(int max) throws SQLException {
    
    }
    
    @Override
    public int getMaxRows() throws SQLException {
        return 0;
    }
    
    @Override
    public void setMaxRows(int max) throws SQLException {
    
    }
    
    @Override
    public void setEscapeProcessing(boolean enable) throws SQLException {
    
    }
    
    @Override
    public int getQueryTimeout() throws SQLException {
        return 0;
    }
    
    @Override
    public void setQueryTimeout(int seconds) throws SQLException {
    
    }
    
    @Override
    public void cancel() throws SQLException {
    
    }
    
    @Override
    public SQLWarning getWarnings() throws SQLException {
        return null;
    }
    
    @Override
    public void clearWarnings() throws SQLException {
    
    }
    
    @Override
    public void setCursorName(String name) throws SQLException {
    
    }
    
    @Override
    public boolean execute(String sql) throws SQLException {
        return false;
    }
    
    @Override
    public ResultSet getResultSet() throws SQLException {
        return null;
    }
    
    @Override
    public int getUpdateCount() throws SQLException {
        return 0;
    }
    
    @Override
    public boolean getMoreResults() throws SQLException {
        return false;
    }
    
    @Override
    public void setFetchDirection(int direction) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public int getFetchDirection() throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public void setFetchSize(int rows) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public int getFetchSize() throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public int getResultSetConcurrency() throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public int getResultSetType() throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public void addBatch(String sql) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public void clearBatch() throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public int[] executeBatch() throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public Connection getConnection() throws SQLException {
        return null;
    }
    
    @Override
    public boolean getMoreResults(int current) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public ResultSet getGeneratedKeys() throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public int executeUpdate(String sql, String[] columnNames) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public boolean execute(String sql, int[] columnIndexes) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public boolean execute(String sql, String[] columnNames) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public int getResultSetHoldability() throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public boolean isClosed() throws SQLException {
        return false;
    }
    
    @Override
    public void setPoolable(boolean poolable) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public boolean isPoolable() throws SQLException {
        return false;
    }
    
    @Override
    public void closeOnCompletion() throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public boolean isCloseOnCompletion() throws SQLException {
        return false;
    }
    
    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        throw new SQLFeatureNotSupportedException("This method is not supported");
    }
    
    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return false;
    }
}
