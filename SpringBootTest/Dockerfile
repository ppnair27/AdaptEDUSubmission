FROM maven:3.9-eclipse-temurin-21

WORKDIR /app

COPY . .

RUN mvn clean install

CMD java -jar target/*.jar