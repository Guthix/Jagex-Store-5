# File Store

The file store describes how the groups and files are stored on disk.
The structure of the files on disk looks as follows: 

![Cache stored on disk](images/FileOverview.svg)

A cache should always have at least 1 data file(.dat2), 1 settings 
file(.idx255) and 1 archive(.idxi) file. There can be multiple 
archive files in one cache. Each archive file represents a type of
data. Examples of data types are models, compiled scripts, configuration
data, songs. The amount of archives in a cache depends on the game.
Archive files should always be sequential and can go from .idx0 up to
.idx254. It is thus not possible to have an .idx0 file and an .idx2 file
without an .idx1 file.

When looking at the size of the cache you might notice that the .idx 
files are really small. This is because all cache data is stored in the
data file. The archive and settings files just contain references
to the data stored in the data file. Every .idx file including the 
settings file contains a list of pointers which points to locations
in the data file.

## Index files

Index files contain pointers to data in the data files. Both archive
files and settings files are considered index files. Index files have
the .idxi file extension where is the a number ranging from 0 up to 255.
Index files have the following structure:

![Index File](images/IndexFile.svg)

An index file contains a list of indexes. Each index represents a pointer
to container data in the data file. For archive index files an index
points to a group. For the master index file an index points to 
archive settings data. An index contains the data size of the container
and the first sector that it should start reading from the data file.
It looks as follows:

![Index Encoding](images/Index.svg)

## Data file

The data file is where the actual cache data is stored. The data file is
a list of sectors. sectors are of fixed size and multiple sectors can
represent a file. A data file has the following structure:

![Data File](images/DataFile.svg)

sectors are made out of 2 parts. The sector header and the data part.
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