package com.safjnest.database;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.safjnest.bot.model.BotSettings.R2Settings;
import com.safjnest.utils.HttpUtils;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CopyObjectResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.MetadataDirective;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

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

    R2(URI cdnBase, S3Client client) {
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

    public boolean canUpload() {
        return client != null;
    }

    public void upload(String bucket, String key, Path source, String contentType, Map<String, String> metadata) {
        upload(bucket, key, RequestBody.fromFile(source), contentType, metadata);
    }

    public void upload(String bucket, String key, byte[] source, String contentType) {
        upload(bucket, key, RequestBody.fromBytes(source), contentType, Map.of());
    }

    public void copyVerified(String bucket, String source, String target) {
        HeadObjectResponse original = head(bucket, source);
        if (original.contentLength() == null || original.contentLength() <= 0 || original.eTag() == null)
            throw new IllegalStateException("Invalid R2 object: " + source);

        HeadObjectResponse current = headIfPresent(bucket, target);
        if (current != null) {
            if (!Objects.equals(original.contentLength(), current.contentLength()) ||
                    (!original.eTag().equals(current.eTag()) && !sameContent(bucket, source, target)))
                throw new IllegalStateException("Different R2 object already exists at " + target);

            if (sameHeaders(original, current))
                return;
        }

        CopyObjectRequest request = CopyObjectRequest.builder()
            .sourceBucket(bucket).sourceKey(source).destinationBucket(bucket).destinationKey(target)
            .copySourceIfMatch(original.eTag()).metadataDirective(MetadataDirective.COPY).build();
        CopyObjectResponse copied = client.copyObject(request);
        HeadObjectResponse verified = head(bucket, target);

        String copiedTag = copied.copyObjectResult() == null ? null : copied.copyObjectResult().eTag();
        if (copiedTag == null || !copiedTag.equals(verified.eTag())
                || !Objects.equals(original.contentLength(), verified.contentLength()) || !sameHeaders(original, verified))
            throw new IllegalStateException("Could not verify R2 copy to " + target);
    }

    public void delete(String bucket, String key) {
        requireClient();
        client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    public void updateMetadata(String bucket, String key, Map<String, String> values) {
        HeadObjectResponse previous = head(bucket, key);
        Map<String, String> metadata = new HashMap<>(previous.metadata());
        metadata.putAll(values);
        if (metadata.equals(previous.metadata()))
            return;

        CopyObjectRequest.Builder request = CopyObjectRequest.builder()
            .sourceBucket(bucket).sourceKey(key).destinationBucket(bucket).destinationKey(key)
            .copySourceIfMatch(previous.eTag()).metadataDirective(MetadataDirective.REPLACE).metadata(metadata)
            .contentType(previous.contentType()).cacheControl(previous.cacheControl())
            .contentDisposition(previous.contentDisposition()).contentEncoding(previous.contentEncoding())
            .contentLanguage(previous.contentLanguage()).expires(previous.expires());
        if (previous.storageClass() != null)
            request.storageClass(previous.storageClassAsString());

        client.copyObject(request.build());
    }

    @Override
    public void close() {
        if (client != null)
            client.close();
    }

    /* -------------------------------------------------------------------------- */

    private HeadObjectResponse head(String bucket, String key) {
        requireClient();

        return client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
    }

    private HeadObjectResponse headIfPresent(String bucket, String key) {
        try {
            return head(bucket, key);
        } catch (S3Exception failure) {
            if (failure.statusCode() == 404)
                return null;

            throw failure;
        }
    }

    private boolean sameContent(String bucket, String source, String target) {
        GetObjectRequest first = GetObjectRequest.builder().bucket(bucket).key(source).build();
        GetObjectRequest second = GetObjectRequest.builder().bucket(bucket).key(target).build();

        try (InputStream original = client.getObject(first); InputStream copy = client.getObject(second)) {
            byte[] expected = new byte[64 * 1024];
            byte[] actual = new byte[expected.length];

            while (true) {
                int size = original.readNBytes(expected, 0, expected.length);
                int copied = copy.readNBytes(actual, 0, actual.length);
                if (size != copied || !Arrays.equals(expected, 0, size, actual, 0, copied))
                    return false;
                if (size < expected.length)
                    return true;
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Could not compare R2 objects", failure);
        }
    }

    private static boolean sameHeaders(HeadObjectResponse first, HeadObjectResponse second) {
        return first.metadata().equals(second.metadata())
            && Objects.equals(first.contentType(), second.contentType())
            && Objects.equals(first.cacheControl(), second.cacheControl())
            && Objects.equals(first.contentDisposition(), second.contentDisposition())
            && Objects.equals(first.contentEncoding(), second.contentEncoding())
            && Objects.equals(first.contentLanguage(), second.contentLanguage())
            && Objects.equals(first.expires(), second.expires());
    }

    private void upload(String bucket, String key, RequestBody source, String contentType, Map<String, String> metadata) {
        requireClient();

        PutObjectRequest request = PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType)
            .metadata(metadata).build();
        client.putObject(request, source);
    }

    private void requireClient() {
        if (client == null)
            throw new IllegalStateException("R2 upload credentials are missing in settings.r2");
    }

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
