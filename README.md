# Wasabi database migration missing hashes

This repository hosts a serialized [MapDB](http://www.mapdb.org/) database containing the relations between the Keccak256 hashes of the stored contract keys and its corresponding contract key.
This is intented to use only during certain time while people using the version 0.6.2 of the RSK node migrates to using the 1.0.0 version.

## Reproduce

If you have an RSK node 1.0.0 sync'd from scratch, you can generate this file easily. We have created a simple `Dockerfile` for this, which you should run passing two volumes, one pointing to the mentioned database and another for where you want it to generete the file. For example:

```shell
$ docker build -t migration .
$ docker run -v ~/.rsk/mainnet/database:/database -v ~/output:/output migration
```

