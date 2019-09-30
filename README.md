# Jagex Store 5
[![Build Status](https://img.shields.io/travis/com/guthix/Jagex-Store-5?style=flat-square)](https://travis-ci.com/guthix/Jagex-Store-5)
[![License](https://img.shields.io/github/license/guthix/Jagex-Store-5?style=flat-square)](https://github.com/guthix/Jagex-Store-5/blob/master/LICENSE)
[![JDK](https://img.shields.io/badge/JDK-Java%209%2B-blue?style=flat-square)]()

A library for reading Jagex Store 5 (JS5) game files. Jagex Store 5 is a 
protocol used for storing game assets in the Runetek game engine made by
Jagex Games Studio. A set of game assets is also called a cache and can
contain all type of game information like client scripts, models, 
animations, sounds etc. This library allows reading and writing the 
encoded file data from and to the cache. Which can later be decoded into 
game assets.

## Structure
A cache contains various files on disk which do not directly correlate 
with game asset files. Game assets can be compressed and encrypted and
can have meta-data attached to them.

### Disk Structure
A JS5 cache is made out of multiple files on disk. First there are the
index files. Index files have an .idx extension suffixed by a number
ranging from 0 until 255. An index file contains a list of pointers(
also called indices) to the game asset data in the data file which has 
the .dat2 file extension. The .idx255 is a special index file, it points
to meta-data data for the game assets. The meta-data are called settings 
and contain information such as version, crc and name hashes.

![JS5 cache stored on disk](docs/images/FileOverview.svg)

All other .idx file contain indices that point to archive data. An 
archive contains game assets of a certain type. An archive can for 
example contain all the model data for a game or all the client scripts 
for a game. The data for both the settings and archives is stored in the 
data file of the cache.

### Cache Structure
The actual structure of the cache is different than it appears on disk.
Each archive contains a set of groups and each group contains a set of
files. A file in a group represents a single game asset. Each index in
an index file points to a group. And index can thus point to multiple
game assets files. Groups are the smallest data volume that can be
read/written from/to the cache. When you want to change a file in a 
group you have to read/write the whole group. Archives, groups and files
all have unique ids in their own domains. Each file has thus an unique 
id in their group and each group has an unique id in their archive. Both 
groups and files can have names and can also be retrieved by their names. 
The names however are stored as a hash inside the cache and can thus not 
be retrieved from the cache.

![JS5 cache content](docs/images/HighLevelOverview.svg)

An uncompressed, unencrypted and undecoded volume from the cache is called 
a container. The index in an index file points to a container which can 
later be decoded into either a group or archive settings data. A 
container can be compressed and encrypted. The encryption uses XTEA
encryption and the compression algorithm can be either BZIP2, GZIP
or LZMA. Both compression and encryption is optional. 

## Usage
The Guthix JS5 library allows for reading and writing groups from and to
the cache. This can be done by creating a `Js5Cache` object. The 
`Js5Cache` requires a `Js5ContainerReader` and an optional `Js5ContainerWriter`. 
The Guthix library provides 2 different readers/writers. First is 
`Js5DiskStore` which is both capable of being a reader and a writer.
The `Js5DiskStore` is used for reading/writing from and to a cache on 
disk. The other option is to use the `Js5SocketReader` which can only 
read. The `Js5SocketReader` can be used for reading container data from
a JS5 server. Writing to a JS5 server is not possible.

## Group and file names
The names of groups and files are stored as hashes. This makes it so 
they can't be retrieved from the cache. To retrieve a file by name you 
therefore need to know its name. Names can by found by performing 
dictionary attacks, rainbow table attacks to crack the hashes.