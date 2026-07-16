def call(Map args = [:]) {
    def imageRef = args.imageRef
    def failOnSeverity = args.failOnSeverity ?: 'CRITICAL'
    def reportDir = args.reportDir ?: 'reports'

    sh "mkdir -p ${reportDir}"
    echo "Scanning built container image ${imageRef}..."

    sh """
        trivy image --severity ${failOnSeverity} \\
                    --ignore-unfixed \\
                    --exit-code 1 \\
                    --format json \\
                    --output ${reportDir}/trivy-image-report.json \\
                    ${imageRef}
    """
}