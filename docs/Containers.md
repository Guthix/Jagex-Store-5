# Containers

Containers are the smallest possible unit which can be read and written
to the cache. Containers are optionally encrypted and optionally 
compressed blocks of data. The data can either represent a group
(loaded from archive indexes) or archive settings (loaded from
the master index).

Containers can be encrypted with an XTEA block cipher. The cache also 
supports 3 different type of compressions: BZIP2, GZIP and LZMA. Some
games only support a subset of those compressions. Encryption and 
compression is optional.

![Container Encoding](images/Container.svg)

The type of compression is indicated by an opcode with the following 
mapping.

| Opcode | Compression |
|--------|-------------|
| 0      | None        |
| 1      | BZIP2       |
| 2      | GZIP        |
| 3      | LZMA        |

Every container also contains the compressed and uncompressed size. The
compressed size is used to determine how many bytes needs to be 
compressed when reading and the uncompressed size can be used for 
verification. Containers can also optionally contain a version. This 
version is different from the archive and archive settings version.