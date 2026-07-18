# DevSecOps Jenkins Shared Library

This repository contains the Jenkins Shared Library (`vars/`) that powers the Continuous Integration (CI) pipeline for the Currency Converter application.

By extracting the pipeline logic into this centralized library, we keep individual application `Jenkinsfile`s incredibly clean, enforce mandatory security gates across all builds, and completely decouple Continuous Integration (Jenkins) from Continuous Deployment (ArgoCD/GitOps).

---

## The Role of Jenkins in this Architecture

In this modern GitOps architecture, **Jenkins is strictly a CI and Security engine**.

Jenkins does not deploy applications. It does not have access to the Kubernetes API, and it holds no `kubeconfig` files. Its exact responsibilities are:

1. **Code Verification:** Compile code and run unit/integration tests.
2. **Security Gating:** Enforce static analysis (SAST), dependency scanning (SCA), and secrets detection.
3. **Artifact Generation:** Build container images and push them to Amazon ECR.
4. **Zero-Trust Supply Chain:** Cryptographically sign images and generate SBOMs using Cosign.
5. **GitOps Handoff:** Commit the new image tag to the separate GitOps configuration repository, triggering ArgoCD to handle the actual deployment.

---

## 🔀 Branching Strategy & Pipeline Flow

The pipeline executes differently depending on the branch being built, optimizing for developer feedback speed on feature branches while enforcing strict release controls on the `main` branch.

### 1. Feature Branch Flow (Continuous Integration)
When a developer pushes to a `feature/*` or `bugfix/*` branch, Jenkins runs a "dry" pipeline designed for rapid feedback.

* **Steps Executed:** Checkout -> Secrets Scan (`gitleaks`) -> SAST (`SonarQube`) -> Quality Gate -> Dependency Scan (`trivy-fs`) -> Build Artifact & Test.

* **Result:** The pipeline stops here. No Docker image is built, nothing is pushed to ECR, and the GitOps repo is untouched.


### 2. Main Branch Flow (Continuous Delivery)
When code is merged to `main`, the full release pipeline is triggered.

* Steps Executed: All feature branch steps, plus -> Build Container Image -> Scan Image (`trivy image`) -> Push to ECR -> Sign Image (`cosign`) -> Attest SBOM.

* The Handoff: The `updateGitOpsManifest` step clones the GitOps repository.
    * If deploying to `dev` or `staging`, it directly commits the new tag to the main branch of the GitOps repo.
    * If deploying to `prod`, it creates a new branch and uses the GitHub CLI (`gh`) to open a `Pull Request`, requiring `human approval` before ArgoCD deploys to production.

---

## 📂 File Deep-Dive (vars/ directory)

Every Groovy script in the `vars/` directory acts as a custom global variable that can be called directly from a Jenkinsfile.

