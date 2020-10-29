FROM openjdk

MAINTAINER pm@miu.edu
WORKDIR /app

COPY . .

RUN chmod -R 0777 ./

RUN ./mvnw install -DskipTests

RUN ls ./target

RUN cp ./target/*.jar ./service.jar

ENTRYPOINT ["java","-jar","./service.jar"]