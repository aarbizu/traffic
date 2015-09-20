FROM debian:jessie

MAINTAINER Alan Arbizu <alan.arbizu+docker@gmail.com>

ENV DEBIAN_FRONTEND noninteractive

RUN apt-get update && \
    apt-get install --no-install-recommends -y maven wget git openssl ca-certificates && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

RUN cd /tmp && \
    wget -qO jdk8.tar.gz \
    --header "Cookie: oraclelicense=accept-securebackup-cookie" \
    http://download.oracle.com/otn-pub/java/jdk/8u60-b27/jdk-8u60-linux-x64.tar.gz && \
    tar xzf jdk8.tar.gz -C /opt && \
    mv /opt/jdk* /opt/java && \
    rm /tmp/jdk8.tar.gz && \
    update-alternatives --install /usr/bin/java java /opt/java/bin/java 3000 && \
    update-alternatives --install /usr/bin/javac javac /opt/java/bin/javac 3000 && \
    update-alternatives --install /usr/bin/jar jar /opt/java/bin/jar 3000 && \
	groupadd \
		--gid 1000 \
		app && \
    useradd \
        --create-home \
        --home-dir /home/app \ 
        --uid 1000 \
        --gid 1000 \
        --shell /bin/bash \
        app && \
    cd /home/app && \ 
    git clone https://github.com/aarbizu/traffic.git && \
	cd traffic && \
    mvn clean compile assembly:single

USER app
ENV HOME /home/app
EXPOSE 8888
