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

`ch.so.agi.gretlgt.steps.VectorizeStep` converts raster cells with a matching band value into a dissolved multipolygon and stores
it in a GeoPackage. The table name inside the GeoPackage matches the input raster file name.

| Parameter | Description |
|-----------|-------------|
| `rasterPath` | Path to the raster file that should be vectorised. |
| `geopackagePath` | Destination GeoPackage that will receive the multipolygon layer. |
| `band` | Zero-based index of the raster band to inspect. |
| `cellValue` | Raster cell value that should be converted into vector geometry. |

The resulting layer contains a single multipolygon feature (if at least one matching cell exists) with a `value` attribute set to the
cell value that triggered the extraction.
