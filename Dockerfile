FROM openjdk:8

WORKDIR /home/rundeck-slack-plugin
CMD ["bash", "./gradlew"]
