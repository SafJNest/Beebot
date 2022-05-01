package com.safjnest.Utilities;

import java.util.ArrayList;
import java.util.HashMap;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;

public class AwsS3 {
    private AmazonS3 s3Client;
    private String bucket;

    public AwsS3(AmazonS3 s3Client, String bucket) {
        this.s3Client = s3Client;
        this.bucket = bucket;
    }
    
    public void initialize() {
        
    }

    /**
     * Makes a list of all the files in the bucket.
     * <p>The files are sorted by the first letter of the file name in the lexicographic order.
     * @param prefix
     * @return
     */
    public HashMap<String, ArrayList<String>> listObjects(String prefix) {
        HashMap<String, ArrayList<String>> alpha = new HashMap<>();
        ListObjectsRequest listObjectsRequest = new ListObjectsRequest()
            .withBucketName(bucket)
            .withPrefix(prefix);
        ObjectListing objectListing;
        do {
            objectListing = s3Client.listObjects(listObjectsRequest);
            for (S3ObjectSummary objectSummary : objectListing.getObjectSummaries()) {
                if(!alpha.containsKey(String.valueOf(objectSummary.getKey().split("/")[2].charAt(0)).toUpperCase()))
                    alpha.put(String.valueOf(objectSummary.getKey().split("/")[2].charAt(0)).toUpperCase(), new ArrayList<String>());
                alpha.get(String.valueOf(objectSummary.getKey().split("/")[2].charAt(0)).toUpperCase()).add(objectSummary.getKey().split("/")[2]);
            }
            listObjectsRequest.setMarker(objectListing.getNextMarker());
        } while (objectListing.isTruncated());
        return alpha;
    }

}
