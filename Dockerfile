FROM gradle:8.5-jdk17 AS builder
WORKDIR /app
COPY . .
# 테스트는 건너뛰고 빌드만 빠르게 수행
RUN ./gradlew bootJar -x test

# 2. 실행 단계
FROM eclipse-temurin:17-jdk
WORKDIR /app
# 빌드 단계에서 만들어진 jar 파일을 가져옴
COPY --from=builder /app/build/libs/*.jar app.jar

# 도커 컨테이너가 시작될 때 실행할 명령어
ENTRYPOINT ["java", "-jar", "app.jar"]