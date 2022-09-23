job("Build, run tests and push to public.jetbrains.space registry (latest)") {
    startOn {
        gitPush {
            branchFilter = "refs/heads/main"
        }
    }

    docker("Push to public.jetbrains.space registry") {
        env["REGISTRY_USER"] = Secrets("public-jetbrains-space-issues-import-publisher-client-id")
        env["REGISTRY_TOKEN"] = Secrets("public-jetbrains-space-issues-import-publisher-token")

        beforeBuildScript {
            content = """
                B64_AUTH=${'$'}(echo -n ${'$'}REGISTRY_USER:${'$'}REGISTRY_TOKEN | base64 -w 0)
                echo "{\"auths\":{\"public.registry.jetbrains.space\":{\"auth\":\"${'$'}B64_AUTH\"}}}" > ${'$'}DOCKER_CONFIG/config.json
            """.trimIndent()
        }

        build()

        push("public.registry.jetbrains.space/p/space/containers/space-documents-import")
    }
}