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
import software.amazon.awssdk.services.s3.model.CopyObjectResult;
import software.amazon.awssdk.services.s3.model.CopyObjectResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.MetadataDirective;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

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
    @Test
    public void migrationCopiesWithSourceConditionAndReusesVerifiedDestination() {
        String source = "sounds/1.mp3";
        String target = "sounds/1/1.mp3";
        HeadObjectResponse original = HeadObjectResponse.builder().contentLength(8L).eTag("\"audio\"")
            .contentType("audio/mpeg").metadata(Map.of("duration-ms", "2088")).build();
        AtomicReference<HeadObjectResponse> destination = new AtomicReference<>();
        AtomicReference<CopyObjectRequest> request = new AtomicReference<>();
        AtomicReference<DeleteObjectRequest> deleted = new AtomicReference<>();

        S3Client client = (S3Client) Proxy.newProxyInstance(S3Client.class.getClassLoader(), new Class<?>[] {S3Client.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "headObject" -> {
                    String key = ((HeadObjectRequest) args[0]).key();
                    if (source.equals(key) && deleted.get() == null)
                        yield original;
                    if (source.equals(key) || destination.get() == null)
                        throw S3Exception.builder().statusCode(404).build();

                    yield destination.get();
                }
                case "copyObject" -> {
                    request.set((CopyObjectRequest) args[0]);
                    destination.set(original);

                    yield CopyObjectResponse.builder().copyObjectResult(CopyObjectResult.builder().eTag(original.eTag()).build()).build();
                }
                case "deleteObject" -> {
                    deleted.set((DeleteObjectRequest) args[0]);

                    yield DeleteObjectResponse.builder().build();
                }
                default -> throw new AssertionError(method.getName());
            });
        R2 storage = new R2(URI.create("https://cdn.example/"), client);

        assertThrows(S3Exception.class, () -> storage.deleteVerifiedDuplicate("beebot", source, target));
        assertNull(deleted.get());
        storage.copyVerified("beebot", source, target);
        assertEquals(source, request.get().sourceKey());
        assertEquals(target, request.get().destinationKey());
        assertEquals(original.eTag(), request.get().copySourceIfMatch());
        assertEquals(MetadataDirective.COPY, request.get().metadataDirective());

        request.set(null);
        storage.copyVerified("beebot", source, target);
        assertNull(request.get());

        destination.set(original.toBuilder().contentLength(9L).build());
        assertThrows(IllegalStateException.class, () -> storage.copyVerified("beebot", source, target));
        assertNull(request.get());
        assertThrows(IllegalStateException.class, () -> storage.deleteVerifiedDuplicate("beebot", source, target));
        assertNull(deleted.get());

        destination.set(original.toBuilder().metadata(Map.of("duration-ms", "9999")).build());
        assertTrue(storage.deleteVerifiedDuplicate("beebot", source, target));
        assertEquals(source, deleted.get().key());
        assertFalse(storage.deleteVerifiedDuplicate("beebot", source, target));
        assertNull(request.get());
    }

}
