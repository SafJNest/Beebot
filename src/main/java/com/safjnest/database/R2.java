package com.safjnest.database;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import com.safjnest.bot.model.BotSettings.R2Settings;
import com.safjnest.utils.HttpUtils;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

public final class R2 implements AutoCloseable {

    private static final Duration UPLOAD_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofSeconds(20);
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private static volatile R2 instance = new R2(null);

    private final URI cdnBase;
    private final S3Client client;

    public R2(URI cdnBase) {
        this(cdnBase, null);
    }

    private R2(URI cdnBase, S3Client client) {
        this.cdnBase = cdnBase;
        this.client = client;
    }

    public static void configure(R2Settings settings) {
        if (settings == null || !settings.enabled())
            return;

        URI cdnBase = cdnBase(settings.cdnUrl());
        instance = new R2(cdnBase, uploadClient(settings));
    }

    public static R2 getInstance() {
        return instance;
    }

    public String url(String key) {
        return cdnBase == null ? null : cdnBase.resolve(key).toString();
    }

    public void download(String key, Path target) throws IOException {
        HttpRequest request = HttpUtils.getRequest(url(key), Map.of(), DOWNLOAD_TIMEOUT);
        try {
            HttpResponse<Path> response = HTTP.send(request, HttpResponse.BodyHandlers.ofFile(target));
            if (response.statusCode() != 200)
                throw new IOException("Sound CDN returned HTTP " + response.statusCode());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted during CDN download", exception);
        }
    }

    public boolean isEnabled() {
        return cdnBase != null;
    }

    public void upload(String bucket, String key, Path source, String contentType) {
        if (client == null)
            throw new IllegalStateException("R2 upload credentials are missing in settings.r2");

        PutObjectRequest request = PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build();
        client.putObject(request, RequestBody.fromFile(source));
    }

    @Override
    public void close() {
        if (client != null)
            client.close();
    }

    /* -------------------------------------------------------------------------- */

    private static S3Client uploadClient(R2Settings settings) {
        if (!hasValue(settings.accountId()) && !hasValue(settings.accessKeyId()) && !hasValue(settings.secretAccessKey()))
            return null;
        if (!hasValue(settings.soundBucket()))
            throw new IllegalArgumentException("settings.r2.soundBucket is required for uploads");

        String accountId = settings.accountId();
        if (accountId == null || !accountId.matches("[a-fA-F0-9]{32}"))
            throw new IllegalArgumentException("settings.r2.accountId must be a Cloudflare account ID");
        if (settings.accessKeyId() == null || settings.accessKeyId().isBlank()
                || settings.secretAccessKey() == null || settings.secretAccessKey().isBlank())
            throw new IllegalArgumentException("settings.r2 requires accessKeyId and secretAccessKey");

        URI endpoint = URI.create("https://" + accountId + ".r2.cloudflarestorage.com");
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
            AwsBasicCredentials.create(settings.accessKeyId(), settings.secretAccessKey()));
        S3Configuration s3 = S3Configuration.builder().pathStyleAccessEnabled(true)
            .chunkedEncodingEnabled(false).checksumValidationEnabled(false).build();

        return S3Client.builder().endpointOverride(endpoint).region(Region.of("auto"))
            .credentialsProvider(credentials).serviceConfiguration(s3)
            .overrideConfiguration(ClientOverrideConfiguration.builder().apiCallTimeout(UPLOAD_TIMEOUT).build())
            .build();
    }

    private static URI cdnBase(String value) {
        if (!hasValue(value))
            throw new IllegalArgumentException("settings.r2.cdnUrl is required when R2 is enabled");

        URI uri = URI.create(value.strip().replaceAll("/+$", "") + "/");
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null)
            throw new IllegalArgumentException("settings.r2.cdnUrl must be an HTTPS base URL");

        return uri;
    }

    private static boolean hasValue(String value) {
        return value != null && !value.isBlank();
    }
}
