def call(Map args = [:]) {
    def tool = args.tool ?: 'gitleaks'
    def failOnFinding = args.failOnFinding == null ? true : args.failOnFinding

    echo "Running Secrets Scan using ${tool}..."
    def exitCode = sh(script: "${tool} detect --source .", returnStatus: true)

    if (exitCode != 0 && failOnFinding) {
        error("Security Gate Failed: Hardcoded secrets detected in the repository!")
    }
}