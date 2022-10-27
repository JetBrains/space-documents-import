# Import Documents into Space 🚀
![](https://jb.gg/badges/incubator-flat-square.svg)

This application imports documents from an external systems into JetBrains Space.

Here you can find a list of supported import sources and sample code.

## Execution examples

### From Confluence
```
$ docker run public.registry.jetbrains.space/p/space/containers/space-documents-import:latest confluence \
        --confluence-host https://<confluenceHost> \
        --confluence-space-key "<spaceKey>" \
        --confluence-username <confluenceUsername> \
        --confluence-password <confluencePassword> \
        --space-server https://<domain>.jetbrains.space \
        --space-project-key <spaceProjectKey> \
        --space-token <spaceAccessToken>
```

### From folder with files
```
$ docker run public.registry.jetbrains.space/p/space/containers/space-documents-import:latest folder \
        --folder <folderPath> \
        --space-server https://<domain>.jetbrains.space \
        --space-project-key <spaceProjectKey> \
        --space-token <spaceAccessToken>
```

As a `spaceAccessToken` you could use either [personal access token](https://www.jetbrains.com/help/space/personal-tokens.html) or [application permanent token](https://www.jetbrains.com/help/space/authorize-with-permanent-token.html).

## Build and Run Locally

### With Docker
```
docker build . -t space-documents-import
docker run space-documents-import ...
```

### Without Docker
```
./gradlew jar  # prepare fat jar
java -jar build/libs/space-documents-import.jar # run from jar with arguments
```

## Contributors

Pull requests are welcome! 🙌
