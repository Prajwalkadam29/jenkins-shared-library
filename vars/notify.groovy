// vars/notify.groovy
//
// Sends an HTML EMAIL notification on every pipeline completion - both
// SUCCESS and FAILURE. No Slack. The email includes enough detail to
// diagnose a failure without opening Jenkins first: branch, commit, which
// stage failed, and a direct link to the full console log. Color-coded
// (green/red) so a failure is visually obvious even skimming an inbox.

def call(Map args = [:]) {
    def status  = args.status ?: 'UNKNOWN'
    def message = args.message ?: ''

    def branch          = env.BRANCH_NAME ?: 'unknown'
    def commitShort     = env.GIT_COMMIT_SHORT ?: (env.GIT_COMMIT ? env.GIT_COMMIT.take(8) : 'unknown')
    def failedStage     = (status == 'FAILURE') ? (env.STAGE_NAME ?: 'unknown stage') : null
    def fixedRecipients = readAppConfig()?.notify?.email ?: ''

    def statusColor = (status == 'SUCCESS') ? '#2e7d32' : '#c62828'   // green / red
    def statusBg    = (status == 'SUCCESS') ? '#e8f5e9' : '#ffebee'

    def subject = "[${status}] ${env.JOB_NAME} #${env.BUILD_NUMBER} (${branch})"

    def failedStageRow = failedStage ? """
        <tr>
          <td style="padding:6px 12px; color:#555; font-weight:bold;">Failed stage</td>
          <td style="padding:6px 12px; color:#c62828;">${failedStage}</td>
        </tr>""" : ""

    def body = """\
<html>
<body style="font-family:Arial,Helvetica,sans-serif; background:#f4f4f4; padding:20px; margin:0;">
  <div style="max-width:600px; margin:0 auto; background:#ffffff; border-radius:6px; overflow:hidden; border:1px solid #e0e0e0;">

    <div style="background:${statusBg}; padding:16px 20px; border-bottom:3px solid ${statusColor};">
      <h2 style="margin:0; color:${statusColor}; font-size:18px;">
        ${status == 'SUCCESS' ? '&#9989;' : '&#10060;'} Pipeline ${status}
      </h2>
    </div>

    <div style="padding:20px;">
      <table style="width:100%; border-collapse:collapse; font-size:14px;">
        <tr>
          <td style="padding:6px 12px; color:#555; font-weight:bold; width:140px;">Job</td>
          <td style="padding:6px 12px; color:#111;">${env.JOB_NAME}</td>
        </tr>
        <tr style="background:#fafafa;">
          <td style="padding:6px 12px; color:#555; font-weight:bold;">Build</td>
          <td style="padding:6px 12px; color:#111;">#${env.BUILD_NUMBER}</td>
        </tr>
        <tr>
          <td style="padding:6px 12px; color:#555; font-weight:bold;">Branch</td>
          <td style="padding:6px 12px; color:#111;">${branch}</td>
        </tr>
        <tr style="background:#fafafa;">
          <td style="padding:6px 12px; color:#555; font-weight:bold;">Commit</td>
          <td style="padding:6px 12px; color:#111; font-family:monospace;">${commitShort}</td>
        </tr>${failedStageRow}
        <tr style="background:#fafafa;">
          <td style="padding:6px 12px; color:#555; font-weight:bold; vertical-align:top;">Summary</td>
          <td style="padding:6px 12px; color:#111;">${message}</td>
        </tr>
      </table>

      <div style="margin-top:24px; text-align:center;">
        <a href="${env.BUILD_URL}console"
           style="display:inline-block; background:${statusColor}; color:#ffffff; text-decoration:none;
                  padding:10px 22px; border-radius:4px; font-size:14px; font-weight:bold;">
          View Console Log
        </a>
      </div>

      <p style="margin-top:20px; font-size:12px; color:#999; text-align:center;">
        <a href="${env.BUILD_URL}" style="color:#999;">${env.BUILD_URL}</a>
      </p>
    </div>
  </div>
</body>
</html>
"""

    emailext(
            subject: subject,
            body: body,
            mimeType: 'text/html',
            to: fixedRecipients,
            recipientProviders: [
                    [$class: 'CulpritsRecipientProvider'],
                    [$class: 'RequesterRecipientProvider']
            ]
    )
}