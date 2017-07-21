OpenHiCAMM
==========

Microsocope automation plugin for Micromanager (http://micromanager.org) and Fiji (http://fiji.sc).

News
----
May 2015
Removed minor bugs and vastly improved stability for hardware control. All modules tested and functional. 

February 2015
First public release.


OpenHiCAMM Installation Instructions
======================================

# build and install fiji and micromanager as outlined in the script file:
./install-fiji-micromanager.sh

# (Optional) install graph-easy for logging workflow and task graphs, e.g.:
brew install cpanminus && sudo cpanm Graph::Easy  # Mac
or
sudo apt-get install cpanminus && sudo cpanm Graph::Easy  # Ubuntu/Debian GNU/Linux

# set the fiji.dir variable (set to your Fiji installation folder)
echo "fiji.dir=/Applications/Fiji.app" >./build.properties

# add the micromanager MMJ_.jar and MMCore.jar files to the user's local maven repo

# change the following command to point to your micromanager source
# directory that has already been built with make
./add-mmj-jar.sh ~/src/micromanager/micromanager

# build and install OpenHiCAMM
mvn install
