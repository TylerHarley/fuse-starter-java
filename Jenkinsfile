import groovy.json.JsonOutput

pipeline {
    agent any
    tools {
        maven '3.5.2'
    }
    stages {
        stage('Build') {
            steps {
                populateGlobalVariables()
                sh 'mvn clean compile'
            }
        }
        stage('Unit tests') {
            steps {
                sh 'mvn org.jacoco:jacoco-maven-plugin:0.8.0:prepare-agent test'
            }
            post {
                always {
                    junit 'target/surefire-reports/*.xml'
                }
            }
        }
        stage('SonarQube analysis') {
        	// see: https://docs.sonarqube.org/display/SCAN/Analyzing+with+SonarQube+Scanner+for+Jenkins#AnalyzingwithSonarQubeScannerforJenkins-AnalyzinginaJenkinspipeline
            steps {
            	populateTargetBranch()
            	echo "Branch name: ${env.BRANCH_NAME} Target branch: ${targetBranch}"            	                 
                withSonarQubeEnv('SonarCloud FUSE') {
                  sh "mvn -Dsonar.organization=galatea -Dsonar.branch.name=${env.GIT_BRANCH} -Dsonar.branch.target=${targetBranch} sonar:sonar"
                }
            }
        }
        stage('Quality gate') {
            steps {
                // Just in case something goes wrong, pipeline will be killed after a timeout
                timeout(time: 2, unit: 'MINUTES') {
                    script {
                        def qg = waitForQualityGate()
                        if (qg.status != 'OK') {
                            error "Pipeline aborted due to quality gate failure: ${qg.status}"
                        }
                    }
                }
            }
        }
        stage('Deploy') {
            when {
                expression { isDeployBranch() }
            }
            steps {
                // for the moment just re-do all the maven phases, I tried doing just jar:jar, but it wasn't working with cloud foundry
                sh 'mvn package -DskipTests'

                pushToCloudFoundry(
                    target: "https://api.run.pivotal.io/",
                    organization: "FUSE",
                    cloudSpace: "development",
                    credentialsId: "PIVOTAL-WEB",
                    manifestChoice: [
                        value: "jenkinsConfig",
                        appName: "fuse-rest-dev-${env.GIT_COMMIT}",
                        memory: "768",
                        instances: "1",
                        appPath: "target/fuse-starter-java-0.0.1-SNAPSHOT.jar",
                        envVars: [
                          [key: "SPRING_PROFILES_ACTIVE", value: "dev"],
                          [key: "JAVA_OPTS", value: "-Dapplication.name=my-fuse-app-${env.GIT_COMMIT} -Dlog4j.configurationFile=log4j2-stdout.yml -Dserver.port=8080"]
                        ]
                    ],
                    pluginTimeout: "240" // default value is 120
                )
            }
            post {
              success {
                script {
                  appStarted = true
                }
              }
            }
        }
        stage('Integration tests') {
            when {
              expression { isDeployBranch() }
            }
            steps {
            	sleep time:90, unit: 'SECONDS'
              sh "mvn verify -Dskip.surefire.tests -Dfuse.sandbox.url=http://fuse-rest-dev-${env.GIT_COMMIT}.cfapps.io"
            }
            post {
               always {
                 junit 'target/failsafe-reports/*.xml'
               }
               failure {
                 echo 'Shutting down app'
                 doShutdown()
               }
            }
        }
        stage('Performance tests') {
            when {
            	expression { isDeployBranch() }
            }
            steps {
                echo 'Running performance tests...'
                echo 'No performance tests defined yet.'
            }
            post {
              failure {
                echo 'Shutting down app'
                doShutdown()
              }
            }
        }
        stage('Shutdown') {
            steps {
                echo 'Shutting down app'
                doShutdown()
            }
        }
    }
    // Sends slack notifications to Slack for success, failed or aborted builds. notifySlack() will check
    // whether the branch is a deploy branch before sending the message.
    // alertChannel - indicates whether a mention to @channel needs to be included in the message. Currently
    // done only for failed builds only.
    post {
        success {
            notifySlack("Successful!", 'fuse-java-builds', "good", false)
        }
        failure {
            notifySlack("Failed", 'fuse-java-builds', "danger", true)
        }
        aborted {
            notifySlack("Aborted", 'fuse-java-builds', "#d3d3d3", false)
        }
    }
}

def isDeployBranch() {
   if ( BRANCH_NAME.startsWith('feature/') || BRANCH_NAME.startsWith('hotfix/') || BRANCH_NAME.startsWith('bugfix/') ) {
       return false
   } else {
       return true
   }
}

def notifySlack(titlePrefix, channel, color, alertChannel) {
    if (!isDeployBranch()) {
      return
    }

    def messageText
    script {
      if (alertChannel) {
        messageText = "<!channel>: Triggered by ${author}"
      } else {
        messageText = "Triggered by ${author}"
      }
    }

    def jenkinsIcon = 'https://wiki.jenkins-ci.org/download/attachments/2916393/logo.png'

    def payload = JsonOutput.toJson([
        channel: channel,
        username: "Jenkins",
        icon_url: jenkinsIcon,
        attachments: [
            [
                title: "${titlePrefix} ${env.BRANCH_NAME}, build #${env.BUILD_NUMBER}",
                title_link: "${env.BUILD_URL}",
                color: "${color}",
                text: messageText,
                "mrkdwn_in": ["fields"],
                fields: [
                    [
                        title: "Branch",
                        value: "${env.GIT_BRANCH}",
                        short: true
                    ],
                    [
                        title: "Last Commit",
                        value: "${message}",
                        short: true
                    ]
                ]
            ]
        ]
    ])

    withCredentials([string(credentialsId: 'gala-slack-url', variable: 'slackURL')]) {
        sh "curl -X POST --data-urlencode \'payload=${payload}\' ${slackURL}"
    }
}

def author = ""
def getGitAuthor() {
    def commit = sh(returnStdout: true, script: 'git rev-parse HEAD')
    author = sh(returnStdout: true, script: "git --no-pager show -s --format='%an' ${commit}").trim()
}

def message = ""
def getLastCommitMessage() {
    message = sh(returnStdout: true, script: 'git log -1 --pretty=%B').trim()
}

def populateGlobalVariables() {
    getLastCommitMessage()
    getGitAuthor()
}

def appStarted = false;
def doShutdown() {
  if (isDeployBranch() && appStarted) {
    timeout(time: 2, unit: 'MINUTES') {
      withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'cf-credentials', usernameVariable: 'CF_USERNAME', passwordVariable: 'CF_PASSWORD']]) {
          // make sure the password does not contain single quotes otherwise the escaping fails
          sh "cf login -u ${CF_USERNAME} -p '${CF_PASSWORD}' -o FUSE -s development -a https://api.run.pivotal.io"
          sh "cf stop fuse-rest-dev-${env.GIT_COMMIT}"
          sh "cf delete fuse-rest-dev-${env.GIT_COMMIT} -r -f"
          sh 'cf logout'
      }
    }

    appStarted = false;
  }
}

def targetBranch=""
def populateTargetBranch() {
	echo "Populating target branch for branch: ${BRANCH_NAME}"
	targetBranch=(BRANCH_NAME == "develop") ? "" : "develop"
}

