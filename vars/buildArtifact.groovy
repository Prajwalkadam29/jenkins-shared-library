// vars/buildArtifact.groovy
def call(Map args = [:]) {
    def appType = args.appType

    if (appType == 'maven') {
        echo "Building Maven artifact (skipping tests for now)..."
        sh "./mvnw clean package -DskipTests"
    } else {
        error("Unsupported appType: ${appType}. Add support in vars/buildArtifact.groovy")
    }
}