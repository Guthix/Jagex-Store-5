# Jagex-Cache

This documentation will discuss the cache system used by the Jagex game engine. A cache is what games use to store large 
amounts of data that the game client needs to operate. Examples of this data are: models, skeletons, music, 
meta-data and compiled scripts. What is exactly stored in the cache can vary per game. The Jagex-Cache libary provides
an easy and fast way of retrieving and storing encoded data from the Jagex cache.

## File Overview
A Jagex cache has the following structure

![Cache stored on disk](images/FileOverview.svg)

The cache always needs atleast one .dat2 file to which we will refer as a data file and atleast one .idx255 file to 
which we will refer as the settings file. On top of having those two files the cache can also contain 255 archive
files which have the .idx_i file extension. All .idx files (including the settings file) contain pointers to data 
stored in the data file.

## High Level Cache Overview

The actually content of the cache looks very different from its file representation. A cache can have multiple 
archives which map to an archive file.

```mermaid
graph TB 
    C[Cache] --> A0[Archive 0]
    C --> AN[Archive N]
    A0 --> G0[Group 0]
    A0 --> GM[Group M]
    G0 --> F0[File 0]
    G0 --> FK[File K]
```

Archives however can have multiple groups inside them and every group can contain multiple files. Retrieving and
storing data in and to the cache is always done on an group basis. When storing for example a file to the cache the
group should be loaded from cache first, then it can be written back with the file added to the group. This is a 
constraint of the file store system used.