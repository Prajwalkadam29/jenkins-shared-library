def call(Map args = [:]) {
    def required = args.required ?: false

    if (required) {
        echo "Verifying cryptographic signature of the commit..."
        // 'git verify-commit' returns exit code 0 if valid, non-zero if invalid/unsigned
        def status = sh(script: "git verify-commit HEAD", returnStatus: true)
        if (status != 0) {
            error("Security Gate Failed: Commits deployed to production MUST be signed.")
        } else {
            echo "Commit signature verified successfully."
        }
    } else {
        echo "Commit signature verification skipped (not required for this environment)."
    }
}