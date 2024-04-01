# jobcacher-azure-storage

## Introduction

This plugin is an extension of the [jobcacher-plugin](https://plugins.jenkins.io/jobcacher/) that allows you to store the caches in Azure Storage.

## Getting started

You only need to configure the extension to use Azure Storage under System Configuration.

![](docs/azure-config.png)

See [jobcacher-plugin](https://plugins.jenkins.io/jobcacher/) for usage in jobs and pipelines.

## Configuration as Code

```yaml
unclassified:
  globalItemStorage:
    storage:
      azure:
        containerName: "cache"
        credentialsId: "the-credentials-id"
```

## Contributing

Refer to our [contribution guidelines](https://github.com/jenkinsci/.github/blob/master/CONTRIBUTING.md)

## LICENSE

Licensed under MIT, see [LICENSE](LICENSE.md)

