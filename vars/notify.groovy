def call(Map args = [:]) {
    def status  = args.status ?: 'UNKNOWN'
    def message = args.message ?: ''

    def branch       = env.BRANCH_NAME ?: 'unknown'
    def commitShort  = env.GIT_COMMIT_SHORT ?: (env.GIT_COMMIT ? env.GIT_COMMIT.take(8) : 'unknown')
    def failedStage  = (status == 'FAILURE') ? (env.STAGE_NAME ?: 'unknown stage') : null
    def fixedRecipients = readAppConfig()?.notify?.email ?: ''

    def subject = "[${status}] ${env.JOB_NAME} #${env.BUILD_NUMBER} (${branch})"

    def body = """\
Status:       ${status}
Job:          ${env.JOB_NAME}
Branch:       ${branch}
Build number: #${env.BUILD_NUMBER}
Commit:       ${commitShort}
${failedStage ? "Failed stage:  ${failedStage}\n" : ""}Summary:      ${message}

Full console log: ${env.BUILD_URL}console
Build details:    ${env.BUILD_URL}
"""

    emailext(
            subject: subject,
            body: body,
            to: fixedRecipients,
            recipientProviders: [
                    [$class: 'CulpritsRecipientProvider'],
                    [$class: 'RequesterRecipientProvider']
            ]
    )
}