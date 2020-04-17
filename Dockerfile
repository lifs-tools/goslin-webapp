FROM openjdk:11-jre-slim
MAINTAINER Nils Hoffmann &lt;nils.hoffmann@isas.de&gt;

EXPOSE 8083
VOLUME /tmp
ARG JAR_FILE
ARG APP_NAME
ADD target/${JAR_FILE} app.jar
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app.jar"]
