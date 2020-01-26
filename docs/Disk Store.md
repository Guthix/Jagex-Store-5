# File Store

The disk store describes how the groups and files are stored on disk.
The structure of the files on disk looks as follows: 

![Cache stored on disk](images/FileOverview.svg)

A JS5 cache should always have at least 1 data file(.dat2), 1 settings 
file(.idx255) and 1 archive(.idxi) file. There can be multiple 
archive files in one cache. Each archive file represents a type of
data. Examples of data types are models, compiled scripts, configuration
data, songs.

## Index files

Index files contain pointers to data in the data files. We call these pointers
indices. There are 2 types of index files: archive index files and settings
index files. Archive index files have the file extension .idxi where i ranges from
0 to 254 inclusive. Archive index files have to be sequential. A cache containing
only index file .idx0 and .idx2 without .idx1 is invalid. The settings index file 
always has the file extension .idx255 and there can only be one per cache. We also
call the settings index file the master index file.

![Index File](images/IndexFile.svg)

An index file contains a list of indexes. Each index represents a pointer
to container data in the data file. For archive index files an index
points to a group. For the master index file an index points to 
archive settings data. An index contains the data size of the container
and the first sector that it should start reading from the data file.
It looks as follows:

![Index Encoding](images/Index.svg)

If data is removed from the cache the data size can be set to 0.

## Data file

The data file is where the actual cache data is stored. The data file is
a list of sectors. sectors are of fixed size and multiple sectors can
represent a container. A data file has the following structure:

![Data File](images/DataFile.svg)

Sectors are made out of 2 parts. The sector header and the data part.
The sector header contains the id of the index file it belongs to
(the file extension number), the next sector which belongs to the same
container, its position in the list of sectors belonging to that 
container and the container id it belongs to.

The container id, sector position and index file id are used for
verification while reading. The next sector position is for knowing where
the next sector of that container is. This means that sectors don't have
to be stored sequentially in the data file.

A sector is encoded as follows:

![Normal sector Encoding](images/Normalsector.svg)

This sector encoding however can only store sectors with a container ids
up until 65535 (maximum unsigned short). So container with higher ids are 
stored with extended sectors which look as follows:

![Extended sector Encoding](images/Extendedsector.svg)