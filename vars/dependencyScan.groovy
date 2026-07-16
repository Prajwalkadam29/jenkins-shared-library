def call(Map args = [:]) {
    def failOnSeverity = args.failOnSeverity ?: 'CRITICAL'

    echo "Running Dependency/SCA Scan using Trivy..."

    // exit-code 1 ensures the pipeline fails if vulnerabilities of the specified severity are found
    sh "trivy fs --scanners vuln --severity ${failOnSeverity} --ignore-unfixed --exit-code 1 ."
}