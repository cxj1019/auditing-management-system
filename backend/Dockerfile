# 多阶段构建:阶段一用 Maven 编译打包,阶段二仅含 JRE 运行时(镜像小)
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
# 先拉依赖(利用 Docker 层缓存,源码变更时无需重复下载)
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

# 运行阶段
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/accounting-firm-0.0.1-SNAPSHOT.jar app.jar
# Render 通过 PORT 环境变量指定监听端口(应用已支持 ${PORT:8080})
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java -jar app.jar --spring.profiles.active=prod"]
