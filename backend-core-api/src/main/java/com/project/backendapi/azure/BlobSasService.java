package com.project.backendapi.azure;

import com.azure.storage.blob.*;
import com.azure.storage.blob.sas.*;
import com.azure.storage.common.sas.SasProtocol;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.OffsetDateTime;

@Service
public class BlobSasService {
    private final BlobServiceClient blobService;
    private final String containerName;
    public BlobSasService(BlobServiceClient blobService, @Value("${app.azure.blob-container:exports}") String containerName) {
        this.blobService=blobService; this.containerName=containerName;
    }
    public String createReadSas(String blobName) {
        if (blobService == null) throw new IllegalStateException("Azure Storage is not configured");
        var container = blobService.getBlobContainerClient(containerName);
        container.createIfNotExists();
        var blob = container.getBlobClient(blobName);
        BlobSasPermission permission = new BlobSasPermission().setReadPermission(true);
        BlobServiceSasSignatureValues values = new BlobServiceSasSignatureValues(
            OffsetDateTime.now().plusMinutes(10), permission).setProtocol(SasProtocol.HTTPS_HTTP);
        return blob.getBlobUrl() + "?" + blob.generateSas(values);
    }
}
