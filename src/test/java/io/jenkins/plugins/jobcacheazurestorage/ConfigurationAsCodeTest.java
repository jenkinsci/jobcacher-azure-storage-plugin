package io.jenkins.plugins.jobcacheazurestorage;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.casc.misc.junit.jupiter.WithJenkinsConfiguredWithCode;
import io.jenkins.plugins.jobcacherazurestorage.AzureItemStorage;
import jenkins.plugins.itemstorage.GlobalItemStorage;
import org.junit.jupiter.api.Test;

@WithJenkinsConfiguredWithCode
public class ConfigurationAsCodeTest {

    @Test
    @ConfiguredWithCode("configuration-as-code.yml")
    public void shouldSupportConfigurationAsCode(JenkinsConfiguredWithCodeRule jenkinsRule) throws Exception {
        AzureItemStorage itemStorage =
                (AzureItemStorage) GlobalItemStorage.get().getStorage();
        assertThat(itemStorage.getCredentialsId(), is("the-credentials-id"));
        assertThat(itemStorage.getContainerName(), is("the-container-name"));
    }
}
