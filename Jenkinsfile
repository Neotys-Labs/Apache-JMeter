@Library('jenkins-groovy-lib')
import com.neotys.jenkins.*

def jobParameter = JobParameter.newJobParameter(this, false)

node ('master') {
    img = 'slave_neoload:i4j8u312'
    docker.image(img).inside {
        stage('Checkout') {
            checkout scm
            cleanGit()
        }
        dir('.'){
            if (jobParameter.buildKind == BuildKind.FEATURE) {
                featureVersion(jobParameter.versionPrefix)
            } else {
                release(jobParameter)
            }

            buildMaven(jobParameter, '')
            sonar(jobParameter)
        }
    }
    upstream()
    archiveArtifacts artifacts: 'target/ApacheJMeter_NeoLoad*.jar'
}