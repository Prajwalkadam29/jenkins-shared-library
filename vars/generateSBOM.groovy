// vars/generateSBOM.groovy
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
    sh "cosign attest --yes --predicate ${outputDir}/sbom-${format}.json --type ${format} ${imageRef}"
}