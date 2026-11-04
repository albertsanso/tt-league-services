package org.cttelsamicsterrassa.data.pipeline.runtime.gateway;

import org.cttelsamicsterrassa.data.pipeline.runtime.config.PipelineOrchestratorProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** The two HTTP clients; each sends its service key as {@code X-API-Key} and never logs it. */
@Configuration(proxyBeanMethods = false)
public class HttpClientsConfiguration {

    public static final String API_KEY_HEADER = "X-API-Key";

    @Bean
    RestClient ingestRestClient(RestClient.Builder builder, PipelineOrchestratorProperties properties) {
        PipelineOrchestratorProperties.Ingest ingest = properties.ingest();
        return restClient(builder, ingest.baseUrl(), ingest.apiKey(), ingest.connectTimeout(),
                ingest.readTimeout());
    }

    @Bean
    RestClient platformRestClient(RestClient.Builder builder, PipelineOrchestratorProperties properties) {
        PipelineOrchestratorProperties.Platform platform = properties.platform();
        return restClient(builder, platform.baseUrl(), platform.apiKey(), platform.connectTimeout(),
                platform.readTimeout());
    }

    public static RestClient restClient(
            RestClient.Builder builder, URI baseUrl, String apiKey, Duration connectTimeout, Duration readTimeout) {
        HttpClient http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(readTimeout);
        return builder
                .baseUrl(baseUrl.toString())
                .defaultHeader(API_KEY_HEADER, apiKey)
                .requestFactory(factory)
                .build();
    }
}
