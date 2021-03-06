node {
    def build_name = 'jenkins/openmpi'

    stage 'Checkout'
    git branch: 'master', credentialsId: 'lammps-jenkins', url: 'https://github.com/lammps/lammps.git'
    git_commit = sh(returnStdout: true, script: 'git rev-parse HEAD').trim()

    step([$class: 'GitHubCommitStatusSetter', commitShaSource: [$class: 'ManuallyEnteredShaSource', sha: git_commit], contextSource: [$class: 'ManuallyEnteredCommitContextSource', context: build_name], reposSource: [$class: 'ManuallyEnteredRepositorySource', url: 'https://github.com/lammps/lammps.git'], statusResultSource: [$class: 'ConditionalStatusResultSource', results: [[$class: 'AnyBuildResult', message: 'building...', state: 'PENDING']]]])


    env.CCACHE_DIR= pwd() + '/.ccache'
    env.COMP     = 'mpicxx'
    env.MACH     = 'mpi'
    env.MPICMD   = 'mpirun -np 4'
    env.LMPFLAGS = '-sf off'
    env.LMP_INC  = '-I/usr/include/hdf5/serial -DFFT_KISSFFT -DLAMMPS_GZIP -DLAMMPS_PNG -DLAMMPS_JPEG -DLAMMPS_BIGBIG -Wall -Wextra -Wno-unused-result -Wno-unused-parameter -Wno-maybe-uninitialized'
    env.JPG_LIB  = '-L/usr/lib/x86_64-linux-gnu/hdf5/serial/ -ljpeg -lpng -lz'

    env.CC = 'gcc'
    env.CXX = 'g++'
    env.OMPI_CC = 'gcc'
    env.OMPI_CXX = 'g++'

    stage 'Setting up build environment'

    def envImage = docker.image('rbberger/lammps-testing:ubuntu_latest')

    try {
        docker.withRegistry('https://registry.hub.docker.com', 'docker-registry-login') {
            // ensure image is current
            envImage.pull()

            // use workaround (see https://issues.jenkins-ci.org/browse/JENKINS-34276)
            docker.image(envImage.imageName()).inside {
                sh 'ccache -C'
                sh 'ccache -M 5G'

                // clean up project directory
                sh '''
                make -C src clean-all
                make -C src yes-all
                make -C src no-lib
                make -C src no-user-omp
                make -C src no-user-intel
                make -C src no-user-smd
                '''

                stage 'Building libraries'

                sh '''
                make -C lib/colvars -f Makefile.g++ clean
                make -C lib/poems -f Makefile.g++ CXX="${COMP}" clean
                #make -C lib/voronoi -f Makefile.g++ CXX="${COMP}" clean
                make -C lib/awpmd -f Makefile.mpicc CC="${COMP}" clean
                make -C lib/meam -f Makefile.gfortran CC=gcc F90=gfortran clean
                make -C lib/h5md clean

                make -j 8 -C lib/colvars -f Makefile.g++ CXX="${COMP}"
                make -j 8 -C lib/poems -f Makefile.g++ CXX="${COMP}"
                #make -j 8 -C lib/voronoi -f Makefile.g++ CXX="${COMP}"
                make -j 8 -C lib/awpmd -f Makefile.mpicc CC="${COMP}"
                make -j 8 -C lib/meam -f Makefile.gfortran CC=gcc F90=gfortran
                make -j 8 -C lib/h5md
                '''

                stage 'Enabling modules'

                sh '''
                #make -C src yes-user-smd yes-user-molfile yes-compress yes-python
                make -C src yes-user-molfile yes-compress yes-python

                #make -C src yes-poems yes-voronoi yes-user-colvars yes-user-awpmd yes-meam
                make -C src yes-poems yes-user-colvars yes-user-awpmd yes-meam

                make -C src yes-user-h5md
                make -C src yes-mpiio yes-user-lb
                '''

                stage 'Compiling'
                sh 'make -j 8 -C src ${MACH} MPICMD="${MPICMD}" CC="${COMP}" LINK="${COMP}" LMP_INC="${LMP_INC}" JPG_LIB="${JPG_LIB}" TAG="${TAG}-$CC" LMPFLAGS="${LMPFLAGS}"'

                //stage 'Testing'
                //sh 'make -C src test-${MACH} MPICMD="${MPICMD}" CC="${COMP}" LINK="${COMP}" LMP_INC="${LMP_INC}" JPG_LIB="${JPG_LIB}" TAG="${TAG}-$CC" LMPFLAGS="${LMPFLAGS}"'

                sh 'ccache -s'
            }
        }
        step([$class: 'GitHubCommitStatusSetter', commitShaSource: [$class: 'ManuallyEnteredShaSource', sha: git_commit], contextSource: [$class: 'ManuallyEnteredCommitContextSource', context: build_name], reposSource: [$class: 'ManuallyEnteredRepositorySource', url: 'https://github.com/lammps/lammps.git'], statusResultSource: [$class: 'ConditionalStatusResultSource', results: [[$class: 'AnyBuildResult', message: 'build successful!', state: 'SUCCESS']]]])

    } catch (err) {
        echo "Caught: ${err}"
        currentBuild.result = 'FAILURE'
        step([$class: 'GitHubCommitStatusSetter', commitShaSource: [$class: 'ManuallyEnteredShaSource', sha: git_commit], contextSource: [$class: 'ManuallyEnteredCommitContextSource', context: build_name], reposSource: [$class: 'ManuallyEnteredRepositorySource', url: 'https://github.com/lammps/lammps.git'], statusResultSource: [$class: 'ConditionalStatusResultSource', results: [[$class: 'AnyBuildResult', message: 'build !', state: 'FAILURE']]]])
    }

    step([$class: 'WarningsPublisher', canComputeNew: false, consoleParsers: [[parserName: 'GNU Make + GNU C Compiler (gcc)']], defaultEncoding: '', excludePattern: '', healthy: '', includePattern: '', messagesPattern: '', unHealthy: ''])
    step([$class: 'AnalysisPublisher', canComputeNew: false, defaultEncoding: '', healthy: '', unHealthy: ''])

    if (currentBuild.result == 'FAILURE') {
        slackSend color: 'bad', message: "Build <${env.BUILD_URL}|#${env.BUILD_NUMBER}> of ${env.JOB_NAME} failed!"
    } else {
        slackSend color: 'good', message: "Build <${env.BUILD_URL}|#${env.BUILD_NUMBER}> of ${env.JOB_NAME} succeeded!"
    }
}
