FROM adoptopenjdk:11-jre-hotspot
RUN mkdir /opt/app
COPY target/traffic-1.0-SNAPSHOT-jar-with-dependencies.jar /opt/app/traffic.jar
CMD ["java", "-jar", "/opt/app/traffic.jar"]
EXPOSE 8888
