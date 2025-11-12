import com.neotys.jenkins.*

@Library('jenkins-groovy-lib') _
startMavenJob(sonarQg: false, dockerImage: 'slave_neoload:java21.0.4_i4j11.0.0_bullseye', toArchive: 'target/ApacheJMeter_NeoLoad*.jar', products: ['JMeterPlugin'])

node('master') {
  def jobParameter = JobParameter.newJobParameter(this, false)
  if (jobParameter.withRelease()) {
    def pom = readMavenPom(file: 'pom.xml')
    def version = pom.version

    stage('Release Github') {
      def credentialsId = 'svc_jenkins_github_neotys_app'
      def repo = 'Neotys-Labs/Apache-JMeter'
      def artifactPath = "${env.WORKSPACE}/target/ApacheJMeter_NeoLoad-${version}.jar"
      
      withCredentials([usernamePassword(credentialsId: credentialsId,
                                        usernameVariable: 'GITHUB_APP',
                                        passwordVariable: 'GITHUB_ACCESS_TOKEN')]) {
          def response = sh(script: """
            curl -X POST -H "Authorization: token ${GITHUB_ACCESS_TOKEN}" \
              -H "Accept: application/vnd.github.v3+json" \
              https://api.github.com/repos/${repo}/releases \
              -d '{
                "tag_name": "Neotys-Labs/Apache-JMeter.git-${version}",
                "name": "Apache JMeter NeoLoad ${version}",
                "body": "Test",
                "draft": true,
                "prerelease": false
               }'
               """, returnStdout: true).trim()
          echo "GitHub Release Response: ${response}"

          def jsonResponse = readJSON text: response
          def releaseId = jsonResponse.id.toString()

          echo "Release ID: ${releaseId}"

          def uploadUrl = "https://uploads.github.com/repos/${repo}/releases/${releaseId}/assets?name=ApacheJMeter_NeoLoad-${version}.jar"

        // Upload artifact
        def uploadResponse = sh(script: """
           curl -X POST -H "Authorization: token ${GITHUB_ACCESS_TOKEN}" \
             -H "Content-Type: application/java-archive" \
             --data-binary @${artifactPath} \
             "${uploadUrl}"
         """, returnStdout: true).trim()

          echo "Artifact Upload Response: ${uploadResponse}"
                                        }
    }
  }
}
