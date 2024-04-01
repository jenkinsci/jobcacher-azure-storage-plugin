package io.jenkins.plugins.jobcacherazurestorage;

import com.azure.core.credential.AzureSasCredential;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.BlobUrlParts;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.options.BlobUploadFromFileOptions;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import com.google.common.io.Files;
import hudson.FilePath;
import hudson.ProxyConfiguration;
import hudson.remoting.VirtualChannel;
import io.jenkins.plugins.azuresdk.HttpClientRetriever;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import jenkins.MasterToSlaveFileCallable;
import jenkins.model.Jenkins;

public class AzureClient {

    private final BlobContainerClient container;
    private final String blobEndpoint;

    public AzureClient(BlobContainerClient container, String blobEndpoint) {
        this.container = container;
        this.blobEndpoint = blobEndpoint;
    }

    public boolean exists(String path) {
        return container.getBlobClient(path).exists();
    }

    public void download(FilePath target, String path) throws IOException, InterruptedException {
        BlobClient blobClient = container.getBlobClient(path);

        String sas = generateReadSas(blobClient);
        String blobUrl = blobClient.getBlobUrl() + "?" + sas;

        target.act(new DownloadFromBlobStorage(Jenkins.get().proxy, blobEndpoint, blobUrl));
    }

    public void upload(FilePath filePath, String path) throws IOException, InterruptedException {
        BlobClient blobClient = container.getBlobClient(path);

        String sas = generateWriteSas(blobClient);
        String blobUrl = blobClient.getBlobUrl() + "?" + sas;

        filePath.act(new UploadToBlobStorage(Jenkins.get().proxy, blobEndpoint, blobUrl));
    }

    private String generateReadSas(BlobClient blobClient) {
        BlobSasPermission permissions = new BlobSasPermission().setReadPermission(true);
        BlobServiceSasSignatureValues sasSignatureValues =
                new BlobServiceSasSignatureValues(generateExpiryDate(), permissions);

        return blobClient.generateSas(sasSignatureValues);
    }

    private String generateWriteSas(BlobClient blobClient) {
        BlobSasPermission permissions = new BlobSasPermission().setWritePermission(true);
        BlobServiceSasSignatureValues sasSignatureValues =
                new BlobServiceSasSignatureValues(generateExpiryDate(), permissions);

        return blobClient.generateSas(sasSignatureValues);
    }

    public static OffsetDateTime generateExpiryDate() {
        return OffsetDateTime.now().plusHours(1);
    }

    public void delete(String filePath) {
        container.getBlobClient(filePath).delete();
    }

    private static class UploadToBlobStorage extends MasterToSlaveFileCallable<Void> {

        public static final int TIMEOUT = 30;
        private final ProxyConfiguration proxy;
        private final String blobEndpoint;
        private final String blobUrl;

        UploadToBlobStorage(ProxyConfiguration proxy, String blobEndpoint, String blobUrl) {
            this.proxy = proxy;
            this.blobEndpoint = blobEndpoint;
            this.blobUrl = blobUrl;
        }

        private BlobServiceClient getSynchronousBlobServiceClient(String sas) {
            return new BlobServiceClientBuilder()
                    .credential(new AzureSasCredential(sas))
                    .httpClient(HttpClientRetriever.get(proxy))
                    .endpoint(blobEndpoint)
                    .buildClient();
        }

        @Override
        public Void invoke(File file, VirtualChannel channel) {
            // fall back to sync client when more than 500 files are being uploaded
            // likely less efficient although not really tested for scale yet.
            // https://github.com/jenkinsci/azure-artifact-manager-plugin/issues/26

            BlobUrlParts blobUrlParts = BlobUrlParts.parse(blobUrl);
            BlobClient blobClient = getSynchronousBlobClient(blobUrlParts);
            BlobUploadFromFileOptions options =
                    new BlobUploadFromFileOptions(file.getAbsolutePath()).setHeaders(getBlobHttpHeaders(file));
            blobClient.uploadFromFileWithResponse(options, Duration.ofSeconds(TIMEOUT), null);
            return null;
        }

        private BlobClient getSynchronousBlobClient(BlobUrlParts blobUrlParts) {
            String sas = blobUrlParts.getCommonSasQueryParameters().encode();
            BlobServiceClient blobServiceClient = getSynchronousBlobServiceClient(sas);

            BlobContainerClient containerClient =
                    blobServiceClient.getBlobContainerClient(blobUrlParts.getBlobContainerName());
            return containerClient.getBlobClient(blobUrlParts.getBlobName());
        }

        private BlobHttpHeaders getBlobHttpHeaders(File file) {
            BlobHttpHeaders method = new BlobHttpHeaders();

            contentType(file).ifPresent(method::setContentType);

            return method;
        }

        private static Optional<String> contentType(File file) {
            String fileExtension = Files.getFileExtension(file.getName());

            String contentType;
            switch (fileExtension) {
                case "zip":
                    contentType = "application/zip";
                    break;
                case "tgz":
                    contentType = "application/gzip";
                    break;
                case "tar":
                    contentType = "application/x-tar";
                    break;
                case "zst":
                    contentType = "application/zstd";
                    break;
                default:
                    contentType = null;
                    break;
            }
            return Optional.ofNullable(contentType);
        }
    }

    private static class DownloadFromBlobStorage extends MasterToSlaveFileCallable<Void> {
        private final ProxyConfiguration proxy;
        private final String blobEndpoint;
        private final String blobUrl;

        public DownloadFromBlobStorage(ProxyConfiguration proxy, String blobEndpoint, String blobUrl) {
            this.proxy = proxy;
            this.blobEndpoint = blobEndpoint;
            this.blobUrl = blobUrl;
        }

        private BlobServiceClient getSynchronousBlobServiceClient(String sas) {
            return new BlobServiceClientBuilder()
                    .credential(new AzureSasCredential(sas))
                    .httpClient(HttpClientRetriever.get(proxy))
                    .endpoint(blobEndpoint)
                    .buildClient();
        }

        private BlobClient getSynchronousBlobClient(BlobUrlParts blobUrlParts) {
            String sas = blobUrlParts.getCommonSasQueryParameters().encode();
            BlobServiceClient blobServiceClient = getSynchronousBlobServiceClient(sas);

            BlobContainerClient containerClient =
                    blobServiceClient.getBlobContainerClient(blobUrlParts.getBlobContainerName());
            return containerClient.getBlobClient(blobUrlParts.getBlobName());
        }

        @Override
        public Void invoke(File file, VirtualChannel channel) throws IOException {
            BlobUrlParts blobUrlParts = BlobUrlParts.parse(blobUrl);
            BlobClient blobClient = getSynchronousBlobClient(blobUrlParts);

            try (OutputStream fos = new FileOutputStream(file)) {
                blobClient.downloadStream(fos);
            }

            return null;
        }
    }
}
