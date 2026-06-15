#!/bin/sh
# ----------------------------------------------------------------------------
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
# ----------------------------------------------------------------------------

# ----------------------------------------------------------------------------
# Apache Maven Wrapper startup batch script, version 3.3.2
#
# Environment Variable Prerequisites
#
#   JAVA_HOME       Must point at your Java Development Kit installation.
#   MAVEN_OPTS      (Optional) Java runtime options used when Maven is executed.
#   MAVEN_SKIP_RC   (Optional) Flag to disable loading of mavenrc files.
# ----------------------------------------------------------------------------

if [ -z "$MAVEN_SKIP_RC" ] ; then

  if [ -f /usr/local/etc/mavenrc ] ; then
    . /usr/local/etc/mavenrc
  fi

  if [ -f /etc/mavenrc ] ; then
    . /etc/mavenrc
  fi

  if [ -f "$HOME/.mavenrc" ] ; then
    . "$HOME/.mavenrc"
  fi

fi

# OS specific support.
cygwin=false;
darwin=false;
mingw=false
case "$(uname)" in
  CYGWIN*) cygwin=true ;;
  MINGW*)  mingw=true;;
  Darwin*) darwin=true
    # Use /usr/libexec/java_home if available, otherwise fall back
    # to /Library/Java/Home
    # See https://developer.apple.com/library/mac/qa/qa1170/_index.html
    if [ -z "$JAVA_HOME" ]; then
      if [ -x "/usr/libexec/java_home" ]; then
        JAVA_HOME="$(/usr/libexec/java_home)"; export JAVA_HOME
      else
        JAVA_HOME="/Library/Java/Home"; export JAVA_HOME
      fi
    fi
    ;;
esac

if [ -z "$JAVA_HOME" ] ; then
  JAVACMD="$(\\unset -f command 2>/dev/null; \\command -v java)"
else
  JAVACMD="$JAVA_HOME/bin/java"
fi

if [ ! -x "$JAVACMD" ] ; then
  echo "Error: JAVA_HOME is not defined correctly." >&2
  echo "  We cannot execute $JAVACMD" >&2
  exit 1
fi

# traverses directory structure from process work directory to filesystem root
# first directory with .mvn subdirectory is considered project base directory
find_maven_basedir() {
  if [ -z "$1" ]
  then
    echo "Path not specified to find_maven_basedir"
    return 1
  fi

  basedir="$1"
  wdir="$1"
  while [ "$wdir" != '/' ] ; do
    if [ -d "$wdir"/.mvn ] ; then
      basedir=$wdir
      break
    fi
    # workaround for JBEAP-8://resolve path on Windows
    if [ -d "${wdir}" ]; then
      wdir="$(cd "$wdir/.." && pwd)"
    fi
    # end of workaround
  done
  printf '%s' "$(cd "$basedir" && pwd -P)"
}

# concatenates all lines of a file
concat_lines() {
  if [ -f "$1" ]; then
    # Remove \r in case we run on Windows within Git Bash
    # and have configured Git to bytes preserve line endings
    tr -s '\r\n' ' ' < "$1"
  fi
}

BASE_DIR=$(find_maven_basedir "$(dirname "$0")")
if [ -z "$BASE_DIR" ]; then
  exit 1;
fi

MAVEN_PROJECTBASEDIR=${MAVEN_BASEDIR:-"$BASE_DIR"}; export MAVEN_PROJECTBASEDIR
if [ "$MVNW_VERBOSE" = true ]; then
  echo $MAVEN_PROJECTBASEDIR
fi

##########################################################################################
# Extension to allow automatically downloading the maven-wrapper.jar from Maven-central
# This allows using the maven wrapper in projects that prohibit checking in binary data.
##########################################################################################
wrapperJarPath="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.jar"
if [ -r "$wrapperJarPath" ]; then
    if [ "$MVNW_VERBOSE" = true ]; then
        echo "Found $wrapperJarPath"
    fi
