package mss301.mediaservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
public class R2Config {

    @Bean
    public S3Configuration s3Configuration() {
        return S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .chunkedEncodingEnabled(false)
                .build();
    }

    @Bean
    public S3Client s3Client(MediaProperties mediaProperties, S3Configuration s3Configuration) {
        MediaProperties.R2 r2 = mediaProperties.getR2();
        StaticCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(r2.getAccessKeyId(), r2.getSecretAccessKey()));

        return S3Client.builder()
                .endpointOverride(URI.create(r2.getEndpoint()))
                .region(Region.of("auto"))
                .credentialsProvider(credentialsProvider)
                .serviceConfiguration(s3Configuration)
                .build();
    }

    @Bean
    public S3Presigner s3Presigner(MediaProperties mediaProperties, S3Configuration s3Configuration) {
        MediaProperties.R2 r2 = mediaProperties.getR2();
        StaticCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(r2.getAccessKeyId(), r2.getSecretAccessKey()));

        return S3Presigner.builder()
                .endpointOverride(URI.create(r2.getEndpoint()))
                .region(Region.of("auto"))
                .credentialsProvider(credentialsProvider)
                .serviceConfiguration(s3Configuration)
                .build();
    }
}
