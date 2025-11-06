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

## VectorizeStep

`ch.so.agi.gretlgt.steps.VectorizeStep` extracts a polygonal footprint for a
single raster value and persists the dissolved multipolygon to a GeoPackage
layer. The step relies on GeoTools' `PolygonExtractionProcess` to build the
vector representation and stores the result in a table that uses the raster
file name as the layer name.

Inputs:

* `rasterPath` – path to the raster whose cells should be vectorized
* `geopackagePath` – output GeoPackage file that will contain the result layer
* `band` – zero-based band index to inspect
* `cellValue` – raster value that should be converted to polygons

When matches are found the GeoPackage layer contains a single feature whose
geometry is the dissolved multipolygon of all cells matching `cellValue`. When
no cells match, the layer is still created but remains empty. The attribute `value`
stores the
requested cell value so downstream steps can retain the classification context.
