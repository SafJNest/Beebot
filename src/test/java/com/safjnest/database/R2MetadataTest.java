package com.safjnest.database;

import static org.junit.Assert.*;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CopyObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.MetadataDirective;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

public class R2MetadataTest {

    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void uploadAndCopyUseObjectMetadataAndPreserveContentHeaders() throws java.lang.Exception {
        AtomicReference<PutObjectRequest> put = new AtomicReference<>();
        AtomicReference<CopyObjectRequest> copy = new AtomicReference<>();
        S3Client client = (S3Client) Proxy.newProxyInstance(S3Client.class.getClassLoader(), new Class<?>[] {S3Client.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "putObject" -> {
                    put.set((PutObjectRequest) args[0]);
                    yield PutObjectResponse.builder().build();
                }
                case "headObject" -> HeadObjectResponse.builder().eTag("\"etag\"").contentType("audio/mpeg")
                    .cacheControl("public, max-age=3600").contentDisposition("inline").storageClass("STANDARD_IA")
                    .metadata(Map.of("duration-ms", "2088", "foreign-key", "retained")).build();
                case "copyObject" -> {
                    copy.set((CopyObjectRequest) args[0]);
                    yield CopyObjectResponse.builder().build();
                }
                default -> throw new AssertionError(method.getName());
            });

        R2 storage = new R2(URI.create("https://cdn.example/"), client);
        Path file = temporary.newFile("10.mp3").toPath();
        Files.writeString(file, "audio fixture");
        String key = "sounds/10.mp3";

        storage.upload("beebot", key, file, "audio/mpeg", Map.of("duration-ms", "2088"));
        assertEquals("2088", put.get().metadata().get("duration-ms"));
        assertEquals(key, put.get().key());

        storage.updateMetadata("beebot", key, Map.of("duration-ms", "2088"));
        assertNull(copy.get());

        storage.updateMetadata("beebot", key, Map.of("name-base64", "updated"));
        CopyObjectRequest request = copy.get();
        assertEquals(key, request.sourceKey());
        assertEquals(key, request.destinationKey());
        assertEquals("beebot", request.destinationBucket());
        assertEquals("\"etag\"", request.copySourceIfMatch());
        assertEquals(MetadataDirective.REPLACE, request.metadataDirective());
        assertEquals("audio/mpeg", request.contentType());
        assertEquals("public, max-age=3600", request.cacheControl());
        assertEquals("inline", request.contentDisposition());
        assertEquals("STANDARD_IA", request.storageClassAsString());
        assertEquals(Map.of("duration-ms", "2088", "foreign-key", "retained", "name-base64", "updated"), request.metadata());
    }
}
