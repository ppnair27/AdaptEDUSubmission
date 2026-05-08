FROM maven:3.9-eclipse-temurin-17

WORKDIR /app

COPY . .

RUN cd AdaptEDUv0507/SpringBootTest && mvn clean install

CMD ["java", "-jar", "AdaptEDUv0507/SpringBootTest/target/*.jar"]