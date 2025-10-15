package io.sentry.samples.spring7;

import java.io.File;
import java.io.IOException;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;

public class Main {

  public static void main(String[] args) throws LifecycleException, IOException {
    File webappsDirectory = new File("./tomcat.8080/webapps");
    if (!webappsDirectory.exists()) {
      boolean didCreateDirectories = webappsDirectory.mkdirs();
      if (!didCreateDirectories) {
        throw new RuntimeException(
            "Failed to create directory required by Tomcat: " + webappsDirectory.getAbsolutePath());
      }
    }

    String pathToWar = "./build/libs";
    String warName = "sentry-samples-spring-7-0.0.1-SNAPSHOT";
    File war = new File(pathToWar + "/" + warName + ".war");

    Tomcat tomcat = new Tomcat();
    tomcat.setPort(8080);
    tomcat.getConnector();

    tomcat.addWebapp("/" + warName, war.getCanonicalPath());
    tomcat.start();
    tomcat.getServer().await();
  }
}
