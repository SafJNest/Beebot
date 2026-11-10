package com.safjnest.core.audio;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

public final class SoundStorage {
    private static final String ACCOUNT_ID = System.getenv("R2_ACCOUNT_ID");
    private static final String ACCESS_KEY_ID = System.getenv("R2_ACCESS_KEY_ID");
    private static final String SECRET_ACCESS_KEY = System.getenv("R2_SECRET_ACCESS_KEY");
    private static final String BUCKET = System.getenv("R2_BUCKET");
    private static final String LEGACY_DIRECTORY = "rsc" + File.separator + "sounds";
    private static final Path CACHE_DIRECTORY = Path.of(System.getProperty("java.io.tmpdir"), "beebot", "sounds");
    private static final S3Client CLIENT = createClient();

    private SoundStorage() {}

    public static boolean isConfigured() {
        return CLIENT != null;
    }

    public static String getLocalPath(String key) {
        Path cachedFile = CACHE_DIRECTORY.resolve(key);
        if (!isConfigured()) {
            Path legacyFile = Path.of(LEGACY_DIRECTORY, key);
            if (Files.exists(legacyFile)) return legacyFile.toString();
            throw new IllegalStateException("Cloudflare R2 is not configured and the sound is not available locally: " + key);
        }

        try {
            Files.createDirectories(CACHE_DIRECTORY);
            if (Files.exists(cachedFile)) return cachedFile.toString();

            try {
                CLIENT.getObject(GetObjectRequest.builder().bucket(BUCKET).key(key).build(), ResponseTransformer.toFile(cachedFile));
            } catch (NoSuchKeyException e) {
                Path legacyFile = Path.of(LEGACY_DIRECTORY, key);
                if (!Files.exists(legacyFile)) throw e;
                upload(legacyFile, key);
                Files.copy(legacyFile, cachedFile);
            }

            return cachedFile.toString();
        } catch (IOException e) {
            throw new IllegalStateException("Could not prepare sound from Cloudflare R2: " + key, e);
        }
    }

    public static void upload(Path file, String key) {
        requireConfigured();
        CLIENT.putObject(PutObjectRequest.builder().bucket(BUCKET).key(key).build(), RequestBody.fromFile(file));
    }

    public static void delete(String key) {
        if (!isConfigured()) return;
        CLIENT.deleteObject(DeleteObjectRequest.builder().bucket(BUCKET).key(key).build());
        try {
            Files.deleteIfExists(CACHE_DIRECTORY.resolve(key));
        } catch (IOException e) {
            throw new IllegalStateException("Could not remove cached sound: " + key, e);
        }
    }

    private static S3Client createClient() {
        if (ACCOUNT_ID == null || ACCESS_KEY_ID == null || SECRET_ACCESS_KEY == null || BUCKET == null) return null;

        return S3Client.builder()
                .endpointOverride(URI.create("https://" + ACCOUNT_ID + ".r2.cloudflarestorage.com"))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(ACCESS_KEY_ID, SECRET_ACCESS_KEY)))
                .region(Region.of("auto"))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).chunkedEncodingEnabled(false).build())
                .build();
    }

    private static void requireConfigured() {
        if (!isConfigured()) throw new IllegalStateException("Cloudflare R2 is not configured");
    }
}
