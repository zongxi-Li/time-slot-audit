#!/bin/sh
# Local Maven wrapper that bypasses the broken msys path translation in the
# official mvn/mvn.cmd launchers (which mangle MAVEN_HOME into /d/... and break
# the classpath passed to the Windows java.exe).
M2="D:/maven/apache-maven-3.9.12-bin/apache-maven-3.9.12"
JAVA="C:/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot/bin/java.exe"
PROJECT_DIR="${maven_multi:-$(pwd)}"
exec "$JAVA" \
  -classpath "$M2/boot/plexus-classworlds-2.9.0.jar" \
  -Dmaven.home="$M2" \
  "-Dclassworlds.conf=$M2/bin/m2.conf" \
  -Dmaven.multiModuleProjectDirectory="$PROJECT_DIR" \
  org.codehaus.plexus.classworlds.launcher.Launcher "$@"
