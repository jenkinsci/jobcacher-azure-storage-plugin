package io.jenkins.plugins.jobcacherazurestorage;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.microsoftopentechnologies.windowsazurestorage.helper.AzureStorageAccount;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.azuresdk.HttpClientRetriever;
import java.io.File;
import java.util.Collections;
import jenkins.model.Jenkins;
import jenkins.plugins.itemstorage.ItemStorage;
import jenkins.plugins.itemstorage.ItemStorageDescriptor;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.verb.POST;

@Extension
public class AzureItemStorage extends ItemStorage<AzureObjectPath> {

    private String credentialsId;
    private String containerName;

    @DataBoundConstructor
    public AzureItemStorage() {}

    public String getCredentialsId() {
        return credentialsId;
    }

    public String getContainerName() {
        return containerName;
    }

    @DataBoundSetter
    public void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    @DataBoundSetter
    public void setContainerName(String containerName) {
        this.containerName = containerName;
    }

    @Override
    public AzureObjectPath getObjectPath(Item item, String path) {
        AzureClient azureClient = getAzureClient(item);

        return new AzureObjectPath(azureClient, item.getFullName(), path);
    }

    private AzureClient getAzureClient(Item item) {
        AzureStorageAccount.StorageAccountCredential accountCredential =
                AzureStorageAccount.getStorageAccountCredential(item, credentialsId);

        BlobServiceClient blobServiceClient = getBlobServiceClient(accountCredential);
        BlobContainerClient blobContainerClient = blobServiceClient.getBlobContainerClient(containerName);

        return new AzureClient(blobContainerClient, blobServiceClient.getAccountUrl());
    }

    private static BlobServiceClient getBlobServiceClient(
            AzureStorageAccount.StorageAccountCredential accountCredential) {
        return new BlobServiceClientBuilder()
                .credential(new StorageSharedKeyCredential(
                        accountCredential.getStorageAccountName(), accountCredential.getStorageAccountKey()))
                .httpClient(HttpClientRetriever.get())
                .endpoint(accountCredential.getBlobEndpointURL())
                .buildClient();
    }

    @Override
    public AzureObjectPath getObjectPathForBranch(Item item, String path, String branch) {
        String branchPath = new File(item.getFullName()).getParent() + "/" + branch;
        AzureClient azureClient = getAzureClient(item);

        return new AzureObjectPath(azureClient, branchPath, path);
    }

    public static AzureItemStorage get() {
        return ExtensionList.lookupSingleton(AzureItemStorage.class);
    }

    @Extension
    public static final class DescriptorImpl extends ItemStorageDescriptor<AzureObjectPath> {

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.AzureItemStorage_displayName();
        }

        @Override
        public boolean configure(StaplerRequest2 req, JSONObject json) throws FormException {
            save();
            return super.configure(req, json);
        }

        @POST
        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item item, @QueryParameter String value) {
            StandardListBoxModel result = new StandardListBoxModel();
            if (item == null) {
                if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
                    return result.includeCurrentValue(get().getCredentialsId());
                }
            } else {
                if (!item.hasPermission(Item.EXTENDED_READ) && !item.hasPermission(CredentialsProvider.USE_ITEM)) {
                    return result.includeCurrentValue(get().getCredentialsId());
                }
            }
            return result.includeEmptyValue()
                    .includeMatchingAs(
                            ACL.SYSTEM2,
                            item,
                            AzureStorageAccount.class,
                            Collections.emptyList(),
                            CredentialsMatchers.instanceOf(AzureStorageAccount.class))
                    .includeCurrentValue(get().getCredentialsId());
        }
    }
}
