FROM debian:jessie

MAINTAINER Alan Arbizu <alan.arbizu+docker@gmail.com>

ENV DEBIAN_FRONTEND noninteractive


RUN apt-get update && \
    apt-get install --no-install-recommends -y maven wget git openssl ca-certificates && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/* && \
    cd /tmp && \
    wget -qO jdk8.tar.gz \
    --header "Cookie: oraclelicense=accept-securebackup-cookie" \
    http://download.oracle.com/otn-pub/java/jdk/8u60-b27/jdk-8u60-linux-x64.tar.gz && \
    tar xzf jdk8.tar.gz -C /opt && \
    mv /opt/jdk* /opt/java && \
    rm /tmp/jdk8.tar.gz && \
    update-alternatives --install /usr/bin/java java /opt/java/bin/java 100 && \
    update-alternatives --install /usr/bin/javac javac /opt/java/bin/javac 100 && \
    useradd \
        --create-home \
        --home-dir /home/app \ 
        --uid 1000 \
        --gid 1000 \
        --shell /bin/bash \
        app && \
    cd /home/app && \ 
    mkdir traffic && \
    cd traffic && \ 
    git clone https://github.com/aarbizu/traffic.git && \
    mvn clean compile assembly:single

EXPOSE 8888