| File                           | Tool              | Description & Internal Logic                                                                                                                                                                               |
|--------------------------------|-------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `buildArtifact.groovy`         | Maven             | Detects the `appType` (e.g., `maven`) and executes the application build (e.g., `./mvnw clean package -DskipTests`).                                                                                       |
| `buildContainerImage.groovy`   | Docker            | Executes a standard `docker build` to package the compiled artifact into a container image.                                                                                                                |
| `dependencyScan.groovy`        | Trivy             | Scans the local filesystem for vulnerable dependencies. Configured with `--exit-code 1` to hard-fail the pipeline if `CRITICAL` vulnerabilities are found.                                                 |
| `generateSBOM.groovy`          | Trivy / Cosign    | Generates a CycloneDX Software Bill of Materials (SBOM) and uses Cosign to cryptographically attach (attest) it to the image in ECR.                                                                       |
| `notify.groovy`                | Jenkins Email     | A robust, custom HTML email generator. Sends color-coded (Green/Red) status emails containing the branch, commit hash, failing stage, and a direct link to the console log.                                |
| `pushImage.groovy`             | AWS CLI / Docker  | Authenticates to Amazon ECR using the underlying EC2 instance's IAM Profile (IRSA/Instance Profile) and pushes the image.                                                                                  |
| `readAppConfig.groovy`         | Pipeline Utility  | Reads a local `pipeline.config.yaml` from the application repo to dynamically determine the app name and type.                                                                                             |
| `runTests.groovy`              | Maven             | Executes unit and integration tests (`./mvnw test`), outputting Surefire XML reports for Jenkins to archive.                                                                                               |
| `scanForSecrets.groovy`        | Gitleaks          | Scans the codebase for accidentally committed API keys, passwords, or tokens. Fails the pipeline immediately if any are found.                                                                             |
| `scanImage.groovy`             | Trivy             | Scans the compiled Docker image for OS-level vulnerabilities before it is pushed to ECR. Fails on `CRITICAL CVEs`.                                                                                         |
| `signImage.groovy`             | Cosign            | Retrieves the `cosign-private-key` from Jenkins Credentials Binding and signs the container image. This signature is later verified by Kyverno in the Kubernetes cluster.                                  |
| `updateGitOpsManifest.groovy`  | yq / Git / GH CLI | The GitOps Bridge. Clones the GitOps repo, uses yq to update the Helm `image.tag` inside `values-<env>.yaml`. For production, it utilizes the GitHub CLI (`gh pr create`) to enforce a PR review workflow. |
| `verifyCommitSignature.groovy` | Git               | Ensures that the actual git commit triggering the build was cryptographically signed by the developer. Mandatory for `prod` deployments.                                                                   |

---

## Jenkins Setup & Integration

To utilize this shared library, the Jenkins management server must be configured with a **Multibranch Pipeline**.

### 1. Global Pipeline Library Configuration

1. Navigate to **Manage Jenkins -> System -> Global Pipeline Libraries**.
2. Name the `library devsecops-shared-lib`.
3. Set the default version to `main`.
4. Point the SCM to this Git repository URL.
5. In your application's `Jenkinsfile`, import it at the very top using: `@Library('devsecops-shared-lib') _`

### 2. Required Jenkins Plugins

* **Pipeline & Pipeline: Groovy** (Core execution)

* **Pipeline Utility Steps** (Required for the `readYaml` function in `readAppConfig.groovy`)

* **SonarQube Scanner** (Required for the `withSonarQubeEnv` wrapper)

* **Credentials Binding** (Securely injects Cosign keys and Git tokens)

* **Email Extension Plugin** (Powers the `notify.groovy` HTML emails)


### 3. Required Server Binaries
The Jenkins EC2 instance executing these scripts must have the following binaries installed natively:
java (JDK), docker, aws, trivy, cosign, gitleaks, gh (GitHub CLI), and yq.

---

## Design Choices & Architectural Trade-offs

**1. Standard Docker vs. Kaniko:**
This pipeline utilizes the standard Docker daemon (`docker build`) on the Jenkins EC2 instance. While effective, a more security-hardened approach for Kubernetes-based Jenkins agents would involve using Kaniko or Buildah, which allow building images entirely without root privileges or a Docker daemon.

**2. Cosign Private Key vs. Keyless (OIDC):**
Image signing is currently implemented using a generated Cosign key-pair stored in the Jenkins Credential store. A more advanced tradeoff to eliminate long-lived private keys entirely would be implementing Cosign's "Keyless" mode, leveraging OIDC and the Sigstore Fulcio/Rekor transparency logs.

**3. Monolithic Jenkins Node vs. Ephemeral Agents:**
Currently, the toolchain (Trivy, Cosign, GH CLI) is installed directly onto the Jenkins EC2 server. In a massive enterprise environment, this would be migrated to ephemeral Kubernetes agent pods, where each step executes inside an isolated, disposable container specifically tailored for that tool.

**4. Hard-Failing Security Gates:**
The `trivy`, `gitleaks`, and `SonarQube gates` are configured to intentionally break the build (`exit-code 1`, `waitForQualityGate abortPipeline: true`). This is a deliberate "Shift-Left" design choice to prevent vulnerable code from ever reaching the container registry, let alone the Kubernetes cluster.

---
