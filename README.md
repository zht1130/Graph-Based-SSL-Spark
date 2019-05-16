# Graph-Based-SSL-Spark
Graph-based Semi-Supervised Learning with Apache Spark



# How to use



## Pre-requirement

We have already tested it in the environment of Scala 2.11, Spark 2.40, JDK 1.8 and sbt 1.2.8.



## Jar package

- Download .jar file [Graph-Based-SSL-Spark.jar](https://github.com/zht1130/Graph-Based-SSL-Spark/blob/master/out/artifacts/jarPackage/Graph-Based-SSL-Spark.jar)

- Or build jar file yourself.



## How to run

This software has two mode: local and global.

- Graph construction: brute-force knn (both), spill-tree-based knn (global mode only).
- Inferring algorithms: Label propagation, Adsorption, Modified Adsorption.

### Local method

```
spark-submit --class local.run Graph-Based-SSL-Spark.jar "path-to-header" "path-to-raw" "path-to-train" "path-to-output" "inferring-algorithm" "number-of-neighbors" "number-of-maps" ("mu1" "mu2" "mu3")
```

- ```--class local.run Graph-Based-SSL-Spark.jar``` Determine the jar file to be run.
- ```"path-to-header"``` Path from HDFS to header.
- ```"path-to-raw"``` Path from HDFS to raw data set.
- ```"path-to-train"``` Path from HDFS to training set.
- ```"path-to-output"``` Path from HDFS to the output.
- ```"inferring-algorithm"``` Inferring algorithm: lp or adsorption or mad
- ```"number-of-neighbors"``` Number of neighbors. The value of k.
- ```"number-of-maps"``` Number of map tasks.
- ```"mu1" "mu2" "mu3"``` Optional parameters. Settings are required only when using the MAD algorithm



### Global method

```
spark-submit --class global.run Graph-Based-SSL-Spark.jar "path-to-header" "path-to-raw" "path-to-train" "path-to-output" "graph-algorithm" "inferring-algorithm" "number-of-neighbors" "number-of-maps" ("mu1" "mu2" "mu3")
```

- ```--class global.run Graph-Based-SSL-Spark.jar``` Determine the jar file to be run.
- ```"path-to-header"``` Path from HDFS to header.
- ```"path-to-raw"``` Path from HDFS to raw data set.
- ```"path-to-train"``` Path from HDFS to training set.
- ```"path-to-output"``` Path from HDFS to the output.
- ```"graph-algorithm"``` Graph construction algorithm: approximate or bruteForce
- ```"inferring-algorithm"``` Inferring algorithm: lp or adsorption or mad
- ```"number-of-neighbors"``` Number of neighbors. The value of k.
- ```"number-of-maps"``` Number of map tasks.
- ```"mu1" "mu2" "mu3"``` Optional parameters. Settings are required only when using the MAD algorithm

