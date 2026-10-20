package com.sciencebot.pos.storage.internal.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

@Configuration
public class R2ClientConfig {

    private final R2StorageProperties properties;

    public R2ClientConfig(R2StorageProperties properties) {
        this.properties = properties;
    }

    @Bean
    public S3Client s3Client() {
        String accountId = properties.getAccountId();
        String accessKey = properties.getAccessKeyId();
        String secretKey = properties.getSecretAccessKey();

        if (accountId == null || accountId.isBlank()) {
            accountId = "local-account-id";
        }
        if (accessKey == null || accessKey.isBlank()) {
            accessKey = "local-access-key";
        }
        if (secretKey == null || secretKey.isBlank()) {
            secretKey = "local-secret-key";
        }

        String endpoint = String.format("https://%s.r2.cloudflarestorage.com", accountId);
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);

        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .region(Region.of("auto"))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                // Apache HttpClient (por defecto) falla el handshake TLS contra el edge de R2 en
                // algunos entornos; UrlConnectionHttpClient usa HttpsURLConnection del propio JDK
                // y si la completa. Ver exclusion de apache-client en pom.xml.
                .httpClient(UrlConnectionHttpClient.create())
                .build();
    }
}
