Description of MicroManager patches in this folder:
===================================================

```
autofocus.patch
```

Patches for obtaining autofocus metrics used in the paper.

```
disable-disk-space-check.patch
```

macOS returns inaccurate available disk space amount for network drives. This patch disables the disk space check in Micro-Manager as a workaround.

```
linux32-gphoto-setpaths.patch
```

This patch tells Micro-Manager to search directories relative to the executable for gphoto libraries in 32-bit linux systems. Used to build the OpenHiCAMM Fiji update site at `http://fruitfly.org/openhicamm`.

```
linux64-gphoto-setpaths.patch
```

This patch tells Micro-Manager to search directories relative to the executable for gphoto libraries in 64-bit linux systems. Used to build the OpenHiCAMM Fiji update site at `http://fruitfly.org/openhicamm`.

```
prior-xystage-multiple-tries.patch
```

Attemt to send commands to the stage multiple times, with a pause between each attempt. This helps make interaction with the stage device more robust.

```
simplecam-dont-crash-if-mkstemps-fails.patch
```

Workaround for an assertion error. Probably not necessary.

```
simplecam-multiple-tries.patch
```

Attempt to send commands to the camera device multiple times, with a pause between each attempt. This helps make interaction with the camera device more robust.

```
zeisscan-zdrive-multiple-tries.patch
```

Attempt to send focus commands to the microscope's focus drive multiple times, with a pause between each attempt. This helps make interaction with the microscope's focus drive device more robust.
