package com.altinity.clickhouse.debezium.embedded.cdc;

import com.altinity.clickhouse.sink.connector.ClickHouseSinkConnectorConfig;
import com.altinity.clickhouse.sink.connector.db.BaseDbWriter;
import com.altinity.clickhouse.sink.connector.model.DBCredentials;
import com.fasterxml.jackson.databind.annotation.JsonAppend;
import io.debezium.storage.jdbc.offset.JdbcOffsetBackingStoreConfig;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Properties;
import java.util.UUID;

public class DebeziumOffsetStorage {

    public String getOffsetKey(Properties props) {
        String connectorName = props.getProperty("name");
        return String.format("[\"%s\",{\"server\":\"embeddedconnector\"}]", connectorName);

    }
    public void deleteOffsetStorageRow(String offsetKey,
                                       Properties props,
                                       BaseDbWriter writer) throws SQLException {

        // String databaseName = p
        String tableName = props.getProperty(JdbcOffsetBackingStoreConfig.OFFSET_STORAGE_PREFIX +
                JdbcOffsetBackingStoreConfig.PROP_TABLE_NAME.name());

        // String connectorName = config.getString("connector.name");
        String debeziumStorageStatusQuery = String.format("delete from %s where offset_key='%s'" , tableName, offsetKey);
        writer.executeQuery(debeziumStorageStatusQuery);
    }

    public String getDebeziumStorageStatusQuery(
                                                Properties props, BaseDbWriter writer) throws SQLException {
        String tableName = props.getProperty(JdbcOffsetBackingStoreConfig.OFFSET_STORAGE_PREFIX +
                JdbcOffsetBackingStoreConfig.PROP_TABLE_NAME.name());

        String offsetKey = getOffsetKey(props);
        // String connectorName = config.getString("connector.name");
        String debeziumStorageStatusQuery = String.format("select offset_val from %s where offset_key='%s'" , tableName, offsetKey);
        return writer.executeQuery(debeziumStorageStatusQuery);
    }

    /**
     *  {"transaction_id":null,"ts_sec":1687278006,"file":"mysql-bin.000003","pos":1156385,"gtids":"30fd82c7-0f86-11ee-9e3b-0242c0a86002:1-2442","row":1,"server_id":266,"event":2}
     *
     * @param record
     * @return
     * @throws ParseException
     */
    public String updateBinLogInformation(String record, String binLogFile, String binLogPosition, String gtids) throws ParseException {
        JSONObject jsonObject = new JSONObject();
        if(record != null || !record.isEmpty()) {
            jsonObject = (JSONObject) new JSONParser().parse(record);
        } else {
            jsonObject.put("ts_sec", System.currentTimeMillis() / 1000);
            jsonObject.put("transaction_id", null);
        }

        jsonObject.put("file", binLogFile);
        jsonObject.put("pos", binLogPosition);
        if(gtids != null) {
            jsonObject.put("gtids", gtids);
        }

        return jsonObject.toJSONString();
    }

    public void updateDebeziumStorageRow(BaseDbWriter writer, String tableName, String offsetKey, String offsetVal,
                                         long currentTs) throws SQLException {

        try(PreparedStatement sql = writer.getConnection().prepareStatement(String.format(JdbcOffsetBackingStoreConfig.DEFAULT_TABLE_INSERT, tableName))) {

            sql.setString(1, UUID.randomUUID().toString());
            sql.setString(2, offsetKey);
            sql.setString(3, offsetVal);
            sql.setTimestamp(4, new Timestamp(currentTs));
            sql.setInt(5, 1);
            sql.executeUpdate();
        }
    }

}