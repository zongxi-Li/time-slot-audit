#!/bin/sh
# Local Maven wrapper that bypasses the broken msys path translation in the
# official mvn/mvn.cmd launchers (which mangle MAVEN_HOME into /d/... and break
# the classpath passed to the Windows java.exe).
M2="D:/apache-maven-3.9.16-bin/apache-maven-3.9.16"
JAVA="C:/Program Files/Microsoft/jdk-21.0.12.101-hotspot/bin/java.exe"
PROJECT_DIR="${maven_multi:-$(pwd)}"
exec "$JAVA" \
  -classpath "$M2/boot/plexus-classworlds-2.11.0.jar" \
  -Dmaven.home="$M2" \
  "-Dclassworlds.conf=$M2/bin/m2.conf" \
  -Dmaven.multiModuleProjectDirectory="$PROJECT_DIR" \
  org.codehaus.plexus.classworlds.launcher.Launcher "$@"
