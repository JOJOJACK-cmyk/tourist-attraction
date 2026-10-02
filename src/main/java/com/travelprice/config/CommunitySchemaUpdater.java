package com.travelprice.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import javax.sql.DataSource;
import java.sql.DatabaseMetaData;

/** Makes the destination tag optional in databases created by the previous version. */
@Component
@Order(-100)
public class CommunitySchemaUpdater implements ApplicationRunner {
    private final DataSource dataSource;
    public CommunitySchemaUpdater(DataSource dataSource){this.dataSource=dataSource;}
    @Override public void run(ApplicationArguments args) throws Exception {
        try(var connection=dataSource.getConnection()) {
            var metadata=connection.getMetaData();
            if(!"MySQL".equals(metadata.getDatabaseProductName()))return;
            // Hibernate does not always detect changed values of native MySQL ENUM columns.
            try(var query=connection.prepareStatement("SELECT COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=? AND TABLE_NAME='community_post' AND COLUMN_NAME='kind'")) {
                query.setString(1,connection.getCatalog());
                try(var result=query.executeQuery()) {
                    if(result.next()) {
                        var columnType=result.getString(1);
                        if(columnType.startsWith("enum(") && !columnType.contains("'GENERAL'")) {
                            // Append the new value so every existing enum value and stored row survives.
                            var extendedType=columnType.substring(0,columnType.length()-1)+",'GENERAL')";
                            try(var statement=connection.createStatement()) {
                                statement.executeUpdate("ALTER TABLE community_post MODIFY COLUMN kind "+extendedType+" NOT NULL");
                            }
                        }
                    }
                }
            }
            try(var columns=metadata.getColumns(connection.getCatalog(),null,"community_post","destination_id")) {
                if(columns.next() && columns.getInt("NULLABLE")==DatabaseMetaData.columnNoNulls) {
                    try(var statement=connection.createStatement()) {
                        statement.executeUpdate("ALTER TABLE community_post MODIFY COLUMN destination_id BIGINT NULL");
                    }
                }
            }
        }
    }
}
