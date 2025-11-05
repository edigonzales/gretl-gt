# gretl-gt

## Logging

GRETL routes all log messages through the `ch.so.agi.gretlgt.logging`
package. When the plugin runs inside Gradle builds, log calls are bridged to
Gradle's lifecycle, info, debug and error channels so messages respect the
configured build verbosity. Outside of Gradle (for example in unit tests) the
same abstraction falls back to `java.util.logging` with a configurable
minimum level. Each GRETL step emits a pair of lifecycle messages that frame the
execution, while info/debug provide additional detail and errors forward the
original exception.

## Raster vectorization

`VectorizeStep` converts raster cells into multipolygon geometries that are grouped by their
cell value. The step expects three inputs:

1. a path to the raster to analyse
2. the zero-based band index to vectorize
3. the target GeoPackage path where the output layer should be written

The generated layer is named after the raster file (without its extension) and contains a
`value` attribute with the raster value represented by each multipolygon feature.
