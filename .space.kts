job("Build and push docker image") {
    startOn {
        gitPush {
            branchFilter = "refs/heads/main"
        }
    }
    
    val registry = "public.registry.jetbrains.space"
    val imageName = "$registry/p/space/containers/space-documents-import"
    val armTag = "arm64"
    val x86Tag = "amd64"
    
    val dockerLogin: () -> String = {
        """
            export DOCKER_CONFIG=/tmp/docker.json
            echo ${'$'}REGISTRY_TOKEN | docker login --username ${'$'}REGISTRY_USER --password-stdin $registry
        """.trimIndent()
    }
    val dockerBuildPush: (archTag: String) -> String = { archTag ->
        """
            docker build -t $imageName:${'$'}JB_SPACE_EXECUTION_NUMBER-$archTag .
            docker push $imageName:${'$'}JB_SPACE_EXECUTION_NUMBER-$archTag
        """.trimIndent()
    }
    
    parallel {
        host("Build ARM64 image") {
            env["REGISTRY_USER"] = Secrets("public-jetbrains-space-issues-import-publisher-client-id")
            env["REGISTRY_TOKEN"] = Secrets("public-jetbrains-space-issues-import-publisher-token")
            shellScript {
                content = """
                    ${dockerLogin()}
                    ${dockerBuildPush(armTag)}
                """.trimIndent()
            }
            requirements {
                os {
                    type = OSType.Linux
                    arch = "aarch64"
                }
                workerTags("linux-arm64")
            }
        }

        host("Build X86 image") {
            env["REGISTRY_USER"] = Secrets("public-jetbrains-space-issues-import-publisher-client-id")
            env["REGISTRY_TOKEN"] = Secrets("public-jetbrains-space-issues-import-publisher-token")
            shellScript {
                content = """
                    ${dockerLogin()}
                    ${dockerBuildPush(x86Tag)}
                """.trimIndent()
            }
            requirements {
                os {
                    type = OSType.Linux
                    arch = "amd64"
                }
            }
        }
    }
    
    host("Build and publish multiarch manifest") {
        env["REGISTRY_USER"] = Secrets("public-jetbrains-space-issues-import-publisher-client-id")
        env["REGISTRY_TOKEN"] = Secrets("public-jetbrains-space-issues-import-publisher-token")
        shellScript {
            content = """
                ${dockerLogin()}

                docker manifest rm $imageName:${'$'}JB_SPACE_EXECUTION_NUMBER
                docker manifest create $imageName:${'$'}JB_SPACE_EXECUTION_NUMBER \
                  $imageName:${'$'}JB_SPACE_EXECUTION_NUMBER-$armTag \
                  $imageName:${'$'}JB_SPACE_EXECUTION_NUMBER-$x86Tag
                docker manifest push $imageName:${'$'}JB_SPACE_EXECUTION_NUMBER

                docker manifest rm $imageName:latest
                docker manifest create $imageName:latest \
                  $imageName:${'$'}JB_SPACE_EXECUTION_NUMBER-$armTag \
                  $imageName:${'$'}JB_SPACE_EXECUTION_NUMBER-$x86Tag
                docker manifest push $imageName:latest
                docker system prune -a -f
            """.trimIndent()
        }
        requirements {
            os {
                type = OSType.Linux
                arch = "aarch64"
            }
            workerTags("linux-arm64")
        }
    }
}