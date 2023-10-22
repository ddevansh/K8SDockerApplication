FROM openjdk:18
 
WORKDIR /DockerK8SCopy

COPY ./target/DockerK8S-0.0.1-SNAPSHOT.jar /app

EXPOSE 8080

CMD ["java", "-jar", "DockerK8S-0.0.1-SNAPSHOT.jar"]