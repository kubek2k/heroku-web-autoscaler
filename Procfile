web: $JAVA_COMMAND $JAVA_OPTS -Ddw.server.type=simple -Ddw.server.connector.type=http -Ddw.server.connector.port.port=$PORT -jar target/heroku-web-autoscaler.jar server etc/settings.yml
consume_stats: $JAVA_COMMAND $JAVA_OPTS -jar target/heroku-web-autoscaler.jar consume_stats etc/settings.yml
observe: $JAVA_COMMAND $JAVA_OPTS -jar target/heroku-web-autoscaler.jar observe etc/settings.yml

