OpenHiCAMM
==========

Microsocope automation plugin for Micromanager (http://micromanager.org) and Fiji (http://fiji.sc).

News
----
July 2017
Added more documentation.

May 2015
Removed minor bugs and vastly improved stability for hardware control. All modules tested and functional. 

February 2015
First public release.

OpenHiCAMM Installation Instructions
======================================

### Build and install fiji and micromanager as outlined in the script file for your OS:
```
./scripts/install-fiji-micromanager-macosx
```
### Or: 
```
./scripts/install-fiji-micromanager-linux
```

### Set the fiji.dir variable (set to your Fiji installation folder):
```
echo "fiji.dir=/Applications/Fiji.app" >./build.properties
```

### Add the micromanager MMJ_.jar and MMCore.jar files to the user's local maven repo.
### Change the following command to point to your micromanager source directory that has already been built with make:
```
./scripts/add-mmj-jar.sh ~/src/micromanager
```

### Build and install OpenHiCAMM:
```
mvn install
```

Notes
=====

In the `patches/` folder, you will find some optional patches for the micro-manager source tree. See the `README.md` file in the `patches/` folder for more information.

More detailed usage instructions can be found in the `docs/` folder.
