FROM openjdk

WORKDIR /app

MAINTAINER pm@miu.edu

COPY . .

RUN chmod -R 0777 .

RUN ls ~/ -lsa

RUN mkdir -p ~/.m2/repository/com/pm/ecommerce/entities/1.0-SNAPSHOT

RUN cp ./src/main/resources/pm-entities.jar ~/.m2/repository/com/pm/ecommerce/entities/1.0-SNAPSHOT/entities-1.0-SNAPSHOT.jar

RUN ./mvnw clean -DskipTests -e

RUN ./mvnw package -DskipTests -e

RUN ls ./target

RUN cp ./target/*.jar ./service.jar

ENTRYPOINT ["java","-jar","./service.jar"]