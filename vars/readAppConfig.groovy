def call() {
    // Requires the Jenkins "Pipeline Utility Steps" plugin
    return readYaml(file: 'pipeline.config.yaml')
}