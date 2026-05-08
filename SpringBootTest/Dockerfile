FROM maven:3.9-eclipse-temurin-17

WORKDIR /app

COPY . .

WORKDIR /app/AdaptEDUv0507/SpringBootTest

RUN mvn clean install

CMD ["java", "-jar", "target/*.jar"]