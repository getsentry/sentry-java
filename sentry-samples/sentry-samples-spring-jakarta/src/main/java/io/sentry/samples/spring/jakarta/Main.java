package io.sentry.samples.spring.jakarta;

import java.io.File;
import java.io.IOException;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;

public class Main {

  public static void main(String[] args) throws LifecycleException, IOException {
    String webappPath = "./build/libs";
    String warName = "sentry-samples-spring-jakarta-0.0.1-SNAPSHOT";
    File war = new File(webappPath + "/" + warName + ".war");

    Tomcat tomcat = new Tomcat();
    tomcat.setPort(8080);
    tomcat.getConnector();

    tomcat.addWebapp("/" + warName, war.getCanonicalPath());
    tomcat.start();
    tomcat.getServer().await();
  }
}
