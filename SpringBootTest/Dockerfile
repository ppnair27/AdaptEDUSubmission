FROM maven:3.9-eclipse-temurin-17

WORKDIR /app

COPY AdaptEDUv0507/SpringBootTest /app

RUN mvn clean install

CMD ["java", "-jar", "target/*.jar"]