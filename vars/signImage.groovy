def call(Map args = [:]) {
    def imageRef = args.imageRef

    echo "Signing image ${imageRef} using Cosign (Private Key)..."

    // Assumes you generate a keypair locally (cosign generate-key-pair)
    // and upload cosign.key to Jenkins as a Secret File named 'cosign-private-key'
    // and the password as a Secret Text named 'cosign-password'.
    withCredentials([
            file(credentialsId: 'cosign-private-key', variable: 'COSIGN_KEY'),
            string(credentialsId: 'cosign-password', variable: 'COSIGN_PASSWORD')
    ]) {
        sh "cosign sign --key \${COSIGN_KEY} ${imageRef}"
    }
}