FROM centos:7

RUN yum update -y && yum groupinstall -y "Development tools"

RUN yum install -y openmpi openmpi-devel
RUN yum install -y clang
RUN yum install -y mpich-devel

ENV MODULEPATH /etc/modulefiles:/usr/share/modulefiles:/usr/share/Modules/modulefiles

COPY z00_StdEnv.sh /etc/profile.d/z00_StdEnv.sh

RUN yum install -y openssh

ENV PATH /usr/lib/ccache:$PATH

RUN groupadd -g 114 jenkins

RUN useradd -u 106 -g 114 -ms /bin/bash jenkins

RUN yum install -y git

RUN yum install -y ccache gcc-gfortran fftw-devel turbojpeg-devel libjpeg-turbo-devel libjpeg-turbo libpng-devel python-devel blas-devel lapack-devel hdf5-devel

RUN yum install -y redhat-rpm-config

RUN yum install -y @development-tools fedora-packager rpmdevtools

RUN yum install -y wget

RUN wget http://www.msweet.org/files/project1/htmldoc-1.8.29-source.tar.gz -O /tmp/htmldoc-1.8.29-source.tar.gz && cd /tmp && tar xvzf /tmp/htmldoc-1.8.29-source.tar.gz && cd htmldoc-1.8.29 && ./configure && make install && cd && rm -rf /tmp/htmldoc*

RUN yum install -y epel-release

RUN yum install -y python34 python-virtualenv python-pip

RUN yum install -y which

RUN pip install --upgrade pip virtualenv

RUN yum update -y && yum install -y texlive

USER jenkins

CMD /bin/bash
