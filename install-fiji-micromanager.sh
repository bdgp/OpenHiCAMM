#!/usr/bin/env bash
set -o errexit
# see https://www.micro-manager.org/wiki/Build_on_MacOS_X#Getting_Micro-Manager_source_code
# and https://www.micro-manager.org/wiki/Download%20Micro-Manager_Latest%20Release

# install homebrew and project dependencies
#ruby -e "$(curl -fsSL https://raw.github.com/Homebrew/homebrew/go/install)"
brew install autoconf automake libtool pkg-config swig subversion boost libusb-compat hidapi libdc1394 libgphoto2 freeimage opencv python git

# install latest Java from Oracle
#brew tap caskroom/cask
#brew install brew-cask
brew cask install java

pip install numpy

# install fiji from source
( git clone git://github.com/fiji/fiji /Applications/Fiji.app
  cd /Applications/Fiji.app
  git config remote.origin.url git://fiji.sc/fiji.git
  git pull
  # build & install deps
  ./Build.sh
  # add the following to fiji/Contents/Info.plist:
  defaults write "$PWD"/Contents/Info fiji -dict-add JVMOptions "-Dorg.micromanager.plugin.path=$PWD/mmplugins -Dorg.micromanager.autofocus.path=$PWD/mmautofocus -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=4000"
  defaults write "$PWD"/Contents/Info CFBundleExecutable -string ImageJ-macosx
  plutil -convert xml1 Contents/Info.plist
)

# install latest imagej-ui-swing
( git clone https://github.com/imagej/imagej-ui-swing
  cd imagej-ui-swing
  mvn -Dimagej.app.directory=/Applications/Fiji.app -Ddelete.other.versions=true
)

# set JAVA_HOME
export JAVA_HOME="$(/usr/libexec/java_home)"
# set Java source and compile targets to version 1.6 for compatibility with
# Micro-Manager and Fiji
export JAVACFLAGS='-source 1.6 -target 1.6'

#install micro-manager from source

( mkdir micromanager
  cd micromanager
  svn --username guest --password guest checkout https://valelab.ucsf.edu/svn/micromanager2/trunk micromanager
  mkdir 3rdpartypublic
  svn --username guest --password guest checkout https://valelab.ucsf.edu/svn/3rdpartypublic/classext 3rdpartypublic/classext

  cd micromanager

  # Configure, build, and install
  ./autogen.sh
  ./configure --enable-imagej-plugin=/Applications/Fiji.app --with-ij-jar=/Applications/Fiji.app/jars/ij-1.49b.jar
  make -j
  make install
)

# NOTE: ImageJ launcher will fail if your PATH contains any null elements, # e.g. ::
# so, make sure your PATH is properly formatted

# There are 9 jars that are shared between Fiji and Micro-Manager, 5 of which
# potentially conflict. If the Fiji version is newer, it's possibly OK
# since the Fiji version gets loaded first, and the newer version is probably
# backward-compatible (unless any breaking changes were introduced):
  # jar-name mm-version/fiji-verison:

  # No conflict:
    # bsh 2.0b4/2.0b4 - Same version, OK
    # clojure 1.3.0/1.3.0 - Same version, OK
    # commons-math 2.0/commons-math3 3.2 - Different namespaces, OK
    # scifio (unmarked version, was committed on 20120921)/0.15.3 - Different namespaces, OK

  # UI-related:
    # jcommon 1.0.16/1.0.17 - Different versions, Fiji is newer, maybe OK?
    # jfreechart 1.0.13/1.0.14 - Different versions, Fiji is newer, maybe OK?
    # miglayout 4.0-swing/3.7.3.1-swing - Different versions, Fiji is older, could cause problems
    # rsyntaxtextarea 2.5.2/2.0.4.1 - Different versions, Fiji is older, CONFIRMED CAUSES PROBLEMS
  # Logging:
    # slf4j-api 1.7.1/1.7.6 - Different versions, Fiji is newer, maybe OK?

# Of all of the shared jars, only rsyntaxtextarea is confirmed to conflict, so
# hide the Fiji version and symlink the MM version in its place.
# You have to use the exact jar names for the replacements.
mv -v /Applications/Fiji.app/jars/rsyntaxtextarea-2.0.4.1.jar /Applications/Fiji.app/jars/rsyntaxtextarea-2.0.4.1.jar.bak
ln -sv /Applications/Fiji.app/plugins/Micro-Manager/rsyntaxtextarea.jar /Applications/Fiji.app/jars/rsyntaxtextarea-2.0.4.1.jar
