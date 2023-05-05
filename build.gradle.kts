plugins {
    id("org.springframework.boot") version "3.0.0"
    id("io.spring.dependency-management") version "1.1.0"
    id("io.freefair.lombok") version "6.6.1"
    java
}

group = "net.uku3lig"
version = "1.0.0"
// sourceCompatibility = "17"

configurations {
    compileOnly {
        extendsFrom(configurations["annotationProcessor"])
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
    maven { url = uri("https://repo.spring.io/snapshot") }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    implementation("com.discord4j:discord4j-core:3.3.0-M1")
    implementation("com.moandjiezana.toml:toml4j:0.7.2")
    implementation("org.reflections:reflections:0.10.2")
    runtimeOnly("org.mariadb.jdbc:mariadb-java-client:3.0.8")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}