else
    if [ "$MVNW_VERBOSE" = true ]; then
        echo "Couldn't find $wrapperJarPath, downloading it ..."
    fi
    if [ -n "$MVNW_REPOURL" ]; then
        wrapperUrl="$MVNW_REPOURL/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar"
    else
        wrapperUrl="https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar"
    fi
    while IFS="=" read -r key value; do
      # Remove '\r' from value to enable usage on Windows
      value=$(echo "$value" | tr -d '\r')
      case "$key" in (wrapperUrl) wrapperUrl="$value"; break ;;
      esac
    done < "$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.properties"
    if [ "$MVNW_VERBOSE" = true ]; then
        echo "Downloading from: $wrapperUrl"
    fi
    if command -v wget > /dev/null; then
        if [ "$MVNW_VERBOSE" = true ]; then
          echo "Found wget ... using wget"
        fi
        [ ${QUIET:=0} -eq 1 ] && WGET_ARGS="-q" || WGET_ARGS=""
        wget $WGET_ARGS "$wrapperUrl" -O "$wrapperJarPath"
    elif command -v curl > /dev/null; then
        if [ "$MVNW_VERBOSE" = true ]; then
            echo "Found curl ... using curl"
        fi
        [ ${QUIET:=0} -eq 1 ] && CURL_ARGS="-s" || CURL_ARGS=""
        curl $CURL_ARGS -o "$wrapperJarPath" "$wrapperUrl"
    else
        if [ "$MVNW_VERBOSE" = true ]; then
            echo "Falling back to using Java to download"
        fi
        # Use Java to download
        javaSource="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/MavenWrapperDownloader.java"
        javaClass="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/MavenWrapperDownloader.class"
        # For Cygwin and MinGW, switch paths to Windows format before running javac
        if $cygwin || $mingw ; then
          [ -n "$javaSource" ] && javaSource="$(cygpath --path --windows "$javaSource")"
          [ -n "$javaClass" ] && javaClass="$(cygpath --path --windows "$javaClass")"
        fi
        cat > "$javaSource" <<-EOF
import java.net.URL;
import java.io.File;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
public class MavenWrapperDownloader {
    public static void main(String[] args) {
        try {
            URL url = new URL(args[0]);
            File file = new File(args[1]);
            ReadableByteChannel channel = Channels.newChannel(url.openStream());
            java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
            fos.getChannel().transferFrom(channel, 0, Long.MAX_VALUE);
            fos.close();
            channel.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
EOF
        javac "$javaSource"
        java -cp "$MAVEN_PROJECTBASEDIR/.mvn/wrapper" MavenWrapperDownloader "$wrapperUrl" "$wrapperJarPath"
        rm -f "$javaSource" "$javaClass"
    fi
fi
##########################################################################################
# End of extension
##########################################################################################

# If specified, validate the SHA-256 sum of the Maven wrapper jar file
wrapperSha256Sum=""
while IFS="=" read -r key value; do
  case "$key" in (wrapperSha256Sum) wrapperSha256Sum=$value; break ;;
  esac
done < "$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.properties"
if [ -n "$wrapperSha256Sum" ]; then
  wrapperShaPath="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.jar"
  if command -v sha256sum > /dev/null; then
    if [ "$(printf '%s  %s' "$wrapperSha256Sum" "$wrapperShaPath" | sha256sum -c --status)" ]; then
      echo "Checksum OK"
    else
      echo "Error: Failed to validate Maven wrapper SHA-256, your Maven wrapper might be compromised." >&2
      echo "Investigate or delete $wrapperShaPath to attempt a clean download." >&2
      echo "If you updated your Maven version, you need to update the specified wrapperSha256Sum property." >&2
      exit 1
    fi
  elif command -v shasum > /dev/null; then
    if [ "$(printf '%s  %s' "$wrapperSha256Sum" "$wrapperShaPath" | shasum -a 256 -c --status)" ]; then
      echo "Checksum OK"
    else
      echo "Error: Failed to validate Maven wrapper SHA-256, your Maven wrapper might be compromised." >&2
      echo "Investigate or delete $wrapperShaPath to attempt a clean download." >&2
      echo "If you updated your Maven version, you need to update the specified wrapperSha256Sum property." >&2
      exit 1
    fi
  else
    echo "Checksum validation requires sha256sum or shasum, but neither is available." >&2
    echo "Skipping checksum validation." >&2
  fi
fi

# Maven arguments
# Add default JVM options here. You can also use JAVA_OPTS and MAVEN_OPTS to pass JVM options to this script.
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

MAVEN_OPTS="$(concat_lines "$MAVEN_PROJECTBASEDIR/.mvn/jvm.config") $MAVEN_OPTS"

# For Cygwin or MinGW, switch paths to Windows format before running java
if $cygwin || $mingw ; then
  [ -n "$JAVA_HOME" ] &&
    JAVA_HOME=$(cygpath --path --windows "$JAVA_HOME")
  [ -n "$CLASSPATH" ] &&
    CLASSPATH=$(cygpath --path --windows "$CLASSPATH")
  [ -n "$MAVEN_PROJECTBASEDIR" ] &&
    MAVEN_PROJECTBASEDIR=$(cygpath --path --windows "$MAVEN_PROJECTBASEDIR")
fi

# Provide a "standardized" way to retrieve the CLI args that will
# work with both Windows and non-Windows executions.
MAVEN_CMD_LINE_ARGS="$MAVEN_CONFIG $*"
export MAVEN_CMD_LINE_ARGS

WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain

# shellcheck disable=SC2086
exec "$JAVACMD" \
  $MAVEN_OPTS \
  $MAVEN_DEBUG_OPTS \
  -classpath "$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.jar" \
  "-Dmaven.multiModuleProjectDirectory=${MAVEN_PROJECTBASEDIR}" \
  ${WRAPPER_LAUNCHER} $MAVEN_CMD_LINE_ARGS
