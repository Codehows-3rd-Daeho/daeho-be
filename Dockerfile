# 빌드 스테이지
FROM gradle:8.5-jdk21 AS builder
# Maven 사용 시: FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# Gradle 사용 시
COPY build.gradle settings.gradle ./
COPY gradle gradle
COPY gradlew ./

# gradlew 실행 권한 부여
RUN chmod +x ./gradlew

RUN ./gradlew dependencies --no-daemon

# Maven 사용 시 (위 4줄 대신 아래 2줄 사용)
# COPY pom.xml ./
# RUN mvn dependency:go-offline

# 소스 코드 복사 및 빌드
COPY src ./src

# Gradle 빌드
RUN chmod +x ./gradlew && ./gradlew bootJar --no-daemon
# Maven 빌드 시: RUN mvn clean package -DskipTests

# 런타임 스테이지
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# 빌드된 JAR 파일 복사
COPY --from=builder /app/build/libs/*.jar app.jar
# Maven 빌드 시: COPY --from=builder /app/target/*.jar app.jar

# 포트 노출
EXPOSE 8080

# 환경변수 설정
ENV JAVA_OPTS="-Xms512m -Xmx1024m"

# 애플리케이션 실행
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]