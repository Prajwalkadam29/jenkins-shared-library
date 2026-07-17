def call(Map args = [:]) {
    def imageRef = args.imageRef
    def format = args.format ?: 'cyclonedx'
    def outputDir = args.outputDir ?: 'reports'

    sh "mkdir -p ${outputDir}"
    echo "Generating SBOM for ${imageRef}..."

    // 1. Generate the SBOM
    sh "trivy image --format ${format} --output ${outputDir}/sbom-${format}.json ${imageRef}"

    // 2. Attest (attach) the SBOM to the image in ECR using Cosign
    echo "Attesting SBOM to the container registry..."

    // FIX: Added the credentials block and the --key argument so Cosign knows how to authenticate
    withCredentials([
            file(credentialsId: 'cosign-private-key', variable: 'COSIGN_KEY'),
            string(credentialsId: 'cosign-password', variable: 'COSIGN_PASSWORD')
    ]) {
        sh "cosign attest --yes --key \${COSIGN_KEY} --predicate ${outputDir}/sbom-${format}.json --type ${format} ${imageRef}"
    }
}