def call(Map args = [:]) {
    def appName     = args.appName
    def environment = args.environment
    def imageTag    = args.imageTag
    def configRepo  = args.configRepoUrl ?: 'https://github.com/Prajwalkadam29/gitops-config-cc-app.git'
    def credsId     = args.credentialsId ?: 'gitops-config-write-token'

    withCredentials([usernamePassword(credentialsId: credsId, usernameVariable: 'GIT_USER', passwordVariable: 'GIT_TOKEN')]) {
        sh """
            rm -rf gitops-config
            # Escaped the variables so Jenkins doesn't leak the token in logs/state
            git clone https://\${GIT_USER}:\${GIT_TOKEN}@${configRepo.replaceFirst('https://', '')} gitops-config
            cd gitops-config
            
            git config user.name "jenkins-bot"
            git config user.email "jenkins-bot@your-org.com"

            # 1. Update the environment-specific file dynamically
            yq -i '.image.tag = "${imageTag}"' apps/${appName}/helm/values-${environment}.yaml
            
            # 2. Update the base values.yaml file so ArgoCD immediately catches it
            yq -i '.image.tag = "${imageTag}"' apps/${appName}/helm/values.yaml
        """

        if (environment == 'prod') {
            def branch = "ci/${appName}-${imageTag}"
            sh """
                cd gitops-config
                git checkout -b ${branch}
                # The -am flag will automatically commit BOTH modified yaml files
                git commit -am "deploy(${appName}): bump prod image tag to ${imageTag}"
                git push origin ${branch}
                
                # Escaped the token here too
                GH_TOKEN=\${GIT_TOKEN} gh pr create \
                  --title "deploy(${appName}): ${imageTag} to prod" \
                  --body "Automated tag bump from Jenkins build. Requires @release-approvers review." \
                  --base main --head ${branch}
            """
            echo "Opened PR to promote ${appName}:${imageTag} to prod."
        } else {
            sh """
                cd gitops-config
                # The -am flag will automatically commit BOTH modified yaml files
                git commit -am "deploy(${appName}): bump ${environment} image tag to ${imageTag}"
                git push origin main
            """
            echo "Committed ${appName}:${imageTag} to ${environment} and base values. Argo CD will deploy."
        }
    }
}