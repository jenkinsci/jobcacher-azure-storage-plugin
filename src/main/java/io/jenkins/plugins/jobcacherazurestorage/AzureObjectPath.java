package io.jenkins.plugins.jobcacherazurestorage;

import hudson.FilePath;
import hudson.model.Job;
import java.io.IOException;
import jenkins.plugins.itemstorage.ObjectPath;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

public class AzureObjectPath extends ObjectPath {

    private final AzureClient azureClient;
    private final String fullName;
    private final String path;

    public AzureObjectPath(AzureClient azureClient, String fullName, String path) {
        this.azureClient = azureClient;
        this.fullName = fullName;
        this.path = path;
    }

    @Override
    public ObjectPath child(String childPath) throws IOException, InterruptedException {
        return new AzureObjectPath(azureClient, fullName, path + "/" + childPath);
    }

    @Override
    public void copyTo(FilePath target) throws IOException, InterruptedException {
        azureClient.download(target, fullName + "/" + path);
    }

    @Override
    public void copyFrom(FilePath source) throws IOException, InterruptedException {
        azureClient.upload(source, fullName + "/" + path);
    }

    @Override
    public boolean exists() throws IOException, InterruptedException {
        return azureClient.exists(fullName + "/" + path);
    }

    @Override
    public void deleteRecursive() {
        azureClient.delete(fullName + "/" + path);
    }

    @Override
    public HttpResponse browse(StaplerRequest request, StaplerResponse response, Job<?, ?> job, String name)
            throws IOException {
        return null;
    }
}
