package dev.chinh.streamingservice.searchclient.config;

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenSearchConfig {

    @Bean
    public OpenSearchClient createClient() {

        HttpHost host = new HttpHost("http", "localhost", 9200);

        BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(
                new AuthScope(host),
                new UsernamePasswordCredentials("username", "password".toCharArray())
        );

        // Create the low-level Apache HTTP client transport
        OpenSearchTransport transport = ApacheHttpClient5TransportBuilder
                .builder(host)
//                .setHttpClientConfigCallback(httpClientBuilder ->
//                        httpClientBuilder.setDefaultCredentialsProvider(credsProvider)
//                )
                .build();

        return new OpenSearchClient(transport);
    }
}
