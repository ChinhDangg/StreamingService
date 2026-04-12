package dev.chinh.streamingservice.filemanager.config;

import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.convert.*;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

import java.util.Collections;

@Configuration
public class MongoDBConfig {

    @Value("${MONGODB_USERNAME}")
    private String username;

    @Value("${MONGODB_PASSWORD}")
    private String password;

    @Value("${MONGODB_AUTH_SOURCE}")
    private String authSource;

    @Value("${MONGODB_HOST}")
    private String host;

    @Value("${MONGODB_PORT}")
    private int port;

    @Value("${MONGODB_DATABASE}")
    private String database;

    @Bean
    public MongoDatabaseFactory mongoDbFactory() {
        // 1. Create Credentials (handles special characters automatically)
        MongoCredential credential = MongoCredential.createCredential(
                username,
                authSource,
                password.toCharArray()
        );

        // 2. Configure Client Settings
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyToClusterSettings(builder ->
                        builder.hosts(Collections.singletonList(new ServerAddress(host, port))))
                .credential(credential)
                .build();

        // 3. Return a Factory that points to your specific database
        return new SimpleMongoClientDatabaseFactory(MongoClients.create(settings), database);
    }

    @Bean
    public MongoTransactionManager transactionManager(MongoDatabaseFactory dbFactory) {
        return new MongoTransactionManager(dbFactory);
    }

    @Bean
    public MappingMongoConverter mappingMongoConverter(MongoDatabaseFactory factory,
                                                       MongoMappingContext context,
                                                       MongoCustomConversions conversions) {
        DbRefResolver dbRefResolver = new DefaultDbRefResolver(factory);
        MappingMongoConverter converter = new MappingMongoConverter(dbRefResolver, context);
        converter.setCustomConversions(conversions);

        // Create a TypeMapper with a NULL type key
        // This tells Spring "Do not write any class information to the document"
        // Only use if not using polymorphism
        converter.setTypeMapper(new DefaultMongoTypeMapper(null));

        return converter;
    }

    @Bean
    @Primary
    public MongoTemplate mongoTemplate(MongoDatabaseFactory factory, MongoConverter converter) {
        // Fast, default template
        return new MongoTemplate(factory, converter);
    }

    @Bean
    public MongoTemplate safeWriteMongoTemplate(MongoDatabaseFactory factory, MongoConverter converter) {
        // Strict consistency template for state changes
        MongoTemplate template = new MongoTemplate(factory, converter);
        template.setWriteConcern(WriteConcern.MAJORITY);
        return template;
    }
}
