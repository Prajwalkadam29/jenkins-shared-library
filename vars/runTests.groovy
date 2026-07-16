// vars/runTests.groovy
def call(Map args = [:]) {
    echo "Running Unit and Integration tests..."
    // Executes tests. The Jenkinsfile post-step picks up the surefire-reports XML.
    sh "./mvnw test"
}