package dbfit.environment;

import dbfit.annotations.DatabaseEnvironment;
import dbfit.api.AbstractDbEnvironment;
import dbfit.util.DbParameterAccessor;
import dbfit.util.Direction;
import dbfit.util.NameNormaliser;

import javax.sql.RowSet;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Pattern;

@DatabaseEnvironment(name="Hive", driver="org.apache.hive.jdbc.HiveDriver")
public class HiveEnvironment extends AbstractDbEnvironment {
    public HiveEnvironment(String driverClassName) {
        super(driverClassName);
    }

    public boolean supportsOuputOnInsert() {
        return false;
    }

    protected String getConnectionString(String dataSource) {
        return "jdbc:hive2://" + dataSource;
    }

    protected String getConnectionString(String dataSource, String database) {
        return "jdbc:hive2://" + dataSource + "/" + database;
    }

    private static String paramNamePattern = "@([A-Za-z0-9_]+)";
    private static Pattern paramRegex = Pattern.compile(paramNamePattern);

    public Pattern getParameterPattern() {
        return paramRegex;
    }

    // Hive jdbc driver does not support named parameters - so just map them
    // to standard jdbc question marks
    // TODO: Is this really the case?
    protected String parseCommandText(String commandText) {
        commandText = commandText.replaceAll(paramNamePattern, "?");
        return super.parseCommandText(commandText);
    }

    public Map<String, DbParameterAccessor> getAllColumns(String tableOrViewName)
            throws SQLException {
        String qry = "describe " + tableOrViewName;
        return readColumnsFromDb(qry);
    }

    private Map<String, DbParameterAccessor> readColumnsFromDb(String query) throws SQLException {
        try (PreparedStatement dc = currentConnection.prepareStatement(query)) {
            ResultSet rs = dc.executeQuery();
            Map<String, DbParameterAccessor> columns = new HashMap<String, DbParameterAccessor>();
            int position = 0;
            while (rs.next()) {
                String columnName = rs.getString(1);
                if (columnName == null)
                    columnName = "";
                String dataType = rs.getString(2);
                DbParameterAccessor dbp = new DbParameterAccessor(columnName,
                        Direction.INPUT, getSqlType(dataType),
                        getJavaClass(dataType), position++);
                columns.put(NameNormaliser.normaliseName(columnName), dbp);
            }
            rs.close();
            return columns;
        }
    }

    // List interface has sequential search, so using list instead of array to
    // map types

    // via https://cwiki.apache.org/confluence/display/Hive/LanguageManual+Types#LanguageManualTypes-Overview
    private static List<String> stringTypes = Arrays.asList(new String[] {"STRING", "VARCHAR", "CHAR" });
    private static List<String> intTypes = Arrays.asList(new String[] {"TINYINT", "SMALLINT", "INT", "INTEGER" });
    private static List<String> longTypes = Arrays.asList("BIGINT");
    private static List<String> floatTypes = Arrays.asList("FLOAT");
    private static List<String> doubleTypes = Arrays.asList("DOUBLE");
    private static List<String> decimalTypes = Arrays.asList("DECIMAL");
    private static List<String> dateTypes = Arrays.asList("DATE");
    private static List<String> timestampTypes = Arrays.asList("TIMESTAMP");
    private static List<String> timeTypes = Arrays.asList(new String[]{});
    private static List<String> refCursorTypes = Arrays.asList(new String[] {});
    private static List<String> boolTypes = Arrays.asList("BOOLEAN");
    private static List<String> binaryTypes = Arrays.asList("BINARY");

    private static List<String> arrayTypes = Arrays.asList("ARRAY"); // "ARRAY<X>" ???
    private static List<String> mapTypes = Arrays.asList("MAP");
    private static List<String> structTypes = Arrays.asList("STRUCT");
    private static List<String> unionTypes = Arrays.asList("UNIONTYPE");

    private static String normaliseTypeName(String dataType) {
        if (dataType != null) {
            dataType = dataType.toUpperCase().trim();
        }
        return dataType;
    }

    private static int getSqlType(String dataType) {
        dataType = normaliseTypeName(dataType);

        if (dataType == null)
            return java.sql.Types.NULL;
        if (stringTypes.contains(dataType))
            return java.sql.Types.VARCHAR;
        if (decimalTypes.contains(dataType))
            return java.sql.Types.NUMERIC;
        if (intTypes.contains(dataType))
            return java.sql.Types.INTEGER;
        if (floatTypes.contains(dataType))
            return java.sql.Types.FLOAT;
        if (doubleTypes.contains(dataType))
            return java.sql.Types.DOUBLE;
        if (longTypes.contains(dataType))
            return java.sql.Types.BIGINT;
        if (timestampTypes.contains(dataType))
            return java.sql.Types.TIMESTAMP;
        if (dateTypes.contains(dataType))
            return java.sql.Types.DATE;
        if (timeTypes.contains(dataType))
            return java.sql.Types.TIME;
        if (refCursorTypes.contains(dataType))
            return java.sql.Types.REF;
        if (boolTypes.contains(dataType))
            return java.sql.Types.BOOLEAN;
        if (binaryTypes.contains(dataType))
            return java.sql.Types.BINARY;
        if (arrayTypes.contains(dataType))
            return java.sql.Types.ARRAY;
        if (mapTypes.contains(dataType))
            return java.sql.Types.OTHER; // ???
        if (structTypes.contains(dataType))
            return java.sql.Types.STRUCT;
        if (unionTypes.contains(dataType))
            return java.sql.Types.OTHER; // ???
        throw new UnsupportedOperationException("Type " + dataType
                + " is not supported");
    }

    public Class<?> getJavaClass(String dataType) {
        dataType = normaliseTypeName(dataType);

        if (dataType == null)
            throw new UnsupportedOperationException("Null type is not supported");

        if (stringTypes.contains(dataType))
            return String.class;
        if (decimalTypes.contains(dataType))
            return BigDecimal.class;
        if (intTypes.contains(dataType))
            return Integer.class;
        if (floatTypes.contains(dataType))
            return Float.class;
        if (dateTypes.contains(dataType))
            return java.sql.Date.class;
        if (refCursorTypes.contains(dataType))
            return RowSet.class;
        if (doubleTypes.contains(dataType))
            return Double.class;
        if (longTypes.contains(dataType))
            return Long.class;
        if (timestampTypes.contains(dataType))
            return java.sql.Timestamp.class;
        if (timeTypes.contains(dataType))
            return java.sql.Time.class;
        if (boolTypes.contains(dataType))
            return Boolean.class;
        //if (binaryTypes.contains(dataType))
        //    return java.sql.Binary.class;
        if (arrayTypes.contains(dataType))
            return java.sql.Array.class;
        if (mapTypes.contains(dataType))
            return Map.class;
        if (structTypes.contains(dataType))
            return java.sql.Struct.class;
        //if (unionTypes.contains(dataType))
        //    return java.sql.Types.OTHER; // ???
        throw new UnsupportedOperationException("Type " + dataType
                + " is not supported");
    }

    public Map<String, DbParameterAccessor> getAllProcedureParameters(
            String procName) throws SQLException {

        return null;
    }
}

