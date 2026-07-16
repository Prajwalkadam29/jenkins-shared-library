def call(Map args = [:]) {
    def imageRef = args.imageRef
    def dockerfile = args.dockerfile ?: 'Dockerfile'

    echo "Building Docker image: ${imageRef} using standard Docker engine..."

    sh """
        docker build -t ${imageRef} -f ${dockerfile} .
    """
}