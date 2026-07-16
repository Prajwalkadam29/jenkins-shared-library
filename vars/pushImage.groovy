def call(Map args = [:]) {
    def imageRef = args.imageRef

    echo "Pushing image to ECR: ${imageRef}"

    // Authenticate Docker CLI to AWS ECR using the Jenkins VM's IAM instance profile
    sh """
        aws ecr get-login-password --region ${env.AWS_REGION} | docker login --username AWS --password-stdin ${env.ECR_REGISTRY}
        docker push ${imageRef}
    """
}